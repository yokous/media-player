package com.example.mediaplayer.server.db

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

/*
 * Таблицы соответствуют ER-диаграмме (docs/diagrams/er.drawio).
 * Физические имена во множественном числе, потому что «user» и «like» зарезервированы в SQL.
 */

object Roles : Table("roles") {
    val id = integer("role_id").autoIncrement()
    val name = varchar("name", 20).uniqueIndex()
    override val primaryKey = PrimaryKey(id)
}

object Users : Table("users") {
    val id = uuid("user_id").autoGenerate()
    val email = varchar("email", 254).uniqueIndex()
    val passwordHash = varchar("password_hash", 100)
    val displayName = varchar("display_name", 40)
    val roleId = integer("role_id").references(Roles.id)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    override val primaryKey = PrimaryKey(id)
}

object RefreshTokens : Table("refresh_tokens") {
    val id = uuid("refresh_token_id").autoGenerate()
    val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val tokenHash = varchar("token_hash", 64).uniqueIndex()
    val expiresAt = timestamp("expires_at")
    val revoked = bool("revoked").default(false)
    override val primaryKey = PrimaryKey(id)
}

object TasteProfiles : Table("taste_profiles") {
    val id = uuid("taste_profile_id").autoGenerate()
    val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val historyEnabled = bool("history_enabled").default(true)
    val profile = text("profile").default("{}")
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
    override val primaryKey = PrimaryKey(id)
}

object Tracks : Table("tracks") {
    val id = uuid("track_id").autoGenerate()
    val title = varchar("title", 200)
    val artist = varchar("artist", 200)
    val genre = varchar("genre", 60)
    val durationMs = long("duration_ms")
    val audioUrl = varchar("audio_url", 500)
    val coverUrl = varchar("cover_url", 500).nullable()
    val likesCount = integer("likes_count").default(0)
    val hidden = bool("hidden").default(false)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    override val primaryKey = PrimaryKey(id)
}

object Likes : Table("likes") {
    val id = uuid("like_id").autoGenerate()
    val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val trackId = uuid("track_id").references(Tracks.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    override val primaryKey = PrimaryKey(id)
    init { uniqueIndex(userId, trackId) }
}

object Comments : Table("comments") {
    val id = uuid("comment_id").autoGenerate()
    val trackId = uuid("track_id").references(Tracks.id, onDelete = ReferenceOption.CASCADE)
    val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val text = varchar("text", 500)
    val positionMs = long("position_ms")
    val hidden = bool("hidden").default(false)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    override val primaryKey = PrimaryKey(id)
}

object Playlists : Table("playlists") {
    val id = uuid("playlist_id").autoGenerate()
    val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val title = varchar("title", 80)
    val shareCode = varchar("share_code", 16).uniqueIndex().nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    override val primaryKey = PrimaryKey(id)
}

object PlaylistTracks : Table("playlist_tracks") {
    val id = uuid("playlist_track_id").autoGenerate()
    val playlistId = uuid("playlist_id").references(Playlists.id, onDelete = ReferenceOption.CASCADE)
    val trackId = uuid("track_id").references(Tracks.id, onDelete = ReferenceOption.CASCADE)
    val position = integer("position")
    override val primaryKey = PrimaryKey(id)
    init { uniqueIndex(playlistId, trackId) }
}

object ListeningEvents : Table("listening_events") {
    val id = uuid("listening_event_id").autoGenerate()
    val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val trackId = uuid("track_id").references(Tracks.id, onDelete = ReferenceOption.CASCADE)
    val type = varchar("type", 10)
    val occurredAt = long("timestamp")
    override val primaryKey = PrimaryKey(id)
}

object RecognitionHistory : Table("recognition_history") {
    val id = uuid("recognition_history_id").autoGenerate()
    val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val trackId = uuid("track_id").references(Tracks.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val rawTitle = varchar("raw_title", 300)
    val recognizedAt = timestamp("recognized_at").clientDefault { Instant.now() }
    override val primaryKey = PrimaryKey(id)
}

object DuetSessions : Table("duet_sessions") {
    val id = uuid("duet_session_id").autoGenerate()
    val userAId = uuid("user_a_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val userBId = uuid("user_b_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val playlistId = uuid("playlist_id").references(Playlists.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val matchPercent = integer("match_percent")
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    override val primaryKey = PrimaryKey(id)
}

val AllTables = arrayOf(
    Roles, Users, RefreshTokens, TasteProfiles, Tracks, Likes, Comments,
    Playlists, PlaylistTracks, ListeningEvents, RecognitionHistory, DuetSessions,
)
