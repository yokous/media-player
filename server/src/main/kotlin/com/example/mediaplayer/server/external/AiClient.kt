package com.example.mediaplayer.server.external

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

/** Клиент ИИ. Любая ошибка превращается в null: приложение при этом работает без объяснений. */
interface AiClient {
    val enabled: Boolean
    suspend fun complete(prompt: String): String?
}

object NoAiClient : AiClient {
    override val enabled = false
    override suspend fun complete(prompt: String): String? = null
}

/** Вызывает Anthropic Messages API. Ключ берётся только из переменной окружения AI_API_KEY. */
class AnthropicAiClient(private val apiKey: String, private val model: String) : AiClient {
    private val log = LoggerFactory.getLogger("AiClient")
    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient(CIO) {
        install(HttpTimeout) { requestTimeoutMillis = 15_000; connectTimeoutMillis = 5_000 }
        expectSuccess = false
    }

    override val enabled = true

    override suspend fun complete(prompt: String): String? = try {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 300)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "user"); put("content", prompt) })
            })
        }
        val resp = http.post("https://api.anthropic.com/v1/messages") {
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        if (!resp.status.isSuccess()) {
            log.warn("ИИ-API ответил кодом {}", resp.status.value)
            null
        } else {
            val root: JsonObject = json.parseToJsonElement(resp.bodyAsText()).jsonObject
            val content: JsonArray = root["content"]?.jsonArray ?: JsonArray(emptyList())
            content.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
        }
    } catch (e: Exception) {
        log.warn("ИИ-API недоступен: {}", e.message)
        null
    }
}
