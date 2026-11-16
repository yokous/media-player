package com.example.mediaplayer.server.routes

import com.example.mediaplayer.server.db.PlaylistTracks
import com.example.mediaplayer.server.db.Playlists
import com.example.mediaplayer.server.db.Tracks
import com.example.mediaplayer.server.db.dbQuery
import com.example.mediaplayer.server.dto.DoubtfulItem
import com.example.mediaplayer.server.dto.FoundItem
import com.example.mediaplayer.server.dto.ImportReport
import com.example.mediaplayer.server.dto.ImportRequest
import com.example.mediaplayer.server.dto.PlaylistCreateRequest
import com.example.mediaplayer.server.dto.PlaylistDto
import com.example.mediaplayer.server.dto.PlaylistWithTracksDto
import com.example.mediaplayer.server.dto.ShareResponse
import com.example.mediaplayer.server.errors.ApiException
import com.example.mediaplayer.server.service.ImportMatcher
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.security.SecureRandom
import java.util.UUID

private const val CODE_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"
private val random = SecureRandom()

private fun newShareCode() = (1..8).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.joinToString("")

private const val MAX_IMPORT_LINES = 200
private const val MAX_PLAYLIST_TRACKS = 500

fun Route.playlistRoutes() {
    // Открытие плейлиста по ссылке/QR доступно без входа.
    get("/playlists/shared/{shareCode}") {
        val code = call.parameters["shareCode"].orEmpty()
        val dto = dbQuery {
            val pl = Playlists.selectAll().where { Playlists.shareCode eq code }.singleOrNull()
                ?: throw ApiException.notFound("Плейлист не найден")
            val rows = (PlaylistTracks innerJoin Tracks).selectAll()
                .where { (PlaylistTracks.playlistId eq pl[Playlists.id]) and (Tracks.hidden eq false) }
                .orderBy(PlaylistTracks.position, SortOrder.ASC).toList()
            PlaylistWithTracksDto(
                pl[Playlists.id].toString(), pl[Playlists.title], rows.size,
                rows.map { it.toTrackDto(false) },
            )
        }
        call.respond(dto)
    }

    authenticate("auth-jwt") {
        post("/playlists") {
            val uid = call.userId()
            val req = call.receive<PlaylistCreateRequest>()
            val title = req.title.trim()
            if (title.length !in 1..80) {
                throw ApiException.validation("Название должно быть от 1 до 80 символов", "title", "length 1..80")
            }
            if (req.trackIds.size > MAX_PLAYLIST_TRACKS) {
                throw ApiException.tooLarge("В плейлисте не может быть больше $MAX_PLAYLIST_TRACKS треков")
            }
            val ids = req.trackIds.map {
                runCatching { UUID.fromString(it) }.getOrNull()
                    ?: throw ApiException.validation("trackIds содержит неверный идентификатор", "trackIds", "invalid uuid")
            }.distinct()
            val dto = dbQuery {
                val existing = if (ids.isEmpty()) emptySet() else
                    Tracks.selectAll().where { (Tracks.id inList ids) and (Tracks.hidden eq false) }
                        .map { it[Tracks.id] }.toSet()
                val missing = ids.firstOrNull { it !in existing }
                if (missing != null) throw ApiException.notFound("Трек $missing не найден")
                val plId = Playlists.insert {
                    it[userId] = uid
                    it[Playlists.title] = title
                } get Playlists.id
                ids.forEachIndexed { index, tid ->
                    PlaylistTracks.insert {
                        it[playlistId] = plId
                        it[trackId] = tid
                        it[position] = index
                    }
                }
                PlaylistDto(plId.toString(), title, ids.size)
            }
            call.respond(HttpStatusCode.Created, dto)
        }

        post("/playlists/{playlistId}/share") {
            val uid = call.userId()
            val id = call.uuidParam("playlistId")
            val code = dbQuery {
                val pl = Playlists.selectAll().where { Playlists.id eq id }.singleOrNull()
                    ?: throw ApiException.notFound("Плейлист не найден")
                if (pl[Playlists.userId] != uid) throw ApiException.forbidden("Это не ваш плейлист")
                pl[Playlists.shareCode] ?: newShareCode().also { c ->
                    Playlists.update({ Playlists.id eq id }) { it[shareCode] = c }
                }
            }
            call.respond(ShareResponse(code, "mediaplayer://playlist/$code"))
        }

        post("/playlists/import/match") {
            call.userId()
            val req = call.receive<ImportRequest>()
            if (req.lines.isEmpty()) {
                throw ApiException.validation("Список строк пуст", "lines", "must not be empty")
            }
            if (req.lines.size > MAX_IMPORT_LINES) {
                throw ApiException.tooLarge("Не больше $MAX_IMPORT_LINES строк за раз")
            }
            val report = dbQuery {
                val catalog = Tracks.selectAll().where { Tracks.hidden eq false }.toList()
                val found = mutableListOf<FoundItem>()
                val doubtful = mutableListOf<DoubtfulItem>()
                val notFound = mutableListOf<String>()
                for (raw in req.lines) {
                    val line = raw.trim()
                    if (line.isEmpty()) continue
                    val ranked = ImportMatcher.rank(line, catalog, { it[Tracks.title] }, { it[Tracks.artist] })
                    val best = ranked.firstOrNull()
                    when {
                        best == null -> notFound += line
                        best.score >= ImportMatcher.FOUND_THRESHOLD -> found += FoundItem(line, best.item.toTrackDto(false))
                        else -> doubtful += DoubtfulItem(line, ranked.take(3).map { it.item.toTrackDto(false) })
                    }
                }
                ImportReport(found, doubtful, notFound)
            }
            call.respond(report)
        }
    }
}
