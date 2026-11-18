package com.example.mediaplayer.server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.TokenExpiredException
import com.example.mediaplayer.server.config.AppConfig
import java.util.Date
import java.util.UUID

/** Выпуск и проверка access-токенов (JWT, HS256). */
class JwtService(private val config: AppConfig) {
    private val algorithm = Algorithm.HMAC256(config.jwtSecret)

    val verifier: JWTVerifier = JWT.require(algorithm).withIssuer(config.jwtIssuer).build()

    fun createAccessToken(userId: UUID, role: String): String {
        val now = System.currentTimeMillis()
        return JWT.create()
            .withIssuer(config.jwtIssuer)
            .withSubject(userId.toString())
            .withClaim("role", role)
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(now + config.accessTtlSeconds * 1000))
            .sign(algorithm)
    }

    /** Код ошибки для невалидного токена: просрочен или повреждён. */
    fun failureCode(token: String?): String {
        if (token.isNullOrBlank()) return "UNAUTHORIZED"
        return try {
            verifier.verify(token)
            "UNAUTHORIZED"
        } catch (e: TokenExpiredException) {
            "TOKEN_EXPIRED"
        } catch (e: Exception) {
            "UNAUTHORIZED"
        }
    }
}
