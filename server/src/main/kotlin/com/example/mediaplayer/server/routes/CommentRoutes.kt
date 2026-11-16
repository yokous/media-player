package com.example.mediaplayer.server.routes

import com.example.mediaplayer.server.db.Comments
import com.example.mediaplayer.server.db.Tracks
import com.example.mediaplayer.server.db.Users
import com.example.mediaplayer.server.db.dbQuery
import com.example.mediaplayer.server.dto.CommentDto
import com.example.mediaplayer.server.dto.CommentPage
import com.example.mediaplayer.server.dto.CommentRequest
import com.example.mediaplayer.server.errors.ApiException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

private fun ResultRow.toCommentDto() = CommentDto(
    id = this[Comments.id].toString(),
    trackId = this[Comments.trackId].toString(),
    authorName = this[Users.displayName],
    text = this[Comments.text],
    positionMs = this[Comments.positionMs],
    createdAt = this[Comments.createdAt].toString(),
)

fun Route.commentRoutes() {
    get("/tracks/{trackId}/comments") {
        val id = call.uuidParam("trackId")
        val pp = call.pageParams()
        val page = dbQuery {
            if (Tracks.selectAll().where { Tracks.id eq id }.empty()) throw ApiException.notFound("Трек не найден")
            val cond = (Comments.trackId eq id) and (Comments.hidden eq false)
            val total = Comments.selectAll().where { cond }.count()
            val rows = (Comments innerJoin Users).selectAll().where { cond }
                .orderBy(Comments.positionMs, SortOrder.ASC)
                .limit(pp.pageSize, pp.offset).map { it.toCommentDto() }
            CommentPage(rows, pp.page, pp.pageSize, total)
        }
        call.respond(page)
    }

    authenticate("auth-jwt") {
        post("/tracks/{trackId}/comments") {
            val uid = call.userId()
            val id = call.uuidParam("trackId")
            val req = call.receive<CommentRequest>()
            val text = req.text.trim()
            if (text.length !in 1..500) {
                throw ApiException.validation("Комментарий должен быть от 1 до 500 символов", "text", "length 1..500")
            }
            if (req.positionMs < 0) {
                throw ApiException.validation("positionMs должен быть >= 0", "positionMs", "must be >= 0")
            }
            val dto = dbQuery {
                if (Tracks.selectAll().where { Tracks.id eq id }.empty()) throw ApiException.notFound("Трек не найден")
                val newId = Comments.insert {
                    it[Comments.trackId] = id
                    it[Comments.userId] = uid
                    it[Comments.text] = text
                    it[Comments.positionMs] = req.positionMs
                } get Comments.id
                (Comments innerJoin Users).selectAll().where { Comments.id eq newId }.single().toCommentDto()
            }
            call.respond(HttpStatusCode.Created, dto)
        }

        delete("/comments/{commentId}") {
            val uid = call.userId()
            val moderator = call.isModerator()
            val cid = call.uuidParam("commentId")
            dbQuery {
                val row = Comments.selectAll().where { Comments.id eq cid }.singleOrNull()
                    ?: throw ApiException.notFound("Комментарий не найден")
                when {
                    row[Comments.userId] == uid -> Comments.deleteWhere { Comments.id eq cid }
                    moderator -> Comments.update({ Comments.id eq cid }) { it[hidden] = true }
                    else -> throw ApiException.forbidden("Можно удалять только свои комментарии")
                }
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
