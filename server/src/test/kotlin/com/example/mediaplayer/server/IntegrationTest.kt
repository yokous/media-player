package com.example.mediaplayer.server

import com.example.mediaplayer.server.config.AppConfig
import com.example.mediaplayer.server.dto.*
import com.example.mediaplayer.server.errors.ApiException
import com.example.mediaplayer.server.external.AiClient
import com.example.mediaplayer.server.external.NoAiClient
import com.example.mediaplayer.server.external.NoRecognitionClient
import com.example.mediaplayer.server.external.RecognitionClient
import com.example.mediaplayer.server.external.RecognizedSong
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Тесты практики 10: внешние сервисы заменены заглушками, лимит запросов и единый формат ошибок. */
class IntegrationTest {
    private fun config(authLimit: Int = 20) = AppConfig(
        port = 0,
        dbUrl = "jdbc:h2:mem:apitest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        dbUser = "sa", dbPassword = "", dbDriver = "org.h2.Driver",
        jwtSecret = "test-secret-test-secret-test-secret-1234",
        authRateLimitPerMinute = authLimit,
    )

    private class FakeAi(private val answer: String?) : AiClient {
        override val enabled = true
        override suspend fun complete(prompt: String) = answer
    }

    private class FakeRecognition(private val song: RecognizedSong?, private val fail: Boolean = false) : RecognitionClient {
        override val enabled = true
        override suspend fun recognize(audio: ByteArray, filename: String): RecognizedSong? {
            if (fail) throw ApiException(HttpStatusCode.BadGateway, "UPSTREAM_ERROR", "Сервис распознавания временно недоступен")
            return song
        }
    }

    private fun run(
        cfg: AppConfig = config(),
        ai: AiClient = NoAiClient,
        rec: RecognitionClient = NoRecognitionClient,
        block: suspend ApplicationTestBuilder.(HttpClient) -> Unit,
    ) = testApplication {
        application { module(cfg, ai, rec) }
        block(createClient { install(ContentNegotiation) { json(AppJson) } })
    }

