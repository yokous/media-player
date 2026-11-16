package com.example.mediaplayer.server.routes

import com.example.mediaplayer.server.auth.TokenService
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

fun Route.apiRoutes(tokens: TokenService) {
    route("/api/v1") {
        healthRoutes()
        authRoutes(tokens)
        trackRoutes()
        commentRoutes()
        playlistRoutes()
        meRoutes()
        stubRoutes()
    }
}
