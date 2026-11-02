package org.arkikeskus.launcher.ui.component

/**
 * Returns true if [name] is considered an unassigned/default folder name (blank, "Folder", "Kansio",
 * or matching [defaultName]).
 */
fun isDefaultOrBlankFolderName(name: String, defaultName: String? = null): Boolean {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return true
    if (trimmed.equals("Folder", ignoreCase = true)) return true
    if (trimmed.equals("Kansio", ignoreCase = true)) return true
    if (defaultName != null && trimmed.equals(defaultName.trim(), ignoreCase = true)) return true
    return false
}
