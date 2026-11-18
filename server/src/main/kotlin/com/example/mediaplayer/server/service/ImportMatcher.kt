package com.example.mediaplayer.server.service

/** Нечёткое сопоставление строк импортированного плейлиста с треками каталога (FR-12). */
object ImportMatcher {
    const val FOUND_THRESHOLD = 0.97
    const val DOUBTFUL_THRESHOLD = 0.6

    data class Candidate<T>(val item: T, val score: Double)

    fun normalize(s: String): String =
        s.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    /** Оценка схожести строки из файла и трека: от 0 до 1. */
    fun score(line: String, title: String, artist: String): Double {
        val l = normalize(line)
        if (l.isEmpty()) return 0.0
        val variants = listOf("$artist $title", "$title $artist", title)
        return variants.maxOf { similarity(l, normalize(it)) }
    }

    fun <T> rank(line: String, items: List<T>, titleOf: (T) -> String, artistOf: (T) -> String): List<Candidate<T>> =
        items.map { Candidate(it, score(line, titleOf(it), artistOf(it))) }
            .filter { it.score >= DOUBTFUL_THRESHOLD }
            .sortedByDescending { it.score }

    fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / maxLen
    }

    fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
