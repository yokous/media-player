package com.example.mediaplayer.server.routes

import com.example.mediaplayer.server.db.Likes
import com.example.mediaplayer.server.db.ListeningEvents
import com.example.mediaplayer.server.db.TasteProfiles
import com.example.mediaplayer.server.db.Tracks
import com.example.mediaplayer.server.db.dbQuery
import com.example.mediaplayer.server.dto.ListeningEventsRequest
import com.example.mediaplayer.server.dto.RecommendationsResponse
import com.example.mediaplayer.server.dto.TasteSettings
import com.example.mediaplayer.server.dto.TasteSettingsRequest
import com.example.mediaplayer.server.errors.ApiException
import com.example.mediaplayer.server.external.AiClient
import com.example.mediaplayer.server.service.genreWeights
import com.example.mediaplayer.server.service.historyEnabled
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

private const val MAX_EVENTS = 200
private const val RECOMMENDATIONS = 20
private val EVENT_TYPES = setOf("PLAY", "SKIP", "FINISH")

fun Route.meRoutes(ai: AiClient) {
    authenticate("auth-jwt") {
        post("/me/listening-events") {
            val uid = call.userId()
            val req = call.receive<ListeningEventsRequest>()
            if (req.events.size > MAX_EVENTS) throw ApiException.tooLarge("Не больше $MAX_EVENTS событий за раз")
            val parsed = req.events.mapIndexed { i, e ->
                val tid = runCatching { UUID.fromString(e.trackId) }.getOrNull()
                    ?: throw ApiException.validation("Неверный trackId", "events[$i].trackId", "invalid uuid")
                if (e.type !in EVENT_TYPES) {
                    throw ApiException.validation("type: PLAY, SKIP или FINISH", "events[$i].type", "must be PLAY|SKIP|FINISH")
                }
                if (e.timestamp < 0) {
                    throw ApiException.validation("timestamp должен быть >= 0", "events[$i].timestamp", "must be >= 0")
                }
                Triple(tid, e.type, e.timestamp)
            }
            dbQuery {
                // Если пользователь выключил историю — события принимаем, но не сохраняем.
                if (historyEnabled(uid) && parsed.isNotEmpty()) {
                    val known = Tracks.selectAll().where { Tracks.id inList parsed.map { it.first }.distinct() }
                        .map { it[Tracks.id] }.toSet()
                    val toSave = parsed.filter { it.first in known }
                    ListeningEvents.batchInsert(toSave) { (tid, type, ts) ->
                        this[ListeningEvents.userId] = uid
                        this[ListeningEvents.trackId] = tid
                        this[ListeningEvents.type] = type
                        this[ListeningEvents.occurredAt] = ts
                    }
                }
            }
            call.respond(HttpStatusCode.Accepted)
        }

        put("/me/taste-profile") {
            val uid = call.userId()
            val req = call.receive<TasteSettingsRequest>()
            val enabled = req.historyEnabled
                ?: throw ApiException.validation("Нужно поле historyEnabled", "historyEnabled", "required")
            dbQuery {
                val updated = TasteProfiles.update({ TasteProfiles.userId eq uid }) {
                    it[historyEnabled] = enabled
                    it[updatedAt] = Instant.now()
                }
                if (updated == 0) TasteProfiles.insert {
                    it[userId] = uid
                    it[historyEnabled] = enabled
                }
            }
            call.respond(TasteSettings(enabled))
        }

        delete("/me/taste-profile") {
            val uid = call.userId()
            dbQuery {
                ListeningEvents.deleteWhere { ListeningEvents.userId eq uid }
                TasteProfiles.update({ TasteProfiles.userId eq uid }) {
                    it[profile] = "{}"
                    it[updatedAt] = Instant.now()
                }
            }
            call.respond(HttpStatusCode.NoContent)
        }

        rateLimit(AiLimit) {
            get("/me/recommendations") {
                val uid = call.userId()
                val result = dbQuery {
                    val personalEnabled = historyEnabled(uid)
                    val weights = genreWeights(uid, withPlays = personalEnabled)

                    val seen = Likes.selectAll().where { Likes.userId eq uid }.map { it[Likes.trackId] }.toSet()
                    val all = Tracks.selectAll().where { Tracks.hidden eq false }
                        .orderBy(Tracks.likesCount, SortOrder.DESC).toList()
                    val personal = weights.isNotEmpty()
                    val ranked = if (personal) {
                        all.filter { it[Tracks.id] !in seen }
                            .sortedByDescending { weights[it[Tracks.genre]] ?: 0 }
                            .take(RECOMMENDATIONS)
                    } else all.take(RECOMMENDATIONS)
                    RecommendationsResponse(personal, ranked.toTrackDtos(uid)) to weights
                }
                val (response, weights) = result
                // ИИ только объясняет подборку. Если он недоступен, отдаём подборку без объяснения.
                val explanation = if (response.personalized && ai.enabled) {
                    val top = weights.entries.sortedByDescending { it.value }.take(3).joinToString { it.key }
                    val sample = response.items.take(5).joinToString("; ") { "${it.artist} — ${it.title}" }
                    ai.complete(
                        "Пользователь музыкального плеера чаще всего слушает жанры: $top. " +
                            "Ему подобрали треки, например: $sample. " +
                            "Напиши по-русски одно-два коротких предложения, почему эта подборка ему подойдёт. " +
                            "Без списков и заголовков.",
                    )
                } else null
                call.respond(response.copy(explanation = explanation))
            }
        }
    }
}
