package org.arkikeskus.launcher.data

import org.arkikeskus.launcher.model.AppearanceSettings
import org.arkikeskus.launcher.model.DockSettings
import org.arkikeskus.launcher.model.DotSettings
import org.arkikeskus.launcher.model.DrawerSettings
import org.arkikeskus.launcher.model.FoldSettings
import org.arkikeskus.launcher.model.FolderSettings
import org.arkikeskus.launcher.model.HomeSettings
import org.arkikeskus.launcher.model.IconSettings
import org.arkikeskus.launcher.model.LaunchSettings
import org.arkikeskus.launcher.model.LiveSettings
import org.arkikeskus.launcher.model.MotionSettings
import org.arkikeskus.launcher.model.RecentsSettings
import org.arkikeskus.launcher.model.RootSettings
import org.arkikeskus.launcher.model.SearchSettings
import org.arkikeskus.launcher.model.StatusBarSettings
import org.arkikeskus.launcher.model.TaskbarSettings
import org.arkikeskus.launcher.model.WidgetSettings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.arkikeskus.launcher.model.AppPair
import org.arkikeskus.launcher.model.DotColor
import org.arkikeskus.launcher.model.DrawerTheme
import org.arkikeskus.launcher.model.DropZones
import org.arkikeskus.launcher.model.FolderTheme
import org.arkikeskus.launcher.model.HomeGesture
import org.arkikeskus.launcher.model.IconShape
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.PopupTheme
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.model.SplitPickerLook
import org.arkikeskus.launcher.model.StackIndicator
import org.arkikeskus.launcher.model.ThemePreset
import org.arkikeskus.launcher.model.storedChoice
import org.arkikeskus.launcher.model.withLiquidGlass
import javax.inject.Inject
import java.util.concurrent.atomic.AtomicReferenceArray
import javax.inject.Singleton

/** A person the user pinned to the people widget: a quiet tile that stays put between messages.
 *  [key] is [PeopleGrouping.personKey] of [name]; the contact fields are what a lookup found when
 *  the pin was made (blank when the person isn't in the contacts, or contacts aren't allowed). */
data class PinnedPerson(
    val key: String,
    val name: String,
    val lookupUri: String = "",
    val number: String = "",
    val photoUri: String = "",
)

/** A folder shown in the app drawer: a stable [id], a [name], and the keys of its member apps. */
data class DrawerFolder(val id: Long, val name: String, val appKeys: List<String>)

