package com.example.mediaplayer.server

import com.example.mediaplayer.server.auth.JwtService
import com.example.mediaplayer.server.auth.TokenService
import com.example.mediaplayer.server.config.AppConfig
import com.example.mediaplayer.server.db.DatabaseFactory
import com.example.mediaplayer.server.dto.ErrorBody
import com.example.mediaplayer.server.dto.ErrorResponse
import com.example.mediaplayer.server.errors.ApiException
import com.example.mediaplayer.server.external.AiClient
import com.example.mediaplayer.server.external.AnthropicAiClient
import com.example.mediaplayer.server.external.AuddRecognitionClient
import com.example.mediaplayer.server.external.NoAiClient
import com.example.mediaplayer.server.external.NoRecognitionClient
import com.example.mediaplayer.server.external.RecognitionClient
import com.example.mediaplayer.server.routes.AiLimit
import com.example.mediaplayer.server.routes.AuthLimit
import com.example.mediaplayer.server.routes.GlobalLimit
import com.example.mediaplayer.server.routes.apiRoutes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.callid.generate
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import kotlin.time.Duration.Companion.seconds

val AppJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

private val log = LoggerFactory.getLogger("Application")

fun main() {
    val config = AppConfig.fromEnv()
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") { module(config) }.start(wait = true)
}

fun Application.module(
    config: AppConfig,
    ai: AiClient = config.aiApiKey?.let { AnthropicAiClient(it, config.aiModel) } ?: NoAiClient,
    recognition: RecognitionClient = config.recognitionApiKey?.let { AuddRecognitionClient(it) } ?: NoRecognitionClient,
) {
    DatabaseFactory.init(config)
    val jwt = JwtService(config)
    val tokens = TokenService(config, jwt)

    install(CallId) {
        header(HttpHeaders.XRequestId)
        generate(12, "abcdefghijklmnopqrstuvwxyz0123456789")
        replyToHeader(HttpHeaders.XRequestId)
    }
    install(CallLogging) {
        level = Level.INFO
        callIdMdc("requestId")
    }
    install(ContentNegotiation) { json(AppJson) }

    // Ограничение частоты запросов. За nginx настоящий адрес клиента приходит в X-Real-IP.
    val clientKey: (io.ktor.server.application.ApplicationCall) -> Any = { call ->
        call.request.headers["X-Real-IP"] ?: call.request.origin.remoteHost
    }
    install(RateLimit) {
        register(GlobalLimit) {
            rateLimiter(limit = config.rateLimitPerMinute, refillPeriod = 60.seconds)
            requestKey(clientKey)
        }
        register(AuthLimit) {
            rateLimiter(limit = config.authRateLimitPerMinute, refillPeriod = 60.seconds)
            requestKey(clientKey)
        }
        register(AiLimit) {
            rateLimiter(limit = config.aiRateLimitPerMinute, refillPeriod = 60.seconds)
            requestKey(clientKey)
        }
    }

    install(Authentication) {
        jwt("auth-jwt") {
            verifier(jwt.verifier)
            validate { cred -> if (cred.payload.subject != null) JWTPrincipal(cred.payload) else null }
            challenge { _, _ ->
                val token = call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()
                val code = jwt.failureCode(token)
                val msg = if (code == "TOKEN_EXPIRED") "Срок действия токена истёк" else "Требуется вход в аккаунт"
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse(ErrorBody(code = code, message = msg, requestId = call.callId)),
                )
            }
        }
    }

    install(StatusPages) {
        exception<ApiException> { call, e -> call.respond(e.status, e.toResponse(call.callId)) }
        exception<BadRequestException> { call, _ ->
            val e = ApiException.validation("Тело запроса не соответствует формату JSON")
            call.respond(e.status, e.toResponse(call.callId))
        }
        exception<kotlinx.serialization.SerializationException> { call, _ ->
            val e = ApiException.validation("Тело запроса не соответствует формату JSON")
            call.respond(e.status, e.toResponse(call.callId))
        }
        status(HttpStatusCode.TooManyRequests) { call, _ ->
            log.warn("Превышен лимит запросов: {} {}", call.request.local.method.value, call.request.local.uri)
            call.respond(
                HttpStatusCode.TooManyRequests,
                ErrorResponse(ErrorBody("RATE_LIMITED", "Слишком много запросов, попробуйте позже", requestId = call.callId)),
            )
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            val e = ApiException.notFound("Такого адреса нет")
            call.respond(e.status, e.toResponse(call.callId))
        }
        exception<Throwable> { call, t ->
            log.error("Необработанная ошибка, requestId=${call.callId}", t)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(ErrorBody("INTERNAL_ERROR", "Внутренняя ошибка сервера", requestId = call.callId)),
            )
        }
    }

    routing { apiRoutes(tokens, ai, recognition) }
}
