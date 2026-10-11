package org.arkikeskus.launcher.model

/**
 * One match from a search source. Kept free of Android `Intent` plumbing so it stays comparable and
 * JVM-testable; the UI builds the actual Intent from [Setting.action] / [Contact.lookupUri] / number.
 */
sealed interface SearchResult {
    val id: String

    data class App(val app: AppItem) : SearchResult {
        override val id: String get() = "app:${app.key}"
    }

    /** A system settings page. [action] is a `Settings.ACTION_*` (or equivalent) intent action. */
    data class Setting(val title: String, val action: String, val key: String) : SearchResult {
        override val id: String get() = "setting:$key"
    }

    data class Contact(
        val name: String,
        val lookupUri: String,
        val number: String?,
        val photoUri: String?,
        val contactId: Long,
    ) : SearchResult {
        override val id: String get() = "contact:$contactId"
    }

    /** An app shortcut ("New tab", "Compose") published by [packageName]; started through LauncherApps. */
    data class Shortcut(
        val packageName: String,
        val shortcutId: String,
        val userSerial: Long,
        val label: String,
        val appLabel: String,
    ) : SearchResult {
        override val id: String get() = "shortcut:$packageName/$shortcutId/$userSerial"
    }

    data class Calculation(val expression: String, val result: String) : SearchResult {
        override val id: String get() = "calc"
    }
}

/** Search results grouped into the sections the drawer renders (top→bottom: calc, apps, settings, contacts). */
data class SearchResults(
    val calc: SearchResult.Calculation? = null,
    val apps: List<AppItem> = emptyList(),
    val settings: List<SearchResult.Setting> = emptyList(),
    val contacts: List<SearchResult.Contact> = emptyList(),
    val shortcuts: List<SearchResult.Shortcut> = emptyList(),
)
