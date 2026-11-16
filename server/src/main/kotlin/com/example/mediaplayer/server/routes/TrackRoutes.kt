package com.example.mediaplayer.server.routes

import com.example.mediaplayer.server.db.Likes
import com.example.mediaplayer.server.db.Tracks
import com.example.mediaplayer.server.db.dbQuery
import com.example.mediaplayer.server.dto.LikeState
import com.example.mediaplayer.server.dto.TrackPage
import com.example.mediaplayer.server.errors.ApiException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

private fun likeState(trackId: UUID, liked: Boolean): LikeState {
    val count = Likes.selectAll().where { Likes.trackId eq trackId }.count().toInt()
    Tracks.update({ Tracks.id eq trackId }) { it[likesCount] = count }
    return LikeState(liked, count)
}

private fun requireTrack(trackId: UUID) {
    if (Tracks.selectAll().where { (Tracks.id eq trackId) and (Tracks.hidden eq false) }.empty()) {
        throw ApiException.notFound("Трек не найден")
    }
}

fun Route.trackRoutes() {
    // Каталог публичный, но если токен передан — отдаём likedByMe.
    authenticate("auth-jwt", optional = true) {
        get("/tracks") {
            val uid = call.userIdOrNull()
            val pp = call.pageParams()
            val query = call.request.queryParameters["query"]?.trim().orEmpty()
            val genre = call.request.queryParameters["genre"]?.trim().orEmpty()
            val sort = call.request.queryParameters["sort"] ?: "popular"
            if (sort != "popular" && sort != "new") {
                throw ApiException.validation("sort: popular или new", "sort", "must be popular|new")
            }
            val page = dbQuery {
                var cond: Op<Boolean> = Tracks.hidden eq false
                if (query.isNotEmpty()) {
                    val p = "%" + escapeLike(query.lowercase()) + "%"
                    cond = cond and ((Tracks.title.lowerCase() like p) or (Tracks.artist.lowerCase() like p))
                }
                if (genre.isNotEmpty()) cond = cond and (Tracks.genre.lowerCase() eq genre.lowercase())
                val total = Tracks.selectAll().where { cond }.count()
                val rows = Tracks.selectAll().where { cond }
                    .orderBy(if (sort == "popular") Tracks.likesCount else Tracks.createdAt, SortOrder.DESC)
                    .limit(pp.pageSize, pp.offset)
                    .toList()
                TrackPage(rows.toTrackDtos(uid), pp.page, pp.pageSize, total)
            }
            call.respond(page)
        }

        get("/tracks/{trackId}") {
            val uid = call.userIdOrNull()
            val id = call.uuidParam("trackId")
            val dto = dbQuery {
                val row = Tracks.selectAll().where { (Tracks.id eq id) and (Tracks.hidden eq false) }.singleOrNull()
                    ?: throw ApiException.notFound("Трек не найден")
                listOf(row).toTrackDtos(uid).single()
            }
            call.respond(dto)
        }
    }

    authenticate("auth-jwt") {
        route("/tracks/{trackId}/like") {
            put {
                val uid = call.userId()
                val id = call.uuidParam("trackId")
                val state = dbQuery {
                    requireTrack(id)
                    Likes.insertIgnore { it[Likes.userId] = uid; it[Likes.trackId] = id }
                    likeState(id, true)
                }
                call.respond(state)
            }
            delete {
                val uid = call.userId()
                val id = call.uuidParam("trackId")
                val state = dbQuery {
                    requireTrack(id)
                    Likes.deleteWhere { (Likes.userId eq uid) and (Likes.trackId eq id) }
                    likeState(id, false)
                }
                call.respond(state)
            }
        }

        get("/me/likes") {
            val uid = call.userId()
            val pp = call.pageParams()
            val page = dbQuery {
                val base = (Likes innerJoin Tracks).selectAll()
                    .where { (Likes.userId eq uid) and (Tracks.hidden eq false) }
                val total = base.count()
                val rows = (Likes innerJoin Tracks).selectAll()
                    .where { (Likes.userId eq uid) and (Tracks.hidden eq false) }
                    .orderBy(Likes.createdAt, SortOrder.DESC)
                    .limit(pp.pageSize, pp.offset).toList()
                TrackPage(rows.map { it.toTrackDto(true) }, pp.page, pp.pageSize, total)
            }
            call.respond(page)
        }
    }
}
