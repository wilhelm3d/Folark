package org.arkikeskus.launcher.data.root

/**
 * The checks a value goes through before it is written into a root command. Folark builds its
 * commands only from numbers, its own fixed text and names Android gave it, but every name and path
 * is checked anyway, here in one place, so no command can ever carry a quote or a shell character.
 */
object ShellSafe {
    private val NAME = Regex("^[A-Za-z0-9_.]+$")
    private val COMPONENT = Regex("^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$")

    /** A package or permission name: letters, digits, dots and underscores only. */
    fun isName(text: String): Boolean = NAME.matches(text)

    /** A component name ("package/class"), as Android writes it into its settings. */
    fun isComponent(text: String): Boolean = COMPONENT.matches(text)

    /**
     * A path that may stand between single quotes in a command: no quote, backtick or dollar sign,
     * and no space either unless [allowSpaces] (for a path that is also used unquoted).
     */
    fun isQuotable(path: String, allowSpaces: Boolean = false): Boolean =
        path.none { it == '\'' || it == '"' || it == '`' || it == '$' || (!allowSpaces && it == ' ') }
}
