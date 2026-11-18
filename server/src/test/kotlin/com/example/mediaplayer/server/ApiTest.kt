package com.example.mediaplayer.server

import com.example.mediaplayer.server.config.AppConfig
import com.example.mediaplayer.server.dto.*
import com.example.mediaplayer.server.service.ImportMatcher
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiTest {
    // Одна общая тестовая база на все тесты; e-mail в каждом тесте свой.
    private fun config() = AppConfig(
        port = 0,
        dbUrl = "jdbc:h2:mem:apitest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        dbUser = "sa", dbPassword = "", dbDriver = "org.h2.Driver",
        jwtSecret = "test-secret-test-secret-test-secret-1234",
    )

    private fun run(block: suspend ApplicationTestBuilder.(io.ktor.client.HttpClient) -> Unit) = testApplication {
        application { module(config()) }
        val client = createClient { install(ContentNegotiation) { json(AppJson) } }
        block(client)
    }

    private suspend fun io.ktor.client.HttpClient.register(email: String): AuthResponse =
        post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, "password123", "Test"))
        }.body()

    private fun HttpRequestBuilder.auth(t: String) = header(HttpHeaders.Authorization, "Bearer $t")

    @Test fun health() = run { c -> assertEquals(HttpStatusCode.OK, c.get("/api/v1/health").status) }

    @Test fun registerAndDuplicateEmail() = run { c ->
        c.register("a@test.com")
        val r = c.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json); setBody(RegisterRequest("a@test.com", "password123", "X"))
        }
        assertEquals(HttpStatusCode.Conflict, r.status)
        assertEquals("EMAIL_TAKEN", r.body<ErrorResponse>().error.code)
    }

    @Test fun registerValidation() = run { c ->
        val r = c.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json); setBody(RegisterRequest("bad", "1", ""))
        }
        assertEquals(HttpStatusCode.BadRequest, r.status)
        assertEquals("VALIDATION_ERROR", r.body<ErrorResponse>().error.code)
    }

    @Test fun loginWrongPassword() = run { c ->
        c.register("b@test.com")
        val r = c.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json); setBody(LoginRequest("b@test.com", "wrongpass"))
        }
        assertEquals(HttpStatusCode.Unauthorized, r.status)
        assertEquals("INVALID_CREDENTIALS", r.body<ErrorResponse>().error.code)
    }

    @Test fun refreshRotatesAndReuseIsRejected() = run { c ->
        val a = c.register("c@test.com")
        val ok = c.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json); setBody(RefreshRequest(a.refreshToken))
        }
        assertEquals(HttpStatusCode.OK, ok.status)
        val again = c.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json); setBody(RefreshRequest(a.refreshToken))
        }
        assertEquals(HttpStatusCode.Unauthorized, again.status)
    }

    @Test fun reuseRevokesWholeChain() = run { c ->
        val a = c.register("reuse@test.com")
        val b: AuthResponse = c.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json); setBody(RefreshRequest(a.refreshToken))
        }.body()
        c.post("/api/v1/auth/refresh") { contentType(ContentType.Application.Json); setBody(RefreshRequest(a.refreshToken)) }
        val after = c.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json); setBody(RefreshRequest(b.refreshToken))
        }
        assertEquals(HttpStatusCode.Unauthorized, after.status)
    }

    @Test fun protectedWithoutToken() = run { c ->
        val r = c.get("/api/v1/me/likes")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
        assertEquals("UNAUTHORIZED", r.body<ErrorResponse>().error.code)
    }

    @Test fun catalogPagingAndSearch() = run { c ->
        val page: TrackPage = c.get("/api/v1/tracks?pageSize=5").body()
        assertEquals(5, page.items.size)
        assertTrue(page.total >= 12)
        val first = page.items.first()
        val found: TrackPage = c.get("/api/v1/tracks?query=${first.title.take(4)}").body()
        assertTrue(found.items.any { it.id == first.id })
        assertEquals(HttpStatusCode.BadRequest, c.get("/api/v1/tracks?pageSize=500").status)
    }

    @Test fun likeIsIdempotentAndCounted() = run { c ->
        val u = c.register("d@test.com")
        val t: TrackPage = c.get("/api/v1/tracks").body()
        val id = t.items.first().id
        val s1: LikeState = c.put("/api/v1/tracks/$id/like") { auth(u.accessToken) }.body()
        val s2: LikeState = c.put("/api/v1/tracks/$id/like") { auth(u.accessToken) }.body()
        assertEquals(s1.likesCount, s2.likesCount)
        val mine: TrackPage = c.get("/api/v1/me/likes") { auth(u.accessToken) }.body()
        assertEquals(1, mine.total)
        val s3: LikeState = c.delete("/api/v1/tracks/$id/like") { auth(u.accessToken) }.body()
        assertEquals(s1.likesCount - 1, s3.likesCount)
    }

    @Test fun commentsLifecycle() = run { c ->
        val u = c.register("e@test.com")
        val other = c.register("f@test.com")
        val id = c.get("/api/v1/tracks").body<TrackPage>().items.first().id
        val bad = c.post("/api/v1/tracks/$id/comments") {
            auth(u.accessToken); contentType(ContentType.Application.Json); setBody(CommentRequest("", 0))
        }
        assertEquals(HttpStatusCode.BadRequest, bad.status)
        val created = c.post("/api/v1/tracks/$id/comments") {
            auth(u.accessToken); contentType(ContentType.Application.Json); setBody(CommentRequest("Класс!", 1000))
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val cm: CommentDto = created.body()
        assertEquals(HttpStatusCode.Forbidden, c.delete("/api/v1/comments/${cm.id}") { auth(other.accessToken) }.status)
        assertEquals(HttpStatusCode.NoContent, c.delete("/api/v1/comments/${cm.id}") { auth(u.accessToken) }.status)
        assertEquals(0, c.get("/api/v1/tracks/$id/comments").body<CommentPage>().total.toInt())
    }

    @Test fun playlistShareAndOpen() = run { c ->
        val u = c.register("g@test.com")
        val ids = c.get("/api/v1/tracks").body<TrackPage>().items.take(3).map { it.id }
        val pl: PlaylistDto = c.post("/api/v1/playlists") {
            auth(u.accessToken); contentType(ContentType.Application.Json); setBody(PlaylistCreateRequest("Мой", ids))
        }.body()
        assertEquals(3, pl.tracksCount)
        val share: ShareResponse = c.post("/api/v1/playlists/${pl.id}/share") { auth(u.accessToken) }.body()
        val opened: PlaylistWithTracksDto = c.get("/api/v1/playlists/shared/${share.shareCode}").body()
        assertEquals(3, opened.tracks.size)
        assertEquals(HttpStatusCode.NotFound, c.get("/api/v1/playlists/shared/nope").status)
    }

    @Test fun importMatchesFuzzy() = run { c ->
        val u = c.register("h@test.com")
        val t = c.get("/api/v1/tracks").body<TrackPage>().items.first()
        val rep: ImportReport = c.post("/api/v1/playlists/import/match") {
            auth(u.accessToken); contentType(ContentType.Application.Json)
            setBody(ImportRequest(listOf("${t.artist} - ${t.title}", "zzzzzz qqqqq xxxxx")))
        }.body()
        assertEquals(1, rep.found.size)
        assertEquals(1, rep.notFound.size)
    }

    @Test fun eventsTasteAndRecommendations() = run { c ->
        val u = c.register("i@test.com")
        val first = c.get("/api/v1/tracks").body<TrackPage>().items.first()
        val ev = c.post("/api/v1/me/listening-events") {
            auth(u.accessToken); contentType(ContentType.Application.Json)
            setBody(ListeningEventsRequest(listOf(ListeningEventDto(first.id, "PLAY", 1L))))
        }
        assertEquals(HttpStatusCode.Accepted, ev.status)
        val rec: RecommendationsResponse = c.get("/api/v1/me/recommendations") { auth(u.accessToken) }.body()
        assertTrue(rec.personalized)
        assertEquals(HttpStatusCode.NoContent, c.delete("/api/v1/me/taste-profile") { auth(u.accessToken) }.status)
        val rec2: RecommendationsResponse = c.get("/api/v1/me/recommendations") { auth(u.accessToken) }.body()
        assertTrue(!rec2.personalized)
    }

    @Test fun matcherNormalizesAndScores() {
        assertEquals(1.0, ImportMatcher.score("M83 - Midnight City!", "Midnight City", "M83"))
        assertTrue(ImportMatcher.score("totally different", "Midnight City", "M83") < ImportMatcher.DOUBTFUL_THRESHOLD)
        assertEquals(3, ImportMatcher.levenshtein("kitten", "sitting"))
    }
}
