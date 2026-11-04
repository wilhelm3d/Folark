package org.arkikeskus.launcher.data.search

import java.text.Normalizer

/**
 * How well an app name matches what was typed, for the drawer's search and the split picker: the
 * best kind of match wins, so the app you meant comes first even when you typed it sloppily.
 *
 * From best to weakest: the name starts with it ("gma" → Gmail); a word in it starts with it
 * ("maps" → Google Maps); the words' first letters ("gm" → Google Maps, "yt" → YouTube's two
 * capitals); anywhere in the name; the letters in order with gaps ("gml" → Gmail); and one typo
 * (a wrong, missing, extra or swapped letter) in a word, for four or more letters typed
 * ("spotfy" → Spotify). Case and accents don't matter ("cafe" finds Café).
 */
object AppMatcher {

    /** Higher is better; null when [label] doesn't match [query] at all. */
    fun score(label: String, query: String): Int? {
        val q = fold(query.trim())
        if (q.isEmpty()) return null
        val name = fold(label)
        val words = name.split(SEPARATORS).filter { it.isNotEmpty() }
        val bonus = (64 - label.length).coerceIn(0, 63) // shorter names first within a kind
        return when {
            name.startsWith(q) -> 6000 + bonus
            words.any { it.startsWith(q) } -> 5000 + bonus
            q.length >= 2 && initials(label).startsWith(q) -> 4000 + bonus
            name.contains(q) -> 3000 + bonus
            q.length >= 3 && inOrder(name.replace(" ", ""), q.replace(" ", "")) -> 2000 + bonus
            q.length >= 4 && words.any { w -> oneTypo(w.take(q.length + 1), q) || oneTypo(w.take(q.length), q) } -> 1000 + bonus
            else -> null
        }
    }

    /** [items] that match [query], best first (ties keep their order). */
    fun <T> rank(items: List<T>, query: String, label: (T) -> String): List<T> {
        if (query.isBlank()) return items
        return items.mapIndexedNotNull { i, item -> score(label(item), query)?.let { Triple(item, it, i) } }
            .sortedWith(compareByDescending<Triple<T, Int, Int>> { it.second }.thenBy { it.third })
            .map { it.first }
    }

    private val SEPARATORS = Regex("[\\s\\-_.:/&+()]+")
    private val MARKS = Regex("\\p{Mn}+")

    /** Lower case, without accents. */
    private fun fold(text: String): String =
        MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase()

    /** The first letter of each word, and of each capital inside a word ("YouTube" → "yt"). */
    private fun initials(label: String): String = buildString {
        var startOfWord = true
        for (c in label) {
            when {
                !c.isLetterOrDigit() -> startOfWord = true
                startOfWord -> { append(c); startOfWord = false }
                c.isUpperCase() -> append(c)
            }
        }
    }.let(::fold)

    /** Every letter of [q] appears in [name], in order. */
    private fun inOrder(name: String, q: String): Boolean {
        var i = 0
        for (c in name) if (i < q.length && c == q[i]) i++
        return i == q.length
    }

    /** [a] and [b] differ by at most one edit (replace, insert, delete, or two swapped letters). */
    internal fun oneTypo(a: String, b: String): Boolean {
        if (a == b) return true
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        if (a.length == b.length) {
            val diff = a.indices.filter { a[it] != b[it] }
            return diff.size == 1 ||
                (diff.size == 2 && diff[1] == diff[0] + 1 && a[diff[0]] == b[diff[1]] && a[diff[1]] == b[diff[0]])
        }
        val (long, short) = if (a.length > b.length) a to b else b to a
        var i = 0
        var j = 0
        var skipped = false
        while (i < long.length && j < short.length) {
            if (long[i] == short[j]) { i++; j++ } else { if (skipped) return false; skipped = true; i++ }
        }
        return true
    }
}
