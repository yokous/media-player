package com.example.mediaplayer.server.db

import com.example.mediaplayer.server.config.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init(config: AppConfig) {
        val hikari = HikariConfig().apply {
            jdbcUrl = config.dbUrl
            username = config.dbUser
            password = config.dbPassword
            driverClassName = config.dbDriver
            maximumPoolSize = 5 // небольшой пул: на сервере мало памяти
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            validate()
        }
        Database.connect(HikariDataSource(hikari))
        transaction {
            SchemaUtils.create(*AllTables)
            seedRoles()
            if (config.seedDemoData) seedTracks()
        }
    }

    private fun seedRoles() {
        if (Roles.selectAll().empty()) {
            listOf(ROLE_USER, ROLE_MODERATOR).forEach { name -> Roles.insert { it[Roles.name] = name } }
        }
    }

    private fun seedTracks() {
        if (!Tracks.selectAll().empty()) return
        // Демо-каталог. Адреса аудио условные: замените на реальные файлы (практика 10).
        val demo = listOf(
            listOf("Midnight City", "M83", "synthwave", "243000"),
            listOf("Nightcall", "Kavinsky", "synthwave", "258000"),
            listOf("Resonance", "HOME", "synthwave", "212000"),
            listOf("Intro", "The xx", "indie", "128000"),
            listOf("Time", "Hans Zimmer", "soundtrack", "275000"),
            listOf("Genesis", "Justice", "electro", "234000"),
            listOf("Sunset Lover", "Petit Biscuit", "electro", "238000"),
            listOf("Teardrop", "Massive Attack", "trip-hop", "329000"),
            listOf("Weightless", "Marconi Union", "ambient", "480000"),
            listOf("Strobe", "deadmau5", "electro", "637000"),
            listOf("Re: Stacks", "Bon Iver", "indie", "401000"),
            listOf("Experience", "Ludovico Einaudi", "soundtrack", "315000"),
        )
        demo.forEachIndexed { i, (title, artist, genre, dur) ->
            Tracks.insert {
                it[Tracks.title] = title
                it[Tracks.artist] = artist
                it[Tracks.genre] = genre
                it[durationMs] = dur.toLong()
                it[audioUrl] = "https://example.com/audio/${i + 1}.mp3"
                it[coverUrl] = null
            }
        }
    }
}

const val ROLE_USER = "user"
const val ROLE_MODERATOR = "moderator"

suspend fun <T> dbQuery(block: Transaction.() -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }
