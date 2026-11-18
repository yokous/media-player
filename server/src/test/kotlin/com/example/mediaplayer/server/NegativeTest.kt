package com.example.mediaplayer.server

import com.example.mediaplayer.server.config.AppConfig
import com.example.mediaplayer.server.dto.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

/** Негативные тесты: на каждый эндпоинт ошибочный запрос должен давать понятный код и единый формат ошибки. */
class NegativeTest {
    private val cfg = AppConfig(
        port = 0,
        dbUrl = "jdbc:h2:mem:apitest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        dbUser = "sa", dbPassword = "", dbDriver = "org.h2.Driver",
        jwtSecret = "test-secret-test-secret-test-secret-1234",
    )
    private val zero = "00000000-0000-0000-0000-000000000000"

    private fun run(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) = testApplication {
        application { module(cfg) }
        block(createClient { install(ContentNegotiation) { json(AppJson) } })
    }

    private suspend fun HttpClient.register(email: String): AuthResponse =
        post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, "password123", "Neg"))
        }.body()

    private fun HttpRequestBuilder.auth(t: String) = header(HttpHeaders.Authorization, "Bearer $t")
    private fun HttpRequestBuilder.json(body: Any) { contentType(ContentType.Application.Json); setBody(body) }

    private suspend fun HttpResponse.expect(status: HttpStatusCode, code: String) {
        assertEquals(status, this.status)
        assertEquals(code, body<ErrorResponse>().error.code)
    }

    // --- auth ---
    @Test fun registerShortPassword() = run { c ->
        c.post("/api/v1/auth/register") { json(RegisterRequest("neg1@test.com", "short", "N")) }
            .expect(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
    }

    @Test fun registerBrokenJson() = run { c ->
        c.post("/api/v1/auth/register") { contentType(ContentType.Application.Json); setBody("{not json") }
            .expect(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
    }

    @Test fun loginEmptyFieldsAndUnknownUser() = run { c ->
        c.post("/api/v1/auth/login") { json(LoginRequest("", "")) }.expect(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        c.post("/api/v1/auth/login") { json(LoginRequest("nobody@test.com", "password123")) }
            .expect(HttpStatusCode.Unauthorized, "INVALID_CREDENTIALS")
    }

    @Test fun refreshEmptyAndGarbageToken() = run { c ->
        c.post("/api/v1/auth/refresh") { json(RefreshRequest("")) }.expect(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        assertEquals(HttpStatusCode.Unauthorized, c.post("/api/v1/auth/refresh") { json(RefreshRequest("garbage")) }.status)
    }

    @Test fun logoutWithoutToken() = run { c ->
        c.post("/api/v1/auth/logout").expect(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
    }

    @Test fun logoutRevokesRefreshToken() = run { c ->
        val u = c.register("neg0@test.com")
        assertEquals(HttpStatusCode.NoContent, c.post("/api/v1/auth/logout") { auth(u.accessToken) }.status)
        assertEquals(
            HttpStatusCode.Unauthorized,
            c.post("/api/v1/auth/refresh") { json(RefreshRequest(u.refreshToken)) }.status,
        )
    }

    @Test fun brokenAccessTokenIsRejected() = run { c ->
        c.get("/api/v1/me/likes") { auth("not.a.jwt") }.expect(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
    }

    // --- tracks ---
    @Test fun trackBadIdAndUnknownId() = run { c ->
        c.get("/api/v1/tracks/not-a-uuid").expect(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        c.get("/api/v1/tracks/$zero").expect(HttpStatusCode.NotFound, "NOT_FOUND")
    }

    @Test fun trackListBadParams() = run { c ->
        c.get("/api/v1/tracks?page=0").expect(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        c.get("/api/v1/tracks?sort=weird").expect(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
    }

    @Test fun likeUnknownTrackAndNoToken() = run { c ->
        val u = c.register("neg2@test.com")
        c.put("/api/v1/tracks/$zero/like") { auth(u.accessToken) }.expect(HttpStatusCode.NotFound, "NOT_FOUND")
        c.delete("/api/v1/tracks/$zero/like").expect(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
    }

    // --- comments ---
    @Test fun commentsUnknownTrackNoTokenBadPosition() = run { c ->
        val u = c.register("neg3@test.com")
        val id = c.get("/api/v1/tracks").body<TrackPage>().items.first().id
        c.get("/api/v1/tracks/$zero/comments").expect(HttpStatusCode.NotFound, "NOT_FOUND")
        c.post("/api/v1/tracks/$id/comments") { json(CommentRequest("hi", 1)) }.expect(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
        c.post("/api/v1/tracks/$zero/comments") { auth(u.accessToken); json(CommentRequest("hi", 1)) }
            .expect(HttpStatusCode.NotFound, "NOT_FOUND")
        c.post("/api/v1/tracks/$id/comments") { auth(u.accessToken); json(CommentRequest("hi", -5)) }
            .expect(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        c.post("/api/v1/tracks/$id/comments") { auth(u.accessToken); json(CommentRequest("x".repeat(501), 1)) }
            .expect(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
    }

    @Test fun deleteForeignOrUnknownComment() = run { c ->
        val a = c.register("neg4@test.com")
        val b = c.register("neg5@test.com")
        val id = c.get("/api/v1/tracks").body<TrackPage>().items.first().id
        val created: CommentDto = c.post("/api/v1/tracks/$id/comments") { auth(a.accessToken); json(CommentRequest("мой", 1)) }.body()
        c.delete("/api/v1/comments/${created.id}") { auth(b.accessToken) }.expect(HttpStatusCode.Forbidden, "FORBIDDEN")
        c.delete("/api/v1/comments/$zero") { auth(a.accessToken) }.expect(HttpStatusCode.NotFound, "NOT_FOUND")
        c.delete("/api/v1/comments/${created.id}").expect(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
    }

    // --- playlists ---
    @Test fun playlistCreateErrors() = run { c ->
        val u = c.register("neg6@test.com")
        c.post("/api/v1/playlists") { json(PlaylistCreateRequest("x", emptyList())) }.expect(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
        c.post("/api/v1/playlists") { auth(u.accessToken); json(PlaylistCreateRequest("", emptyList())) }
            .expect(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        c.post("/api/v1/playlists") { auth(u.accessToken); json(PlaylistCreateRequest("x", listOf("bad"))) }
            .expect(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        c.post("/api/v1/playlists") { auth(u.accessToken); json(PlaylistCreateRequest("x", listOf(zero))) }
            .expect(HttpStatusCode.NotFound, "NOT_FOUND")
    }

    @Test fun shareForeignPlaylistAndUnknownCode() = run { c ->
        val a = c.register("neg7@test.com")
        val b = c.register("neg8@test.com")
        val pl: PlaylistDto = c.post("/api/v1/playlists") { auth(a.accessToken); json(PlaylistCreateRequest("мой", emptyList())) }.body()
        c.post("/api/v1/playlists/${pl.id}/share") { auth(b.accessToken) }.expect(HttpStatusCode.Forbidden, "FORBIDDEN")
        c.post("/api/v1/playlists/$zero/share") { auth(a.accessToken) }.expect(HttpStatusCode.NotFound, "NOT_FOUND")
        c.get("/api/v1/playlists/shared/nope1234").expect(HttpStatusCode.NotFound, "NOT_FOUND")
    }

    @Test fun importErrors() = run { c ->
        val u = c.register("neg9@test.com")
        c.post("/api/v1/playlists/import/match") { json(ImportRequest(listOf("a"))) }.expect(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
        c.post("/api/v1/playlists/import/match") { auth(u.accessToken); json(ImportRequest(emptyList())) }
            .expect(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        c.post("/api/v1/playlists/import/match") { auth(u.accessToken); json(ImportRequest(List(201) { "song $it" })) }
            .expect(HttpStatusCode.PayloadTooLarge, "PAYLOAD_TOO_LARGE")
    }

    // --- personal ---
    @Test fun listeningEventsErrors() = run { c ->
        val u = c.register("neg10@test.com")
        val id = c.get("/api/v1/tracks").body<TrackPage>().items.first().id
        c.post("/api/v1/me/listening-events") { json(ListeningEventsRequest(emptyList())) }.expect(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
        c.post("/api/v1/me/listening-events") { auth(u.accessToken); json(ListeningEventsRequest(listOf(ListeningEventDto("bad", "PLAY", 1)))) }
            .expect(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        c.post("/api/v1/me/listening-events") { auth(u.accessToken); json(ListeningEventsRequest(listOf(ListeningEventDto(id, "DANCE", 1)))) }
            .expect(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        c.post("/api/v1/me/listening-events") {
            auth(u.accessToken); json(ListeningEventsRequest(List(201) { ListeningEventDto(id, "PLAY", 1) }))
        }.expect(HttpStatusCode.PayloadTooLarge, "PAYLOAD_TOO_LARGE")
    }

    @Test fun tasteRecommendationsAndFriendCodeNeedAuth() = run { c ->
        val u = c.register("neg11@test.com")
        c.put("/api/v1/me/taste-profile") { auth(u.accessToken); contentType(ContentType.Application.Json); setBody("{}") }
            .expect(HttpStatusCode.BadRequest, "VALIDATION_ERROR")
        c.put("/api/v1/me/taste-profile") { json(TasteSettingsRequest(true)) }.expect(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
        c.delete("/api/v1/me/taste-profile").expect(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
        c.get("/api/v1/me/recommendations").expect(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
        c.get("/api/v1/me/friend-code").expect(HttpStatusCode.Unauthorized, "UNAUTHORIZED")
    }

    @Test fun unknownPathHasUnifiedError() = run { c ->
        c.get("/api/v1/nothing-here").expect(HttpStatusCode.NotFound, "NOT_FOUND")
    }
}