    private suspend fun HttpClient.register(email: String): AuthResponse =
        post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, "password123", "User ${email.take(3)}"))
        }.body()

    private fun HttpRequestBuilder.auth(t: String) = header(HttpHeaders.Authorization, "Bearer $t")

    private suspend fun HttpClient.upload(token: String, bytes: ByteArray?): io.ktor.client.statement.HttpResponse =
        submitFormWithBinaryData("/api/v1/recognition", formData {
            if (bytes != null) append("audio", bytes, Headers.build {
                append(HttpHeaders.ContentType, "audio/wav")
                append(HttpHeaders.ContentDisposition, "filename=\"a.wav\"")
            })
        }) { auth(token) }

    @Test fun rateLimitReturnsUnifiedError() = run(cfg = config(authLimit = 2)) { c ->
        repeat(2) {
            c.post("/api/v1/auth/login") {
                contentType(ContentType.Application.Json); setBody(LoginRequest("none@test.com", "password123"))
            }
        }
        val r = c.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json); setBody(LoginRequest("none@test.com", "password123"))
        }
        assertEquals(HttpStatusCode.TooManyRequests, r.status)
        assertEquals("RATE_LIMITED", r.body<ErrorResponse>().error.code)
    }

    @Test fun recognitionNotConfiguredIs503() = run { c ->
        val u = c.register("r1@test.com")
        val r = c.upload(u.accessToken, ByteArray(100))
        assertEquals(HttpStatusCode.ServiceUnavailable, r.status)
        assertEquals("SERVICE_UNAVAILABLE", r.body<ErrorResponse>().error.code)
    }

    @Test fun recognitionRequiresAuthAndFile() = run(rec = FakeRecognition(null)) { c ->
        assertEquals(HttpStatusCode.Unauthorized, c.post("/api/v1/recognition").status)
        val u = c.register("r2@test.com")
        val r = c.upload(u.accessToken, null)
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }

    @Test fun recognitionMatchesCatalogTrack() = run(rec = FakeRecognition(RecognizedSong("Midnight City", "M83"))) { c ->
        val u = c.register("r3@test.com")
        val res: RecognitionResult = c.upload(u.accessToken, ByteArray(500)).body()
        assertTrue(res.recognized)
        assertEquals("Midnight City", res.track?.title)
    }

    @Test fun recognitionNotFoundAndUpstreamFailure() {
        run(rec = FakeRecognition(null)) { c ->
            val u = c.register("r4@test.com")
            val res: RecognitionResult = c.upload(u.accessToken, ByteArray(500)).body()
            assertEquals(false, res.recognized)
        }
        run(rec = FakeRecognition(null, fail = true)) { c ->
            val u = c.register("r5@test.com")
            val r = c.upload(u.accessToken, ByteArray(500))
            assertEquals(HttpStatusCode.BadGateway, r.status)
            assertEquals("UPSTREAM_ERROR", r.body<ErrorResponse>().error.code)
        }
    }

    @Test fun recognitionTooLargeIs413() = run(rec = FakeRecognition(null)) { c ->
        val u = c.register("r6@test.com")
        assertEquals(HttpStatusCode.PayloadTooLarge, c.upload(u.accessToken, ByteArray(1_100_000)).status)
    }

    @Test fun recommendationsExplanationFromAiOrNull() {
        run(ai = FakeAi("Потому что вы любите синтвейв.")) { c ->
            val u = c.register("e1@test.com")
            val t = c.get("/api/v1/tracks").body<TrackPage>().items.first()
            c.put("/api/v1/tracks/${t.id}/like") { auth(u.accessToken) }
            val rec: RecommendationsResponse = c.get("/api/v1/me/recommendations") { auth(u.accessToken) }.body()
            assertEquals("Потому что вы любите синтвейв.", rec.explanation)
        }
        run(ai = FakeAi(null)) { c ->
            val u = c.register("e2@test.com")
            val t = c.get("/api/v1/tracks").body<TrackPage>().items.first()
            c.put("/api/v1/tracks/${t.id}/like") { auth(u.accessToken) }
            val rec: RecommendationsResponse = c.get("/api/v1/me/recommendations") { auth(u.accessToken) }.body()
            assertTrue(rec.personalized)
            assertNull(rec.explanation)
        }
    }

    private suspend fun HttpClient.friendCode(t: String): String =
        get("/api/v1/me/friend-code") { auth(t) }.body<FriendCodeResponse>().friendCode

    private suspend fun HttpClient.duet(t: String, code: String) =
        post("/api/v1/duets") { auth(t); contentType(ContentType.Application.Json); setBody(DuetRequest(code)) }

    @Test fun duetBuildsPlaylistAndValidates() = run(ai = FakeAi("Вам обоим нравится синтвейв.")) { c ->
        val a = c.register("d1@test.com")
        val b = c.register("d2@test.com")
        val tracks = c.get("/api/v1/tracks").body<TrackPage>().items
        c.put("/api/v1/tracks/${tracks[0].id}/like") { auth(a.accessToken) }
        c.put("/api/v1/tracks/${tracks[0].id}/like") { auth(b.accessToken) }

        val ok = c.duet(a.accessToken, c.friendCode(b.accessToken))
        assertEquals(HttpStatusCode.OK, ok.status)
        val duet: DuetResponse = ok.body()
        assertEquals(100, duet.matchPercent)
        assertTrue(duet.playlist.tracks.isNotEmpty())
        assertEquals("Вам обоим нравится синтвейв.", duet.explanation)

        assertEquals(HttpStatusCode.BadRequest, c.duet(a.accessToken, c.friendCode(a.accessToken)).status)
        assertEquals(HttpStatusCode.BadRequest, c.duet(a.accessToken, "bad").status)
        assertEquals(HttpStatusCode.NotFound, c.duet(a.accessToken, "0".repeat(32)).status)
        assertEquals(HttpStatusCode.Unauthorized, c.post("/api/v1/duets").status)
    }

    @Test fun duetUnavailableWhenHistoryDisabled() = run { c ->
        val a = c.register("d3@test.com")
        val b = c.register("d4@test.com")
        c.put("/api/v1/me/taste-profile") {
            auth(b.accessToken); contentType(ContentType.Application.Json); setBody(TasteSettingsRequest(false))
        }
        val r = c.duet(a.accessToken, c.friendCode(b.accessToken))
        assertEquals(HttpStatusCode.Conflict, r.status)
        assertEquals("DUET_UNAVAILABLE", r.body<ErrorResponse>().error.code)
    }
}