/**
 * Reads/writes launcher preferences. The [DataStore] is injected (provided from a Context-backed
 * `preferencesDataStore` in DataModule) so the repository's merge logic can be unit-tested on the
 * JVM with a temp-file store.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val settings: Flow<LauncherSettings> get() = settings(ScreenType.OUTER)

    /** The one-off store repair below has run in this process (it used to run on every collection). */
    @Volatile
    private var storeRepaired = false
    private val repairLock = Mutex()

    // No default screen: on a foldable, the wrong profile is an easy, silent mistake.
    fun settings(screenType: ScreenType): Flow<LauncherSettings> = dataStore.data.onStart {
        // 0.7 imports unknown numeric backup keys as Long. Repair them before the first typed
        // read when a newer backup was restored in 0.7 and the installation then upgraded.
        if (!storeRepaired) repairLock.withLock {
            if (!storeRepaired) {
                repairStore()
                storeRepaired = true
            }
        }
    }.map { p -> settingsFor(p, screenType) }
        // Unrelated writes to the store (app-usage counts, held notifications) must not make every
        // collector rebuild its state.
        .distinctUntilChanged()

    /** The settings last built per screen, with the store snapshot they came from. */
    private class SettingsMemo(val prefs: Preferences, val settings: LauncherSettings)
    private val settingsMemo = AtomicReferenceArray<SettingsMemo?>(ScreenType.entries.size)

    /**
     * Every collector of [settings] gets the same store snapshot after a write; the settings are
     * built from it once and shared (building reads a few hundred keys).
     */
    private fun settingsFor(p: Preferences, screenType: ScreenType): LauncherSettings {
        val hit = settingsMemo.get(screenType.ordinal)
        if (hit != null && hit.prefs === p) return hit.settings
        return readSettings(p, screenType).withLiquidGlass().also { settingsMemo.set(screenType.ordinal, SettingsMemo(p, it)) }
    }

    /** One stored value as a flow: emits only when that value changes, then parses it. */
    internal fun <K, T> keyFlow(key: Preferences.Key<K>, parse: (K?) -> T): Flow<T> =
        dataStore.data.map { it[key] }.distinctUntilChanged().map { parse(it) }

    private suspend fun repairStore() {
        dataStore.edit { p ->
            for (key in listOf(Keys.homePage(ScreenType.OUTER), Keys.homePageCount(ScreenType.OUTER), Keys.homePage(ScreenType.INNER), Keys.homePageCount(ScreenType.INNER))) {
                val raw = p.asMap()[key] ?: continue
                if (raw !is Int) {
                    p[key] = (raw as? Number)?.toLong()
                        ?.coerceIn(0L, HomeLayoutRepository.MAX_PAGES.toLong())?.toInt() ?: 0
                }
            }
            splitProfilesOnce(p)
        }
    }

    val appPairs: Flow<List<AppPair>> = keyFlow(Keys.APP_PAIRS) { parseAppPairs(it) }

    // --- App-pair schedules (PairSchedules.kt) ----------------------------------------------------
    // One dynamic DataStore key per pair id ("pairsched:<id>"), so the map is read with a prefix
    // filter rather than a fixed key.

    /** Every stored pair schedule, pair id to its raw "HH:MM|days" value. */
    val pairSchedules: Flow<Map<String, String>> = dataStore.data.map { prefs ->
        prefs.asMap().entries
            .filter { it.key.name.startsWith(PAIR_SCHEDULE_PREFIX) }
            .associate { it.key.name.removePrefix(PAIR_SCHEDULE_PREFIX) to (it.value as? String).orEmpty() }
    }.distinctUntilChanged() // the store emits on every write; only a schedule change matters here

    /** Pairs that have a schedule set, paired with their parsed schedule. */
    val scheduledPairs: Flow<List<ScheduledPair>> = combine(appPairs, pairSchedules) { pairs, raw ->
        pairs.map { ScheduledPair(it, PairSchedule.parse(raw[it.id])) }
            .filter { it.schedule != null }
    }.distinctUntilChanged() // the alarm is re-laid only when a pair or its schedule changed

    /** Reads one pair's schedule (null when none). */
    suspend fun pairSchedule(pairId: String): PairSchedule? =
        PairSchedule.parse(dataStore.data.first()[stringPreferencesKey(PAIR_SCHEDULE_PREFIX + pairId)])

    /** Sets (or, with a null [schedule], clears) a pair's schedule. */
    suspend fun setPairSchedule(pairId: String, schedule: PairSchedule?) = edit { p ->
        val key = stringPreferencesKey(PAIR_SCHEDULE_PREFIX + pairId)
        if (schedule == null) p.remove(key) else p[key] = schedule.serialize()
    }

    suspend fun saveAppPair(pair: AppPair) = edit { p ->
        val current = parseAppPairs(p[Keys.APP_PAIRS]).toMutableList()
        val index = current.indexOfFirst { it.id == pair.id }
        if (index >= 0) current[index] = pair else current.add(pair)
        p[Keys.APP_PAIRS] = serializeAppPairs(current)
    }

    suspend fun deleteAppPair(id: String) = edit { p ->
        val current = parseAppPairs(p[Keys.APP_PAIRS])
        p[Keys.APP_PAIRS] = serializeAppPairs(current.filterNot { it.id == id })
        // A deleted pair leaves the taskbar too, and takes its schedule with it.
        p[Keys.TASKBAR_PAIRS]?.let { raw ->
            p[Keys.TASKBAR_PAIRS] = raw.split("\n").filter { it.isNotEmpty() && it != id }.joinToString("\n")
        }
        p.remove(stringPreferencesKey(PAIR_SCHEDULE_PREFIX + id))
    }

    private fun parseAppPairs(raw: String?): List<AppPair> =
        raw?.split("\n")?.filter { it.isNotEmpty() }?.mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 10) return@mapNotNull null
            AppPair(
                id = parts[0],
                name = parts[1],
                app1Key = parts[2],
                app1Package = parts[3],
                app1ClassName = parts[4],
                app1UserSerial = parts[5].toLongOrNull() ?: 0L,
                app2Key = parts[6],
                app2Package = parts[7],
                app2ClassName = parts[8],
                app2UserSerial = parts[9].toLongOrNull() ?: 0L,
                // Optional third (floating) app — absent in pairs saved by older versions.
                app3Key = parts.getOrNull(10).orEmpty(),
                app3Package = parts.getOrNull(11).orEmpty(),
                app3ClassName = parts.getOrNull(12).orEmpty(),
                app3UserSerial = parts.getOrNull(13)?.toLongOrNull() ?: 0L,
                peek = parts.getOrNull(14) == "1",
                // Scenes (Settings ▸ Scenes): absent in pairs saved by older versions.
                layout = parts.getOrNull(15)?.takeIf { it in AppPair.LAYOUTS }
                    ?: if (parts.getOrNull(14) == "1") AppPair.LAYOUT_PEEK else AppPair.LAYOUT_EVEN,
                thirdMode = parts.getOrNull(16)?.takeIf { it in AppPair.THIRD_MODES } ?: AppPair.THIRD_AUTO,
                coverMode = parts.getOrNull(17)?.takeIf { it in AppPair.COVER_MODES } ?: AppPair.COVER_SPLIT,
                sizes = parts.getOrNull(18).orEmpty(),
            )
        } ?: emptyList()

    private fun serializeAppPairs(pairs: List<AppPair>): String =
        pairs.joinToString("\n") { p ->
            listOf(
                p.id,
                p.name.replace(SEPARATORS, " "),
                p.app1Key,
                p.app1Package,
                p.app1ClassName,
                p.app1UserSerial.toString(),
                p.app2Key,
                p.app2Package,
                p.app2ClassName,
                p.app2UserSerial.toString(),
                p.app3Key,
                p.app3Package,
                p.app3ClassName,
                p.app3UserSerial.toString(),
                if (p.effectiveLayout == AppPair.LAYOUT_PEEK) "1" else "0",
                p.effectiveLayout,
                p.thirdMode,
                p.coverMode,
                p.sizes,
            ).joinToString("\t")
        }

    /** The place typed in for the weather ("name<TAB>latitude<TAB>longitude"); "" = use the device's location. */
    val weatherLocation: Flow<String> = keyFlow(Keys.WEATHER_LOCATION) { it ?: "" }

    /**
     * Settings ▸ App drawer ▸ Keep copied items: how long a copied item stays in the clipboard history,
     * in minutes. 0 keeps it until newer ones push it out (the 30 newest are kept).
     */
    val clipboardKeepMinutes: Flow<Int> = keyFlow(Keys.CLIPBOARD_KEEP_MINUTES) { (it ?: 0).coerceAtLeast(0) }

    suspend fun setClipboardKeepMinutes(value: Int) = edit { it[Keys.CLIPBOARD_KEEP_MINUTES] = value.coerceAtLeast(0) }
    suspend fun setWeatherLocation(value: String) = edit { it[Keys.WEATHER_LOCATION] = value }

    /**
     * Apps installed lately and not opened yet (app keys): the drawer marks them "new". Stored as
     * "key<TAB>installed-at"; a mark older than [NEW_APP_DAYS] days fades on its own.
     */
    val newApps: Flow<Set<String>> = keyFlow(Keys.NEW_APPS) { raw ->
        val cutoff = System.currentTimeMillis() - NEW_APP_DAYS * 86_400_000L
        parseNewApps(raw).filterValues { it >= cutoff }.keys
    }

    /** Marks [keys] as new (just installed). */
    suspend fun markNewApps(keys: Collection<String>) = edit { p ->
        if (keys.isEmpty()) return@edit
        val now = System.currentTimeMillis()
        val cutoff = now - NEW_APP_DAYS * 86_400_000L
        val map = parseNewApps(p[Keys.NEW_APPS]).filterValues { it >= cutoff }.toMutableMap()
        keys.forEach { map[it] = now }
        p[Keys.NEW_APPS] = map.entries.joinToString("\n") { (k, t) -> "$k\t$t" }
    }

    /** [key] was opened: no longer new. */
    suspend fun clearNewApp(key: String) = edit { p ->
        val map = parseNewApps(p[Keys.NEW_APPS])
        if (key !in map) return@edit
        p[Keys.NEW_APPS] = (map - key).entries.joinToString("\n") { (k, t) -> "$k\t$t" }
    }

    private fun parseNewApps(raw: String?): Map<String, Long> =
        raw?.split("\n")?.mapNotNull { line ->
            val i = line.lastIndexOf('\t')
            if (i <= 0) null else line.substring(0, i) to (line.substring(i + 1).toLongOrNull() ?: return@mapNotNull null)
        }?.toMap() ?: emptyMap()

    suspend fun setIconShape(value: IconShape) = edit { it[Keys.ICON_SHAPE] = value.stored }

    /** Packages whose battery-optimisation exemption Folark added for a pin (and may take back). */
    suspend fun pinExemptions(): Set<String> = lines(dataStore.data.first()[Keys.RECENTS_PIN_EXEMPT])
    suspend fun setPinExemptions(value: Set<String>) = edit { it[Keys.RECENTS_PIN_EXEMPT] = value.joinToString("\n") }
    /** Packages the user muted from Recents; persistent, enforced via appops PLAY_AUDIO on root. */
    val mutedApps: Flow<Set<String>> = keyFlow(Keys.RECENTS_MUTED) { lines(it) }
    suspend fun mutedPackages(): Set<String> = lines(dataStore.data.first()[Keys.RECENTS_MUTED])
    suspend fun setMutedPackages(value: Set<String>) = edit { it[Keys.RECENTS_MUTED] = value.joinToString("\n") }

    /** Adds [appKey] to the split picker's favorites, or takes it out when it is one already. */
    suspend fun toggleSplitPickerFavorite(appKey: String) = edit {
        val now = orderedLines(it[Keys.SPLIT_PICKER_FAVORITE_KEYS])
        it[Keys.SPLIT_PICKER_FAVORITE_KEYS] = (if (appKey in now) now - appKey else now + appKey).joinToString("\n")
    }

    /** Per-widget look, home row id → "cornerRadius,padding,opacity" (see the home screen's WidgetLook). */
    val widgetLooks: Flow<Map<String, String>> = keyFlow(Keys.WIDGET_LOOKS) { parseLabels(it) }

    /** Sets (or, for a null/blank [look], clears) the look of the widget in home row [rowId]. */
    suspend fun setWidgetLook(rowId: Long, look: String?) = edit { p ->
        val current = parseLabels(p[Keys.WIDGET_LOOKS]).toMutableMap()
        if (look.isNullOrBlank()) current.remove(rowId.toString()) else current[rowId.toString()] = look
        p[Keys.WIDGET_LOOKS] = current.entries.joinToString("\n") { "${it.key}\t${it.value}" }
    }

    /**
     * Applies a launcher-wide theme preset to [screenType]: one tap sets the drawer, folders,
     * long-press menus, notification dots, widget-stack indicator and dock together. Every value
     * stays an ordinary setting afterwards and can be changed on its own.
     */
    suspend fun applyThemePreset(preset: ThemePreset, screenType: ScreenType) = edit { p ->
        fun put(
            drawer: DrawerTheme, glass: Boolean, folder: FolderTheme, popup: PopupTheme, dot: DotColor,
            indicator: StackIndicator, dock: Float, amoled: Boolean,
        ) {
            p[Keys.drawerThemeTint(screenType)] = drawer.stored
            p[Keys.drawerGlassEnabled(screenType)] = glass
            p[Keys.folderTheme(screenType)] = folder.stored
            p[Keys.popupTheme(screenType)] = popup.stored
            p[Keys.dotColor(screenType)] = dot.stored
            p[Keys.stackIndicator(screenType)] = indicator.stored
            p[Keys.dockBackgroundOpacity(screenType)] = dock
            p[Keys.amoledDark(screenType)] = amoled
        }
        when (preset) {
            ThemePreset.GLASS -> put(
                DrawerTheme.SYSTEM, true, FolderTheme.GLASS,
                PopupTheme.MATERIAL, DotColor.ACCENT,
                StackIndicator.DOTS, 0.35f, false,
            )
            ThemePreset.DARK -> put(
                DrawerTheme.OBSIDIAN_BLACK, false, FolderTheme.DARK,
                PopupTheme.DARK, DotColor.RED,
                StackIndicator.DOTS, 0.6f, true,
            )
            ThemePreset.LIGHT -> put(
                DrawerTheme.LIGHT, false, FolderTheme.LIGHT,
                PopupTheme.LIGHT, DotColor.RED,
                StackIndicator.DOTS, 0.5f, false,
            )
            ThemePreset.MONO -> put(
                DrawerTheme.MONOCHROME, false, FolderTheme.DARK,
                PopupTheme.DARK, DotColor.WHITE,
                StackIndicator.BAR, 0.4f, false,
            )
        }
    }

    /** Hand-picked icons, app key → "packPackage|drawableName" (both displays). */
    val customIcons: Flow<Map<String, String>> = keyFlow(Keys.CUSTOM_ICONS) { parseLabels(it) }

    /** Sets (or, for a null/blank [icon], clears) the hand-picked icon of the app [key]. */
    suspend fun setCustomIcon(key: String, icon: String?) = edit { p ->
        val current = parseLabels(p[Keys.CUSTOM_ICONS]).toMutableMap()
        if (icon.isNullOrBlank()) current.remove(key) else current[key] = icon
        p[Keys.CUSTOM_ICONS] = current.entries.joinToString("\n") { "${it.key}\t${it.value}" }
    }

    /** Per-app icon shapes, app key → shape (both displays); the rest follow the global shape. */
    val iconShapes: Flow<Map<String, IconShape>> = keyFlow(Keys.ICON_SHAPES) { raw ->
        parseLabels(raw).mapValues { (_, shape) -> storedChoice(shape, IconShape.SYSTEM) }
    }

    /** Gives the app [key] its own icon [shape], or (null) returns it to the global one. */
    suspend fun setIconShape(key: String, shape: IconShape?) = edit { p ->
        val current = parseLabels(p[Keys.ICON_SHAPES]).toMutableMap()
        if (shape == null) current.remove(key) else current[key] = shape.stored
        p[Keys.ICON_SHAPES] = current.entries.joinToString("\n") { "${it.key}\t${it.value}" }
    }

    // --- People widget: local batch deadlines (also reads legacy Android snoozes) -------------
    // "key\tdeliverAtEpochMs" per line. Device-local: a notification key means nothing elsewhere.

    /** Notification key → local delivery time. Notification text is never persisted. */
    val heldNotifications: Flow<Map<String, Long>> = keyFlow(Keys.PEOPLE_HELD) { raw ->
        raw?.split("\n")?.filter { it.isNotEmpty() }?.mapNotNull { line ->
            val i = line.indexOf('\t')
            val until = if (i > 0) line.substring(i + 1).toLongOrNull() else null
            if (until == null) null else line.substring(0, i) to until
        }?.toMap() ?: emptyMap()
    }

    suspend fun setHeldNotifications(held: Map<String, Long>) = edit { p ->
        p[Keys.PEOPLE_HELD] = held.entries.joinToString("\n") { (k, v) -> k.replace(SEPARATORS, " ") + "\t" + v }
    }

    // --- People widget aliases ("this notification name is the same person") --------------------
    // One link per line: "aliasKey\ttargetKey" (both PeopleGrouping.personKey values).

    /** alias key → target key. Single-hop: a target is never itself an alias (see [linkPerson]). */
    val peopleAliases: Flow<Map<String, String>> = keyFlow(Keys.PEOPLE_ALIASES) { parseAliases(it) }

    /** Links [alias] to [target]: an existing link on [alias] is replaced, links that pointed at
     *  [alias] follow it to the new target, and a target that is itself an alias resolves first. */
    suspend fun linkPerson(alias: String, target: String) = edit { p ->
        val current = parseAliases(p[Keys.PEOPLE_ALIASES])
        val resolved = current[target] ?: target
        if (resolved == alias) return@edit
        val next = current
            .mapValues { (_, t) -> if (t == alias) resolved else t }
            .filterKeys { it != alias && it != resolved } + (alias to resolved)
        p[Keys.PEOPLE_ALIASES] = serializeAliases(next.filter { (a, t) -> a != t })
    }

    /** Removes every link that merges into [target]. */
    suspend fun unlinkPerson(target: String) = edit { p ->
        p[Keys.PEOPLE_ALIASES] = serializeAliases(parseAliases(p[Keys.PEOPLE_ALIASES]).filterValues { it != target })
    }

    private fun parseAliases(raw: String?): Map<String, String> =
        raw?.split("\n")?.filter { it.isNotEmpty() }?.mapNotNull { line ->
            val i = line.indexOf('\t')
            if (i <= 0 || i == line.length - 1) null else line.substring(0, i) to line.substring(i + 1)
        }?.toMap() ?: emptyMap()

    private fun serializeAliases(aliases: Map<String, String>): String =
        aliases.entries.joinToString("\n") { (a, t) -> a.replace(SEPARATORS, " ") + "\t" + t.replace(SEPARATORS, " ") }

    // --- People widget pins ---------------------------------------------------------------------
    // One person per line, tab-separated: "key\tname\tlookupUri\tnumber\tphotoUri". Names are
    // normalized at the write point like folder names, so a plain split round-trips.

    /** People pinned to the people widget, in pin order. */
    val pinnedPeople: Flow<List<PinnedPerson>> = keyFlow(Keys.PINNED_PEOPLE) { parsePinned(it) }

    /** Pins [person] (replacing an earlier pin with the same key, keeping its position). */
    suspend fun pinPerson(person: PinnedPerson) = edit { p ->
        val current = parsePinned(p[Keys.PINNED_PEOPLE])
        val next = if (current.any { it.key == person.key }) current.map { if (it.key == person.key) person else it }
            else current + person
        p[Keys.PINNED_PEOPLE] = serializePinned(next)
    }

    suspend fun unpinPerson(key: String) = edit { p ->
        p[Keys.PINNED_PEOPLE] = serializePinned(parsePinned(p[Keys.PINNED_PEOPLE]).filterNot { it.key == key })
    }

    private fun parsePinned(raw: String?): List<PinnedPerson> =
        raw?.split("\n")?.filter { it.isNotEmpty() }?.mapNotNull { line ->
            val parts = line.split('\t')
            val key = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            PinnedPerson(
                key = key,
                name = parts.getOrNull(1).orEmpty().ifBlank { key },
                lookupUri = parts.getOrNull(2).orEmpty(),
                number = parts.getOrNull(3).orEmpty(),
                photoUri = parts.getOrNull(4).orEmpty(),
            )
        } ?: emptyList()

    private fun serializePinned(people: List<PinnedPerson>): String =
        people.joinToString("\n") { person ->
            listOf(person.key, person.name, person.lookupUri, person.number, person.photoUri)
                .joinToString("\t") { it.replace(SEPARATORS, " ") }
        }

    /** Ordered list of dock favorite app keys (see AppItem.key). */
    val dockFavorites: Flow<List<String>> get() = dockFavorites(ScreenType.OUTER)

    /** Ordered list of dock favorite app keys (see AppItem.key). */
    fun dockFavorites(screenType: ScreenType = ScreenType.OUTER): Flow<List<String>> = keyFlow(Keys.dockFavorites(screenType)) { raw ->
        raw?.split("\n")?.filter { it.isNotEmpty() } ?: emptyList()
    }

    /** App keys hidden from the app drawer (see AppItem.key). */
    val hiddenApps: Flow<Set<String>> = keyFlow(Keys.HIDDEN_APPS) { raw ->
        raw?.split("\n")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
    }

    suspend fun setAppHidden(key: String, hidden: Boolean) = edit { p ->
        val current = (p[Keys.HIDDEN_APPS]?.split("\n")?.filter { it.isNotEmpty() } ?: emptyList()).toMutableSet()
        if (hidden) current.add(key) else current.remove(key)
        p[Keys.HIDDEN_APPS] = current.joinToString("\n")
    }

    /** User-chosen custom app labels (AppItem.key / componentName -> label), applied over the system label. */
    val customLabels: Flow<Map<String, String>> = keyFlow(Keys.CUSTOM_LABELS) { parseLabels(it) }

    /** Sets (or, for a blank [label], clears) the custom label for an app by its [componentName] or key. */
    suspend fun setCustomAppLabel(componentName: String, label: String) = edit { p ->
        val current = parseLabels(p[Keys.CUSTOM_LABELS]).toMutableMap()
        val trimmed = label.trim()
        if (trimmed.isEmpty()) current.remove(componentName) else current[componentName] = trimmed
        p[Keys.CUSTOM_LABELS] = current.entries.joinToString("\n") { "${it.key}\t${it.value}" }
    }

    /** Sets (or, for a blank [label], clears) the custom label for [key]. */
    suspend fun setCustomLabel(key: String, label: String?) = setCustomAppLabel(key, label.orEmpty())

    private fun parseLabels(raw: String?): Map<String, String> =
        raw?.split("\n")?.filter { it.isNotEmpty() }?.mapNotNull { line ->
            val i = line.indexOf('\t')
            if (i <= 0) null else line.substring(0, i) to line.substring(i + 1)
        }?.toMap() ?: emptyMap()

    // --- Hidden notification channels -------------------------------------------------------------
    // Per app (package name): the Android notification channels whose notifications Folark's own
    // surfaces (dots, the status icons, the notification widgets) never show. One "pkg\tchannel"
    // pair per line, same shape as the custom labels. Android's shade is untouched.

    /** Hidden notification channels, package name to its channel ids. */
    val hiddenChannels: Flow<Map<String, Set<String>>> = keyFlow(Keys.HIDDEN_CHANNELS) { raw ->
        parseHiddenChannelList(raw).groupBy({ it.packageName }, { it.channelId }).mapValues { it.value.toSet() }
    }

    /** The muted channels one by one, with the names they had when muted (Settings ▸ Notifications). */
    val hiddenChannelList: Flow<List<HiddenChannel>> = keyFlow(Keys.HIDDEN_CHANNELS) { parseHiddenChannelList(it) }

    /**
     * Hides (or, with [hidden] false, unhides) one of an app's notification channels. [name] is the
     * channel's own name, kept so Settings can say what was muted.
     */
    suspend fun setChannelHidden(packageName: String, channelId: String, hidden: Boolean = true, name: String? = null) = edit { p ->
        val others = parseHiddenChannelList(p[Keys.HIDDEN_CHANNELS])
            .filterNot { it.packageName == packageName && it.channelId == channelId }
        val next = if (hidden) others + HiddenChannel(packageName, channelId, name) else others
        p[Keys.HIDDEN_CHANNELS] = next.joinToString("\n") { h ->
            // "package<TAB>channel[<TAB>name]": lines from before names were kept have two fields.
            val clean = h.name?.replace('\t', ' ')?.replace('\n', ' ')?.takeIf { it.isNotBlank() }
            if (clean == null) "${h.packageName}\t${h.channelId}" else "${h.packageName}\t${h.channelId}\t$clean"
        }
    }

    /** Unmutes every channel (Settings ▸ Notifications ▸ Muted channels ▸ Unmute all). */
    suspend fun clearHiddenChannels() = edit { it.remove(Keys.HIDDEN_CHANNELS) }

    private fun parseHiddenChannelList(raw: String?): List<HiddenChannel> =
        raw?.split("\n")?.filter { it.isNotEmpty() }?.mapNotNull { line ->
            val parts = line.split('\t')
            val pkg = parts.getOrNull(0).orEmpty()
            val id = parts.getOrNull(1).orEmpty()
            if (pkg.isEmpty() || id.isEmpty()) null else HiddenChannel(pkg, id, parts.getOrNull(2)?.takeIf { it.isNotBlank() })
        }?.distinctBy { it.packageName to it.channelId } ?: emptyList()

    /** Colours are stored opaque: a pasted hex with zero alpha would make the accent/background invisible. */
    suspend fun setDrawerAccentColor(argb: Int, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerAccentColor(screenType)] = argb or OPAQUE }
    suspend fun setDrawerCustomColor(argb: Int, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.drawerCustomColor(screenType)] = argb or OPAQUE }

    /** Copies every per-display setting of [from] onto [to] in one write — the explicit way to make
     *  both displays match, now that the profiles never inherit from each other. Layout data (dock
     *  favourites, home pages) is deliberately not copied: icons live per display. */
    @Suppress("UNCHECKED_CAST")
    suspend fun copyProfile(from: ScreenType, to: ScreenType) = edit { p ->
        if (from == to) return@edit
        val snapshot = p.asMap()
        for (name in PROFILE_KEY_NAMES) {
            val sourceName = profileKeyName(name, from)
            val targetName = profileKeyName(name, to)
            when (val value = snapshot.entries.firstOrNull { it.key.name == sourceName }?.value) {
                is Boolean -> p[booleanPreferencesKey(targetName)] = value
                is Int -> p[intPreferencesKey(targetName)] = value
                is Float -> p[floatPreferencesKey(targetName)] = value
                is String -> p[stringPreferencesKey(targetName)] = value
                // Unset on the source means "default" there, so the target returns to its default too.
                else -> {
                    val stale = snapshot.keys.firstOrNull { it.name == targetName }
                    if (stale != null) p.remove(stale as Preferences.Key<Any>)
                }
            }
        }
    }

    suspend fun copyDockFavorites(from: ScreenType, to: ScreenType) = edit { p ->
        if (from == to) return@edit
        p[Keys.dockFavorites(to)] = p[Keys.dockFavorites(from)] ?: ""
    }

    /**
     * One-time split of the two display profiles. Older builds let the inner profile read the outer
     * display's value whenever it had none of its own, so changing a cover-screen setting silently
     * changed the inner screen as well. This freezes what the inner profile inherited at that
     * moment into its own keys; from then on each display only ever reads and writes its own.
     */
    private fun splitProfilesOnce(p: MutablePreferences) {
        if (p[Keys.PROFILES_SPLIT] == true) return
        val all = p.asMap()
        for (name in INHERITED_KEY_NAMES) {
            val innerName = name + INNER_SUFFIX
            if (all.keys.any { it.name == innerName }) continue
            when (val value = all.entries.firstOrNull { it.key.name == name }?.value) {
                is Boolean -> p[booleanPreferencesKey(innerName)] = value
                is Int -> p[intPreferencesKey(innerName)] = value
                is Float -> p[floatPreferencesKey(innerName)] = value
                is String -> p[stringPreferencesKey(innerName)] = value
                else -> Unit
            }
        }
        // Two legacy keys used a different name than today's outer key.
        p[Keys.FOLD_TRANSITION_STYLE]?.let { legacy ->
            for (type in ScreenType.entries) {
                if (p[Keys.foldTransitionStyle(type)] == null) p[Keys.foldTransitionStyle(type)] = legacy
            }
        }
        p[Keys.DOUBLE_TAP_LOCK]?.let { legacy ->
            for (type in ScreenType.entries) {
                if (p[Keys.doubleTapToLock(type)] == null) p[Keys.doubleTapToLock(type)] = legacy
            }
        }
        for (type in ScreenType.entries) {
            if (p[Keys.doubleTapToLock(type)] == true) {
                if (p[Keys.doubleTapAction(type)] == null || p[Keys.doubleTapAction(type)] == HomeGesture.NONE.stored) {
                    p[Keys.doubleTapAction(type)] = HomeGesture.LOCK.stored
                }
            }
        }
        p[Keys.PROFILES_SPLIT] = true
    }


    /** The dock's apps in order, as stored (for an undo that puts one back in its place). */
    suspend fun dockKeys(screenType: ScreenType = ScreenType.OUTER): List<String> =
        dataStore.data.first()[Keys.dockFavorites(screenType)]?.split("\n")?.filter { it.isNotEmpty() } ?: emptyList()

    suspend fun addToDock(key: String, screenType: ScreenType = ScreenType.OUTER) = edit { p ->
        val current = currentFavorites(p, screenType).toMutableList()
        if (key !in current) current.add(key)
        p[Keys.dockFavorites(screenType)] = current.joinToString("\n")
    }

    suspend fun removeFromDock(key: String, screenType: ScreenType = ScreenType.OUTER) = edit { p ->
        val current = currentFavorites(p, screenType).toMutableList()
        current.remove(key)
        p[Keys.dockFavorites(screenType)] = current.joinToString("\n")
    }

    suspend fun addToDockAt(key: String, index: Int, screenType: ScreenType = ScreenType.OUTER) = edit { p ->
        val current = currentFavorites(p, screenType).toMutableList()
        current.remove(key)
        current.add(index.coerceIn(0, current.size), key)
        p[Keys.dockFavorites(screenType)] = current.joinToString("\n")
    }

    suspend fun reorderVisibleDock(visibleKeys: List<String>, screenType: ScreenType = ScreenType.OUTER) = edit { p ->
        val normalizedVisible = visibleKeys.distinct()
        val hiddenTail = currentFavorites(p, screenType).filter { it !in normalizedVisible }
        p[Keys.dockFavorites(screenType)] = (normalizedVisible + hiddenTail).joinToString("\n")
    }

    private fun currentFavorites(p: MutablePreferences, screenType: ScreenType = ScreenType.OUTER): List<String> =
        p[Keys.dockFavorites(screenType)]?.split("\n")?.filter { it.isNotEmpty() } ?: emptyList()

    // --- First-run default layout + onboarding (device-local) ---
    suspend fun defaultLayoutSeededOnce(): Boolean = dataStore.data.first()[Keys.DEFAULT_LAYOUT_SEEDED] ?: false
    suspend fun setDefaultLayoutSeeded() = edit { it[Keys.DEFAULT_LAYOUT_SEEDED] = true }
    suspend fun onboardingDoneOnce(): Boolean = dataStore.data.first()[Keys.ONBOARDING_DONE] ?: false
    suspend fun setOnboardingDone() = edit { it[Keys.ONBOARDING_DONE] = true }

    /** One-time first-run freshness decision, persisted BEFORE any seeding writes so a mid-seed
     *  death can't flip a fresh install into an "updating user". Null = not decided yet. */
    suspend fun firstRunFreshOnce(): Boolean? =
        when (dataStore.data.first()[Keys.FIRST_RUN_FRESH]) {
            "yes" -> true
            "no" -> false
            else -> null
        }
    suspend fun setFirstRunFresh(fresh: Boolean) = edit { it[Keys.FIRST_RUN_FRESH] = if (fresh) "yes" else "no" }

    /** Replaces the (empty) dock with the first-run default apps — seeding only, not a user API. */
    suspend fun seedDock(keys: List<String>, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.dockFavorites(screenType)] = keys.joinToString("\n") }

    internal suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }


    companion object {
        private const val OPAQUE = 0xFF000000.toInt()
        private const val INNER_SUFFIX = "_inner"

        /** Outer-profile key names the inner profile used to fall back to (see splitProfilesOnce). */
        internal val INHERITED_KEY_NAMES = listOf(
            "show_drawer_search", "show_dock_labels", "show_home_labels", "show_drawer_labels",
            "show_frequent_apps", "parallax_wallpaper", "dock_opacity", "show_page_indicator",
            "left_swipe_app_key", "drawer_opens_at_top", "app_label_scale", "two_line_home_labels",
            "two_line_drawer_labels", "app_label_color", "show_status_bar", "hide_system_status_bar",
            "status_bar_scrim", "show_notif_dots", "notif_dot_count", "notif_dot_scale",
            "use_themed_icons", "icon_pack_package", "widget_tonal_background",
            "notif_widget_count_style", "show_weather",
        )

        /** Base names of every per-display setting [copyProfile] transfers (outer name; the inner
         *  key appends "_inner"). Layout data — dock favourites and home pages — is not listed. */
        internal val PROFILE_KEY_NAMES = INHERITED_KEY_NAMES + listOf(
            // The home grid size is left out on purpose: shrinking it needs a reflow of the placed
            // icons, which only the settings stepper performs.
            "dock_enabled", "dock_columns", "drawer_columns", "drawer_rows",
            "workspace_density", "amoled_dark", "allow_landscape", "swipe_down_action", "double_tap_action",
            "swipe_up_action", "drawer_search_position", "drawer_index_bar_enabled", "az_index_position", "az_index_scale", "drawer_corner_radius", "drawer_handle_style", "drawer_bottom_space", "drawer_letter_headers", "drawer_large_letter_popup", "drawer_swipe_tabs", "drawer_flick_close",
            "drawer_background_blur", "drawer_scrim_opacity", "glass_blur_radius", "glass_dark_tint",
            "drawer_style", "drawer_theme_tint", "drawer_accent_color", "drawer_custom_color",
            "drawer_glass_enabled", "drawer_panel_opacity", "drawer_height_fraction", "drawer_width_fraction",
            "drawer_alignment", "drawer_layout_mode", "dpi_workspace", "dpi_app_drawer", "dpi_settings",
            "right_swipe_app_key", "double_tap_to_lock", "aquamorphic_touch_enabled",
            "app_launch_zoom_enabled", "page_bounce_enabled", "fold_transition_style",
            "icon_size_scale", "folder_shape", "folder_theme", "folder_preview", "folder_open_columns",
            "folder_open_size", "folder_tap_first_app",
            "popup_theme", "new_page_on_drag", "stack_auto_rotate_seconds", "stack_corner_radius", "stack_indicator", "stack_show_pill", "dot_shape", "dot_color", "dot_position", "home_wallpaper_dim", "home_wallpaper_blur", "icon_swipe_up_action", "auto_remove_empty_pages", "drawer_folder_tabs",
        )

        /** The stored key name of [base] for [type]. The fold transition is the one key whose outer
         *  name carries a suffix too. */
        internal fun profileKeyName(base: String, type: ScreenType): String = when {
            base == "fold_transition_style" -> if (type == ScreenType.OUTER) "fold_transition_style_outer" else "fold_transition_style_inner"
            type == ScreenType.OUTER -> base
            else -> base + INNER_SUFFIX
        }

        /** Valid range for the home/dock/drawer column counts (mirrors the settings steppers). */
        const val MIN_COLUMNS = 3
        const val MAX_COLUMNS = 8

        /** Valid range for the home-grid row count (mirrors the settings stepper). */
        const val MIN_ROWS = 4
        const val MAX_ROWS = 8

        /** The home grid goes two further than the dock and drawer (columns and rows). */
        const val MAX_HOME_COLUMNS = MAX_COLUMNS + 2
        const val MAX_HOME_ROWS = MAX_ROWS + 2
        const val MIN_DRAWER_ROWS = 3
        const val MAX_DRAWER_ROWS = 8

        /** Valid range for the notification-dot scale slider. */
        const val MIN_DOT_SCALE = 0.6f
        const val MAX_DOT_SCALE = 1.8f

        /** Valid range for the app-label text-size slider. */
        const val MIN_LABEL_SCALE = 0.8f
        const val MAX_LABEL_SCALE = 1.6f
    }
}

/** A notification channel muted from the notifications widget: its app, id and (when known) name. */
data class HiddenChannel(val packageName: String, val channelId: String, val name: String?)

/** How long an installed app keeps its "new" mark in the drawer when it is not opened. */
const val NEW_APP_DAYS = 7L
