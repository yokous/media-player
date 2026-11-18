package com.example.mediaplayer.server.routes

import com.example.mediaplayer.server.db.DuetSessions
import com.example.mediaplayer.server.db.PlaylistTracks
import com.example.mediaplayer.server.db.Playlists
import com.example.mediaplayer.server.db.RecognitionHistory
import com.example.mediaplayer.server.db.Tracks
import com.example.mediaplayer.server.db.Users
import com.example.mediaplayer.server.db.dbQuery
import com.example.mediaplayer.server.dto.DuetRequest
import com.example.mediaplayer.server.dto.DuetResponse
import com.example.mediaplayer.server.dto.FriendCodeResponse
import com.example.mediaplayer.server.dto.PlaylistWithTracksDto
import com.example.mediaplayer.server.dto.RecognitionResult
import com.example.mediaplayer.server.errors.ApiException
import com.example.mediaplayer.server.external.AiClient
import com.example.mediaplayer.server.external.RecognitionClient
import com.example.mediaplayer.server.service.ImportMatcher
import com.example.mediaplayer.server.service.genreWeights
import com.example.mediaplayer.server.service.historyEnabled
import com.example.mediaplayer.server.service.matchPercent
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

private const val MAX_AUDIO_BYTES = 1_000_000
private const val DUET_TRACKS = 15

private fun uuidToCode(id: UUID) = id.toString().replace("-", "")

private fun codeToUuid(code: String): UUID? {
    if (code.length != 32 || !code.all { it.isDigit() || it in 'a'..'f' }) return null
    val d = "${code.substring(0, 8)}-${code.substring(8, 12)}-${code.substring(12, 16)}-${code.substring(16, 20)}-${code.substring(20)}"
    return runCatching { UUID.fromString(d) }.getOrNull()
}

/** Маршруты, которые обращаются к внешним сервисам (распознавание, ИИ). */
fun Route.integrationRoutes(ai: AiClient, recognition: RecognitionClient) {
    authenticate("auth-jwt") {
        get("/me/friend-code") {
            call.respond(FriendCodeResponse(uuidToCode(call.userId())))
        }

        post("/recognition") {
            val uid = call.userId()
            if (!recognition.enabled) {
                throw ApiException(HttpStatusCode.ServiceUnavailable, "SERVICE_UNAVAILABLE", "Распознавание на сервере не настроено")
            }
            var audio: ByteArray? = null
            var filename = "audio"
            call.receiveMultipart().forEachPart { part ->
                if (part is PartData.FileItem && part.name == "audio") {
                    filename = part.originalFileName ?: "audio"
                    val bytes = part.streamProvider().use { it.readNBytes(MAX_AUDIO_BYTES + 1) }
                    if (bytes.size > MAX_AUDIO_BYTES) {
                        part.dispose()
                        throw ApiException.tooLarge("Запись не должна быть больше 1 МБ")
                    }
                    audio = bytes
                }
                part.dispose()
            }
            val data = audio?.takeIf { it.isNotEmpty() }
                ?: throw ApiException.validation("Нужна запись в поле audio", "audio", "required")

            // Запись нигде не сохраняется: она есть только в памяти на время запроса.
            val song = recognition.recognize(data, filename)
            if (song == null) {
                call.respond(RecognitionResult(recognized = false))
                return@post
            }
            val raw = "${song.artist} — ${song.title}".trim(' ', '—')
            val result = dbQuery {
                val best = Tracks.selectAll().where { Tracks.hidden eq false }.toList()
                    .let { rows ->
                        ImportMatcher.rank("${song.artist} ${song.title}", rows, { it[Tracks.title] }, { it[Tracks.artist] })
                            .firstOrNull { it.score >= 0.8 }?.item
                    }
                RecognitionHistory.insert {
                    it[userId] = uid
                    it[trackId] = best?.get(Tracks.id)
                    it[rawTitle] = raw.take(300)
                }
                RecognitionResult(
                    recognized = true,
                    track = best?.let { listOf(it).toTrackDtos(uid).first() },
                    rawTitle = raw,
                )
            }
            call.respond(result)
        }

        post("/duets") {
            val uid = call.userId()
            val req = call.receive<DuetRequest>()
            val otherId = codeToUuid(req.friendCode.trim().lowercase())
                ?: throw ApiException.validation("Код друга указан неверно", "friendCode", "invalid code")
            if (otherId == uid) throw ApiException.validation("Нельзя сделать дуэт с самим собой", "friendCode", "own code")

            val data = dbQuery {
                val me = Users.selectAll().where { Users.id eq uid }.single()
                val other = Users.selectAll().where { Users.id eq otherId }.singleOrNull()
                    ?: throw ApiException.notFound("Пользователь с таким кодом не найден")
                if (!historyEnabled(uid) || !historyEnabled(otherId)) {
                    throw ApiException.conflict("DUET_UNAVAILABLE", "Дуэт невозможен: у одного из пользователей отключён сбор истории")
                }
                val wa = genreWeights(uid, withPlays = true)
                val wb = genreWeights(otherId, withPlays = true)
                val percent = matchPercent(wa, wb)
                val common = (wa.keys intersect wb.keys).sortedByDescending { (wa[it] ?: 0) + (wb[it] ?: 0) }.take(3)

                val pool = Tracks.selectAll().where { Tracks.hidden eq false }.toList()
                val ranked = pool.sortedWith(
                    compareByDescending<org.jetbrains.exposed.sql.ResultRow> {
                        val g = it[Tracks.genre]
                        val a = wa[g] ?: 0
                        val b = wb[g] ?: 0
                        // Жанры, которые нравятся обоим, важнее.
                        (a + b) * (if (a > 0 && b > 0) 2 else 1)
                    }.thenByDescending { it[Tracks.likesCount] },
                ).take(DUET_TRACKS)

                val title = "Дуэт: ${me[Users.displayName]} и ${other[Users.displayName]}".take(80)
                val plId = Playlists.insert {
                    it[userId] = uid
                    it[Playlists.title] = title
                } get Playlists.id
                ranked.forEachIndexed { index, row ->
                    PlaylistTracks.insert {
                        it[playlistId] = plId
                        it[trackId] = row[Tracks.id]
                        it[position] = index
                    }
                }
                DuetSessions.insert {
                    it[userAId] = uid
                    it[userBId] = otherId
                    it[playlistId] = plId
                    it[matchPercent] = percent
                }
                val dto = PlaylistWithTracksDto(plId.toString(), title, ranked.size, ranked.toTrackDtos(uid))
                Triple(percent, common, dto)
            }
            val (percent, common, playlist) = data
            val explanation = if (ai.enabled) {
                ai.complete(
                    "Двое пользователей музыкального плеера совпали по вкусам на $percent%. " +
                        "Общие жанры: ${common.joinToString().ifEmpty { "нет" }}. " +
                        "Для них собрали общий плейлист, например: ${playlist.tracks.take(5).joinToString("; ") { "${it.artist} — ${it.title}" }}. " +
                        "Напиши по-русски одно-два коротких предложения, что объединяет их вкусы. Без списков.",
                )
            } else null
            call.respond(DuetResponse(percent, common, playlist, explanation))
        }
    }
}
