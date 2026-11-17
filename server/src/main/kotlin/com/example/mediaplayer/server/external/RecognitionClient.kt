package com.example.mediaplayer.server.external

import com.example.mediaplayer.server.errors.ApiException
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

data class RecognizedSong(val title: String, val artist: String)

/** Внешний сервис распознавания музыки. null = песня не распознана. Сбой сервиса = ApiException 502. */
interface RecognitionClient {
    val enabled: Boolean
    suspend fun recognize(audio: ByteArray, filename: String): RecognizedSong?
}

object NoRecognitionClient : RecognitionClient {
    override val enabled = false
    override suspend fun recognize(audio: ByteArray, filename: String): RecognizedSong? = null
}

/** AudD (api.audd.io). Ключ только из переменной окружения RECOGNITION_API_KEY. */
class AuddRecognitionClient(private val apiKey: String) : RecognitionClient {
    private val log = LoggerFactory.getLogger("RecognitionClient")
    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient(CIO) {
        install(HttpTimeout) { requestTimeoutMillis = 20_000; connectTimeoutMillis = 5_000 }
        expectSuccess = false
    }

    override val enabled = true

    override suspend fun recognize(audio: ByteArray, filename: String): RecognizedSong? {
        val upstream = ApiException(HttpStatusCode.BadGateway, "UPSTREAM_ERROR", "Сервис распознавания временно недоступен")
        try {
            val resp = http.submitFormWithBinaryData(
                url = "https://api.audd.io/",
                formData = formData {
                    append("api_token", apiKey)
                    append("file", audio, Headers.build {
                        append(HttpHeaders.ContentType, "application/octet-stream")
                        append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                    })
                },
            )
            if (!resp.status.isSuccess()) {
                log.warn("Сервис распознавания ответил кодом {}", resp.status.value)
                throw upstream
            }
            val root: JsonObject = json.parseToJsonElement(resp.bodyAsText()).jsonObject
            if (root["status"]?.jsonPrimitive?.content != "success") {
                log.warn("Сервис распознавания вернул ошибку")
                throw upstream
            }
            val result = root["result"]
            if (result == null || result is JsonNull) return null
            val obj = result.jsonObject
            val title = obj["title"]?.jsonPrimitive?.content ?: return null
            val artist = obj["artist"]?.jsonPrimitive?.content.orEmpty()
            return RecognizedSong(title, artist)
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            log.warn("Сервис распознавания недоступен: {}", e.message)
            throw upstream
        }
    }
}
