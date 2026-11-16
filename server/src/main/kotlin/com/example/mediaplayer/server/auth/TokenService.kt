package com.example.mediaplayer.server.auth

import com.example.mediaplayer.server.config.AppConfig
import com.example.mediaplayer.server.db.RefreshTokens
import com.example.mediaplayer.server.dto.AuthResponse
import com.example.mediaplayer.server.dto.UserDto
import com.example.mediaplayer.server.errors.ApiException
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

/** Пара токенов: access (JWT) и одноразовый refresh (в базе лежит только его хеш). */
class TokenService(private val config: AppConfig, private val jwt: JwtService) {
    private val random = SecureRandom()

    fun issue(tx: Transaction, user: UserDto): AuthResponse {
        val raw = ByteArray(32).also { random.nextBytes(it) }
        val refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        RefreshTokens.insert {
            it[userId] = UUID.fromString(user.id)
            it[tokenHash] = sha256(refresh)
            it[expiresAt] = Instant.now().plusSeconds(config.refreshTtlSeconds)
        }
        return AuthResponse(
            accessToken = jwt.createAccessToken(UUID.fromString(user.id), user.role),
            refreshToken = refresh,
            expiresIn = config.accessTtlSeconds,
            user = user,
        )
    }

    /**
     * Проверяет refresh-токен и отзывает его. Возвращает id пользователя
     * или null, если токен уже использовался (тогда отозваны все токены пользователя —
     * ошибку нужно бросить уже ПОСЛЕ коммита транзакции, иначе отзыв откатится).
     */
    fun consume(tx: Transaction, refreshToken: String): UUID? {
        val row = RefreshTokens.selectAll().where { RefreshTokens.tokenHash eq sha256(refreshToken) }.singleOrNull()
            ?: throw ApiException.unauthorized(message = "Refresh-токен не найден")
        val uid = row[RefreshTokens.userId]
        if (row[RefreshTokens.revoked]) {
            // Повторное использование: считаем токен украденным и отзываем все.
            revokeAll(tx, uid)
            return null
        }
        if (row[RefreshTokens.expiresAt].isBefore(Instant.now())) {
            throw ApiException.unauthorized("TOKEN_EXPIRED", "Срок действия токена истёк")
        }
        RefreshTokens.update({ RefreshTokens.id eq row[RefreshTokens.id] }) { it[revoked] = true }
        return uid
    }

    fun revokeAll(tx: Transaction, userId: UUID) {
        RefreshTokens.update({ (RefreshTokens.userId eq userId) and (RefreshTokens.revoked eq false) }) {
            it[revoked] = true
        }
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
