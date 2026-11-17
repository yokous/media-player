package com.example.mediaplayer.server.dto

import kotlinx.serialization.Serializable

@Serializable data class ErrorDetail(val field: String, val issue: String)
@Serializable data class ErrorBody(
    val code: String,
    val message: String,
    val details: List<ErrorDetail>? = null,
    val requestId: String? = null,
)
@Serializable data class ErrorResponse(val error: ErrorBody)

@Serializable data class RegisterRequest(val email: String = "", val password: String = "", val displayName: String = "")
@Serializable data class LoginRequest(val email: String = "", val password: String = "")
@Serializable data class RefreshRequest(val refreshToken: String = "")
@Serializable data class UserDto(val id: String, val email: String, val displayName: String, val role: String)
@Serializable data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val user: UserDto,
)

@Serializable data class TrackDto(
    val id: String,
    val title: String,
    val artist: String,
    val genre: String,
    val durationMs: Long,
    val coverUrl: String? = null,
    val streamUrl: String,
    val likesCount: Int,
    val likedByMe: Boolean,
)
@Serializable data class TrackPage(val items: List<TrackDto>, val page: Int, val pageSize: Int, val total: Long)
@Serializable data class LikeState(val liked: Boolean, val likesCount: Int)

@Serializable data class CommentRequest(val text: String = "", val positionMs: Long = -1)
@Serializable data class CommentDto(
    val id: String,
    val trackId: String,
    val authorName: String,
    val text: String,
    val positionMs: Long,
    val createdAt: String,
)
@Serializable data class CommentPage(val items: List<CommentDto>, val page: Int, val pageSize: Int, val total: Long)

@Serializable data class PlaylistCreateRequest(val title: String = "", val trackIds: List<String> = emptyList())
@Serializable data class PlaylistDto(val id: String, val title: String, val tracksCount: Int)
@Serializable data class PlaylistWithTracksDto(
    val id: String,
    val title: String,
    val tracksCount: Int,
    val tracks: List<TrackDto>,
)
@Serializable data class ShareResponse(val shareCode: String, val url: String)

@Serializable data class ImportRequest(val lines: List<String> = emptyList())
@Serializable data class FoundItem(val line: String, val track: TrackDto)
@Serializable data class DoubtfulItem(val line: String, val candidates: List<TrackDto>)
@Serializable data class ImportReport(
    val found: List<FoundItem>,
    val doubtful: List<DoubtfulItem>,
    val notFound: List<String>,
)

@Serializable data class ListeningEventDto(val trackId: String = "", val type: String = "", val timestamp: Long = -1)
@Serializable data class ListeningEventsRequest(val events: List<ListeningEventDto> = emptyList())
@Serializable data class TasteSettingsRequest(val historyEnabled: Boolean? = null)
@Serializable data class TasteSettings(val historyEnabled: Boolean)
@Serializable data class RecommendationsResponse(
    val personalized: Boolean,
    val items: List<TrackDto>,
    val explanation: String? = null,
)

@Serializable data class RecognitionResult(val recognized: Boolean, val track: TrackDto? = null, val rawTitle: String? = null)
@Serializable data class FriendCodeResponse(val friendCode: String)
@Serializable data class DuetRequest(val friendCode: String = "")
@Serializable data class DuetResponse(
    val matchPercent: Int,
    val commonGenres: List<String>,
    val playlist: PlaylistWithTracksDto,
    val explanation: String? = null,
)

@Serializable data class HealthDto(val status: String, val version: String)
