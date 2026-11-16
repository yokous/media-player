package com.example.mediaplayer.server.routes

import com.example.mediaplayer.server.dto.HealthDto
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.healthRoutes() {
    get("/health") { call.respond(HealthDto("ok", "0.3.0")) }
}
