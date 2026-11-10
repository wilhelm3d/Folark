package checks

import java.io.File

/**
 * The string-resource mistakes aapt2 fails the build on, found before it gets there (formerly
 * tools/check_strings.py): an apostrophe that isn't escaped, two or more non-positional
 * substitutions ("%" before a letter or a space counts) without formatted="false", and a bare "&".
 * Pure Kotlin, so it can be tested without Gradle.
 */
object StringChecks {
    private val STRING = Regex("""<string name="([^"]+)"([^>]*)>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    private val ITEM = Regex("""<item\b([^>]*)>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
    private val PLURALS = Regex("""<plurals name="([^"]+)"[^>]*>(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
    private val FORMAT = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[a-zA-Z%]""")
    private val APOSTROPHE = Regex("""(?<!\\)'""")
    private val AMPERSAND = Regex("""&(?!amp;|lt;|gt;|quot;|apos;|#)""")

    /** Problems in [text] (the content of the resource file [path]), one line each. */
    fun check(path: String, text: String): List<String> {
        val problems = mutableListOf<String>()
        fun value(name: String, attrs: String, value: String) {
            if (value.startsWith("\"") && value.endsWith("\"")) return
            if (APOSTROPHE.containsMatchIn(value)) problems += "$path: $name: apostrophe not escaped (write \\')"
            if (!attrs.contains("formatted=\"false\"")) {
                val plain = FORMAT.findAll(value).count { it.value != "%%" && it.groupValues[1].isEmpty() }
                if (plain > 1) problems += "$path: $name: $plain non-positional \"%\" (use %1\$s… or formatted=\"false\")"
            }
            if (AMPERSAND.containsMatchIn(value)) problems += "$path: $name: bare & (write &amp;)"
        }
        for (m in STRING.findAll(text)) value(m.groupValues[1], m.groupValues[2], m.groupValues[3])
        for (p in PLURALS.findAll(text)) {
            for (item in ITEM.findAll(p.groupValues[2])) value(p.groupValues[1], item.groupValues[1], item.groupValues[2])
        }
        return problems
    }

    /** Problems in every file of [files] (paths shown relative to [root]). */
    fun check(files: Iterable<File>, root: File): List<String> =
        files.filter { it.isFile }.sortedBy { it.path }.flatMap { check(it.relativeTo(root).path, it.readText()) }
}
