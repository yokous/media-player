package com.example.mediaplayer.server.errors

import com.example.mediaplayer.server.dto.ErrorBody
import com.example.mediaplayer.server.dto.ErrorDetail
import com.example.mediaplayer.server.dto.ErrorResponse
import io.ktor.http.HttpStatusCode

/** Ошибка, которую сервер возвращает клиенту в едином формате (см. docs/08-backend.md, раздел 5). */
class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
    val details: List<ErrorDetail>? = null,
) : RuntimeException(message) {

    fun toResponse(requestId: String?) =
        ErrorResponse(ErrorBody(code = code, message = message, details = details, requestId = requestId))

    companion object {
        fun validation(message: String, field: String? = null, issue: String? = null) = ApiException(
            HttpStatusCode.BadRequest, "VALIDATION_ERROR", message,
            if (field != null && issue != null) listOf(ErrorDetail(field, issue)) else null,
        )

        fun unauthorized(code: String = "UNAUTHORIZED", message: String = "Требуется вход в аккаунт") =
            ApiException(HttpStatusCode.Unauthorized, code, message)

        fun forbidden(message: String = "Недостаточно прав") =
            ApiException(HttpStatusCode.Forbidden, "FORBIDDEN", message)

        fun notFound(message: String = "Объект не найден") =
            ApiException(HttpStatusCode.NotFound, "NOT_FOUND", message)

        fun conflict(code: String, message: String) =
            ApiException(HttpStatusCode.Conflict, code, message)

        fun tooLarge(message: String) =
            ApiException(HttpStatusCode.PayloadTooLarge, "PAYLOAD_TOO_LARGE", message)
    }
}
