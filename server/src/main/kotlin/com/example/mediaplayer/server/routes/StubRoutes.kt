package com.example.mediaplayer.server.routes

import com.example.mediaplayer.server.errors.ApiException
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

/** Распознавание по фото/микрофону и «Дуэт» зависят от внешнего ИИ — делаются в практике 10. */
fun Route.stubRoutes() {
    authenticate("auth-jwt") {
        post("/recognition") {
            throw ApiException(HttpStatusCode.ServiceUnavailable, "SERVICE_UNAVAILABLE", "Распознавание пока недоступно")
        }
        post("/duets") {
            throw ApiException(HttpStatusCode.ServiceUnavailable, "SERVICE_UNAVAILABLE", "Режим «Дуэт» пока недоступен")
        }
    }
}
