package com.example.mediaplayer.server.service

import com.example.mediaplayer.server.db.Likes
import com.example.mediaplayer.server.db.ListeningEvents
import com.example.mediaplayer.server.db.TasteProfiles
import com.example.mediaplayer.server.db.Tracks
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Профиль вкуса: вес жанра. Лайк = 3, прослушивание = 1 (если история включена). */
fun Transaction.historyEnabled(uid: UUID): Boolean =
    TasteProfiles.selectAll().where { TasteProfiles.userId eq uid }.singleOrNull()?.get(TasteProfiles.historyEnabled) ?: true

fun Transaction.genreWeights(uid: UUID, withPlays: Boolean): Map<String, Int> {
    val weights = HashMap<String, Int>()
    (Likes innerJoin Tracks).selectAll().where { Likes.userId eq uid }
        .forEach { weights.merge(it[Tracks.genre], 3, Int::plus) }
    if (withPlays) {
        (ListeningEvents innerJoin Tracks).selectAll()
            .where { (ListeningEvents.userId eq uid) and (ListeningEvents.type inList listOf("PLAY", "FINISH")) }
            .forEach { weights.merge(it[Tracks.genre], 1, Int::plus) }
    }
    return weights
}

/** Косинусное сходство двух профилей, в процентах (0..100). */
fun matchPercent(a: Map<String, Int>, b: Map<String, Int>): Int {
    if (a.isEmpty() || b.isEmpty()) return 0
    val dot = a.entries.sumOf { (g, w) -> (b[g] ?: 0) * w.toDouble() }
    val na = sqrt(a.values.sumOf { it.toDouble() * it })
    val nb = sqrt(b.values.sumOf { it.toDouble() * it })
    if (na == 0.0 || nb == 0.0) return 0
    return (dot / (na * nb) * 100).roundToInt().coerceIn(0, 100)
}
