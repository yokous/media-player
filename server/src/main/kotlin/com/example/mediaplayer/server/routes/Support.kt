package com.example.mediaplayer.server.routes

import com.example.mediaplayer.server.db.Likes
import com.example.mediaplayer.server.db.Tracks
import com.example.mediaplayer.server.dto.TrackDto
import com.example.mediaplayer.server.errors.ApiException
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

const val MAX_PAGE_SIZE = 50
const val DEFAULT_PAGE_SIZE = 20

fun ApplicationCall.userIdOrNull(): UUID? =
    principal<JWTPrincipal>()?.payload?.subject?.let { runCatching { UUID.fromString(it) }.getOrNull() }

fun ApplicationCall.userId(): UUID = userIdOrNull() ?: throw ApiException.unauthorized()

fun ApplicationCall.isModerator(): Boolean =
    principal<JWTPrincipal>()?.payload?.getClaim("role")?.asString() == "moderator"

fun ApplicationCall.uuidParam(name: String): UUID =
    parameters[name]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        ?: throw ApiException.validation("Параметр $name не является идентификатором", name, "invalid uuid")

data class PageParams(val page: Int, val pageSize: Int) {
    val offset: Long get() = (page - 1).toLong() * pageSize
}

fun ApplicationCall.pageParams(): PageParams {
    val page = request.queryParameters["page"]?.let {
        it.toIntOrNull()?.takeIf { v -> v >= 1 } ?: throw ApiException.validation("page должен быть целым числом от 1", "page", "must be >= 1")
    } ?: 1
    val size = request.queryParameters["pageSize"]?.let {
        it.toIntOrNull()?.takeIf { v -> v in 1..MAX_PAGE_SIZE }
            ?: throw ApiException.validation("pageSize должен быть от 1 до $MAX_PAGE_SIZE", "pageSize", "must be 1..$MAX_PAGE_SIZE")
    } ?: DEFAULT_PAGE_SIZE
    return PageParams(page, size)
}

fun ResultRow.toTrackDto(liked: Boolean) = TrackDto(
    id = this[Tracks.id].toString(),
    title = this[Tracks.title],
    artist = this[Tracks.artist],
    genre = this[Tracks.genre],
    durationMs = this[Tracks.durationMs],
    coverUrl = this[Tracks.coverUrl],
    streamUrl = this[Tracks.audioUrl],
    likesCount = this[Tracks.likesCount],
    likedByMe = liked,
)

/** Какие из треков пользователь уже лайкнул (один запрос вместо N). Вызывать внутри транзакции. */
fun likedIds(userId: UUID?, trackIds: Collection<UUID>): Set<UUID> {
    if (userId == null || trackIds.isEmpty()) return emptySet()
    return Likes.selectAll()
        .where { (Likes.userId eq userId) and (Likes.trackId inList trackIds) }
        .map { it[Likes.trackId] }
        .toSet()
}

fun List<ResultRow>.toTrackDtos(userId: UUID?): List<TrackDto> {
    val liked = likedIds(userId, map { it[Tracks.id] })
    return map { it.toTrackDto(it[Tracks.id] in liked) }
}

fun escapeLike(s: String): String = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
