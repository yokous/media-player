package com.example.mediaplayer.server.routes

import com.example.mediaplayer.server.auth.TokenService
import com.example.mediaplayer.server.external.AiClient
import com.example.mediaplayer.server.external.RecognitionClient
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

val GlobalLimit = RateLimitName("global")
val AuthLimit = RateLimitName("auth")
val AiLimit = RateLimitName("ai")

fun Route.apiRoutes(tokens: TokenService, ai: AiClient, recognition: RecognitionClient) {
    route("/api/v1") {
        healthRoutes()
        rateLimit(GlobalLimit) {
            rateLimit(AuthLimit) { authRoutes(tokens) }
            trackRoutes()
            commentRoutes()
            playlistRoutes()
            rateLimit(AiLimit) { integrationRoutes(ai, recognition) }
            meRoutes(ai)
        }
    }
}
