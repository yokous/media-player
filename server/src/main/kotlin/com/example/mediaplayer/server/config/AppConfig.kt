package com.example.mediaplayer.server.config

/** Настройки сервера. Все секреты берутся только из переменных окружения. */
data class AppConfig(
    val port: Int,
    val dbUrl: String,
    val dbUser: String,
    val dbPassword: String,
    val dbDriver: String = "org.postgresql.Driver",
    val jwtSecret: String,
    val jwtIssuer: String = "media-player",
    val accessTtlSeconds: Long = 15 * 60,
    val refreshTtlSeconds: Long = 30L * 24 * 60 * 60,
    val seedDemoData: Boolean = true,
    /** Ключи внешних API. Только из окружения; если не заданы, функция отключена или работает без ИИ. */
    val aiApiKey: String? = null,
    val aiModel: String = "claude-haiku-5-5",
    val recognitionApiKey: String? = null,
    /** Ограничение частоты запросов (на один IP или пользователя в минуту). */
    val rateLimitPerMinute: Int = 120,
    val authRateLimitPerMinute: Int = 20,
    val aiRateLimitPerMinute: Int = 10,
) {
    companion object {
        fun fromEnv(): AppConfig {
            fun env(name: String, default: String? = null): String =
                System.getenv(name) ?: default ?: error("Не задана переменная окружения $name")

            val secret = env("JWT_SECRET")
            require(secret.length >= 32) { "JWT_SECRET должен быть не короче 32 символов" }
            return AppConfig(
                port = env("PORT", "8080").toInt(),
                dbUrl = env("DB_URL"),
                dbUser = env("DB_USER"),
                dbPassword = env("DB_PASSWORD"),
                jwtSecret = secret,
                seedDemoData = env("SEED_DEMO_DATA", "true").toBoolean(),
                aiApiKey = System.getenv("AI_API_KEY")?.takeIf { it.isNotBlank() },
                aiModel = env("AI_MODEL", "claude-haiku-5-5"),
                recognitionApiKey = System.getenv("RECOGNITION_API_KEY")?.takeIf { it.isNotBlank() },
                rateLimitPerMinute = env("RATE_LIMIT_PER_MINUTE", "120").toInt(),
                authRateLimitPerMinute = env("AUTH_RATE_LIMIT_PER_MINUTE", "20").toInt(),
                aiRateLimitPerMinute = env("AI_RATE_LIMIT_PER_MINUTE", "10").toInt(),
            )
        }
    }
}
