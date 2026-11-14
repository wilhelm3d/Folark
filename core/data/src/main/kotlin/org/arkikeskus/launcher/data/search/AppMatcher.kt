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
 *
 * A name is folded (accents and case removed, split into words, initials taken) once and kept, so
 * a keystroke costs a few string comparisons per app instead of normalising every name again.
 */
object AppMatcher {

    /** Higher is better; null when [label] doesn't match [query] at all. */
    fun score(label: String, query: String): Int? {
        val needle = Needle.of(query) ?: return null
        return score(prepared(label), needle)
    }

    /** [items] that match [query], best first (ties keep their order). */
    fun <T> rank(items: List<T>, query: String, label: (T) -> String): List<T> {
        if (query.isBlank()) return items
        // Nothing left of a query that was only accent marks: nothing matches it.
        val needle = Needle.of(query) ?: return emptyList()
        return items.mapIndexedNotNull { i, item -> score(prepared(label(item)), needle)?.let { Triple(item, it, i) } }
            .sortedWith(compareByDescending<Triple<T, Int, Int>> { it.second }.thenBy { it.third })
            .map { it.first }
    }

    private fun score(name: Prepared, needle: Needle): Int? {
        val q = needle.text
        return when {
            name.folded.startsWith(q) -> 6000 + name.bonus
            name.words.any { it.startsWith(q) } -> 5000 + name.bonus
            q.length >= 2 && name.initials.startsWith(q) -> 4000 + name.bonus
            name.folded.contains(q) -> 3000 + name.bonus
            q.length >= 3 && inOrder(name.compact, needle.compact) -> 2000 + name.bonus
            q.length >= 4 && name.words.any { w -> oneTypo(w.take(q.length + 1), q) || oneTypo(w.take(q.length), q) } -> 1000 + name.bonus
            else -> null
        }
    }

    /** What was typed, folded once for every name it is compared with. */
    private class Needle private constructor(val text: String) {
        /** [text] without spaces, for the letters-in-order match. */
        val compact = text.replace(" ", "")

        companion object {
            /** Null when nothing is left to look for. */
            fun of(query: String): Needle? = fold(query.trim()).takeIf { it.isNotEmpty() }?.let(::Needle)
        }
    }

    /** One app name, ready to be compared with any number of queries. */
    private class Prepared(label: String) {
        /** Lower case, without accents. */
        val folded = fold(label)
        val words = folded.split(SEPARATORS).filter { it.isNotEmpty() }
        val initials = initials(label)
        val compact = folded.replace(" ", "")
        /** Shorter names first within a kind. */
        val bonus = (64 - label.length).coerceIn(0, 63)
    }

    /**
     * The prepared names, most recently used last. A drawer holds a few hundred apps, so this stays
     * small; a renamed app is simply prepared again under its new name.
     */
    private val cache = object : LinkedHashMap<String, Prepared>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Prepared>) = size > MAX_PREPARED
    }

    private fun prepared(label: String): Prepared = synchronized(cache) { cache.getOrPut(label) { Prepared(label) } }

    private const val MAX_PREPARED = 1024

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
