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
            )
        }
    }
}
