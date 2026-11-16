package com.example.mediaplayer.server.routes

import com.example.mediaplayer.server.auth.PasswordHasher
import com.example.mediaplayer.server.auth.TokenService
import com.example.mediaplayer.server.db.Roles
import com.example.mediaplayer.server.db.TasteProfiles
import com.example.mediaplayer.server.db.Users
import com.example.mediaplayer.server.db.ROLE_USER
import com.example.mediaplayer.server.db.dbQuery
import com.example.mediaplayer.server.dto.LoginRequest
import com.example.mediaplayer.server.dto.RefreshRequest
import com.example.mediaplayer.server.dto.RegisterRequest
import com.example.mediaplayer.server.dto.UserDto
import com.example.mediaplayer.server.errors.ApiException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")

private fun ResultRow.toUserDto(roleName: String) = UserDto(
    id = this[Users.id].toString(),
    email = this[Users.email],
    displayName = this[Users.displayName],
    role = roleName,
)

fun Route.authRoutes(tokens: TokenService) {
    route("/auth") {
        post("/register") {
            val req = call.receive<RegisterRequest>()
            val email = req.email.trim().lowercase()
            if (!EMAIL_REGEX.matches(email) || email.length > 254) {
                throw ApiException.validation("Поле email заполнено неверно", "email", "invalid format")
            }
            if (req.password.length !in 8..72) {
                throw ApiException.validation("Пароль должен быть от 8 до 72 символов", "password", "length 8..72")
            }
            val name = req.displayName.trim()
            if (name.length !in 1..40) {
                throw ApiException.validation("Имя должно быть от 1 до 40 символов", "displayName", "length 1..40")
            }
            val hash = PasswordHasher.hash(req.password)
            val response = dbQuery {
                if (Users.selectAll().where { Users.email eq email }.any()) {
                    throw ApiException.conflict("EMAIL_TAKEN", "Этот e-mail уже зарегистрирован")
                }
                val roleId = Roles.selectAll().where { Roles.name eq ROLE_USER }.single()[Roles.id]
                val newId: UUID = Users.insert {
                    it[Users.email] = email
                    it[passwordHash] = hash
                    it[displayName] = name
                    it[Users.roleId] = roleId
                } get Users.id
                TasteProfiles.insert { it[TasteProfiles.userId] = newId }
                tokens.issue(this, UserDto(newId.toString(), email, name, ROLE_USER))
            }
            call.respond(HttpStatusCode.Created, response)
        }

        post("/login") {
            val req = call.receive<LoginRequest>()
            val email = req.email.trim().lowercase()
            if (email.isEmpty() || req.password.isEmpty()) {
                throw ApiException.validation("Введите e-mail и пароль", "email", "required")
            }
            val response = dbQuery {
                val row = (Users innerJoin Roles).selectAll().where { Users.email eq email }.singleOrNull()
                if (row == null || !PasswordHasher.verify(req.password, row[Users.passwordHash])) {
                    throw ApiException.unauthorized("INVALID_CREDENTIALS", "Неверный e-mail или пароль")
                }
                tokens.issue(this, row.toUserDto(row[Roles.name]))
            }
            call.respond(response)
        }

        post("/refresh") {
            val req = call.receive<RefreshRequest>()
            if (req.refreshToken.isBlank()) {
                throw ApiException.validation("Не передан refreshToken", "refreshToken", "required")
            }
            val response = dbQuery {
                val uid = tokens.consume(this, req.refreshToken)
                if (uid == null) null else {
                    val row = (Users innerJoin Roles).selectAll().where { Users.id eq uid }.single()
                    tokens.issue(this, row.toUserDto(row[Roles.name]))
                }
            } ?: throw ApiException.unauthorized(message = "Refresh-токен уже использован, войдите заново")
            call.respond(response)
        }

        authenticate("auth-jwt") {
            post("/logout") {
                val uid = call.userId()
                dbQuery { tokens.revokeAll(this, uid) }
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
