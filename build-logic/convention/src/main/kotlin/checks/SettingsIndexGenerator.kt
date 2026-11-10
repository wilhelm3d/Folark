package checks

import java.io.File

/**
 * Builds SettingsSearchIndex.kt from the settings screens' own code (formerly
 * tools/gen_settings_index.py, same rules): each `SettingsCategory.X -> SomeSection(` in
 * SettingsScreen.kt is a category's page; its rows are the `title = stringResource(R.string.…)`
 * (with the subtitle or description after it) and the `Text(stringResource(R.string.settings_…))`
 * headings in that page's function and in every settings function it calls, however deep.
 * Pure Kotlin, so it can be tested without Gradle.
 */
object SettingsIndexGenerator {
    const val INDEX_FILE = "SettingsSearchIndex.kt"

    private val DECL = Regex(
        """^(?:@Composable\s*\n)?(?:(?:internal|private|public)\s+)?fun\s+(?:[A-Za-z_.<>]+\.)?([A-Za-z_]\w*)\s*\(""",
        RegexOption.MULTILINE,
    )
    private val TITLE = Regex("""\btitle\s*=\s*stringResource\(R\.string\.(\w+)\)""")
    private val SUB = Regex("""\b(?:subtitle|description)\s*=\s*stringResource\(R\.string\.(\w+)\)""")
    private val HEADING = Regex("""\bText\(\s*(?:text\s*=\s*)?stringResource\(R\.string\.(settings_\w+)\)""")
    private val CALL = Regex("""\b([A-Z][A-Za-z0-9_]*)\s*\(""")
    private val ROOTS = Regex("""SettingsCategory\.([A-Z_]+)\s*->\s*([A-Z]\w*)\(""")

    /** The index file's text, from [sources]: file name → content of each .kt file of the settings package. */
    fun generate(sources: Map<String, String>): String {
        val functions = LinkedHashMap<String, String>()
        val roots = LinkedHashMap<String, MutableList<String>>()
        for ((name, src) in sources.entries.sortedBy { it.key }) {
            if (!name.endsWith(".kt") || name == INDEX_FILE) continue
            if (name == "SettingsScreen.kt") {
                for (m in ROOTS.findAll(src)) {
                    val list = roots.getOrPut(m.groupValues[1]) { mutableListOf() }
                    if (m.groupValues[2] !in list) list += m.groupValues[2]
                }
            }
            val starts = DECL.findAll(src).map { it.range.first to it.groupValues[1] }.toList()
            starts.forEachIndexed { i, (at, fn) ->
                val end = if (i + 1 < starts.size) starts[i + 1].first else src.length
                // The whole screen: its dialogs would pull everything into every category.
                if (fn != "SettingsScreen") functions[fn] = functions.getOrElse(fn) { "" } + src.substring(at, end)
            }
        }
        val index = mutableListOf<Triple<String, String, String?>>()
        val seen = HashSet<Pair<String, String>>()
        for ((cat, fns) in roots) {
            // The Start page repeats switches from other pages: search finds them on their own pages.
            if (cat == "START") continue
            val todo = ArrayDeque(fns)
            val visited = HashSet<String>()
            while (todo.isNotEmpty()) {
                val f = todo.removeFirst()
                if (f in visited) continue
                val body = functions[f] ?: continue
                visited += f
                for ((title, sub) in rows(body)) {
                    if (seen.add(cat to title)) index += Triple(cat, title, sub)
                }
                for (m in CALL.findAll(body)) {
                    val callee = m.groupValues[1]
                    if (callee in functions && callee !in visited) todo += callee
                }
            }
        }
        return buildString {
            append("package org.arkikeskus.launcher.feature.settings\n\n")
            append("// Generated from the settings screens by the build (generateSettingsIndex, SettingsIndexGenerator):\n")
            append("// do not edit by hand. Written again before every build of :feature:settings.\n\n")
            append("/** One setting row for the search in Settings: its title, its description (0 = none), its page. */\n")
            append("internal class SettingsSearchEntry(val title: Int, val subtitle: Int, val category: SettingsCategory)\n\n")
            append("internal val SETTINGS_SEARCH_INDEX: List<SettingsSearchEntry> = listOf(\n")
            for ((cat, title, sub) in index) {
                append("    SettingsSearchEntry(R.string.$title, ${if (sub != null) "R.string.$sub" else "0"}, SettingsCategory.$cat),\n")
            }
            append(")\n")
        }
    }

    /** How many categories [sources] has pages for (for the task's log line). */
    fun categoryCount(sources: Map<String, String>): Int =
        sources["SettingsScreen.kt"]?.let { src -> ROOTS.findAll(src).map { it.groupValues[1] }.toSet().size } ?: 0

    /** (title, subtitle or null) in the order they appear in [body]. */
    private fun rows(body: String): List<Pair<String, String?>> {
        val events = (
            TITLE.findAll(body).map { Triple(it.range.first, 't', it.groupValues[1]) } +
                SUB.findAll(body).map { Triple(it.range.first, 's', it.groupValues[1]) } +
                HEADING.findAll(body).map { Triple(it.range.first, 'h', it.groupValues[1]) }
            ).sortedWith(compareBy({ it.first }, { it.second }, { it.third }))
        val found = mutableListOf<Pair<String, String?>>()
        for ((_, kind, res) in events) {
            if (kind == 't' || kind == 'h') found += res to null
            else if (found.isNotEmpty() && found.last().second == null) found[found.lastIndex] = found.last().first to res
        }
        return found
    }

    /** The index for the settings package folder [dir]. */
    fun generate(dir: File): String =
        generate(dir.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".kt") }.associate { it.name to it.readText() })
}
