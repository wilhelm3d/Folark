package org.arkikeskus.launcher.data.search

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.arkikeskus.launcher.data.R
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.model.SearchResult
import javax.inject.Inject

/** A curated, localizable settings page entry. */
data class SettingItem(
    val key: String,
    val action: String,
    val label: String,
    val aliases: List<String>,
)

/** Pure matcher: a [SettingItem] matches when the trimmed query is a substring of its label or any alias. */
fun matchSettings(items: List<SettingItem>, query: String): List<SearchResult.Setting> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    return items.filter { item ->
        item.label.lowercase().contains(q) || item.aliases.any { it.lowercase().contains(q) }
    }.map { SearchResult.Setting(it.label, it.action, it.key) }
}

/**
 * Searches a curated set of common system settings pages. No public API enumerates all settings
 * without system privileges, so this is a hand-maintained list with localized labels + bilingual
 * keyword aliases. No permission; Settings ▸ Search ▸ Settings pages in search turns it off.
 */
class SettingsSearchProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) : SearchProvider {

    override suspend fun isEnabled(): Boolean =
        settingsRepository.settings(ScreenType.OUTER).first().search.settingsPages

    override suspend fun query(query: String): List<SearchResult> = matchSettings(items(), query)

    /** The pages this phone actually has (some makers leave pages out), worked out once. */
    private val available: List<Quad> by lazy {
        CURATED.filter { q ->
            runCatching { context.packageManager.resolveActivity(android.content.Intent(q.action), 0) != null }
                .getOrDefault(true)
        }
    }

    private fun items(): List<SettingItem> = available.map { (key, action, labelRes, aliasRes) ->
        SettingItem(
            key = key,
            action = action,
            label = context.getString(labelRes),
            aliases = context.getString(aliasRes).split(",").map { it.trim() }.filter { it.isNotEmpty() },
        )
    }

    private companion object {
        // key, intent action, label resId, comma-separated aliases resId
        val CURATED = listOf(
            Quad("wifi", "android.settings.WIFI_SETTINGS", R.string.setting_wifi, R.string.setting_wifi_aliases),
            Quad("bluetooth", "android.settings.BLUETOOTH_SETTINGS", R.string.setting_bluetooth, R.string.setting_bluetooth_aliases),
            Quad("network", "android.settings.WIRELESS_SETTINGS", R.string.setting_network, R.string.setting_network_aliases),
            Quad("data", "android.settings.DATA_USAGE_SETTINGS", R.string.setting_data, R.string.setting_data_aliases),
            Quad("battery", "android.settings.BATTERY_SAVER_SETTINGS", R.string.setting_battery, R.string.setting_battery_aliases),
            Quad("display", "android.settings.DISPLAY_SETTINGS", R.string.setting_display, R.string.setting_display_aliases),
            Quad("sound", "android.settings.SOUND_SETTINGS", R.string.setting_sound, R.string.setting_sound_aliases),
            Quad("apps", "android.settings.APPLICATION_SETTINGS", R.string.setting_apps, R.string.setting_apps_aliases),
            Quad("storage", "android.settings.INTERNAL_STORAGE_SETTINGS", R.string.setting_storage, R.string.setting_storage_aliases),
            Quad("location", "android.settings.LOCATION_SOURCE_SETTINGS", R.string.setting_location, R.string.setting_location_aliases),
            Quad("datetime", "android.settings.DATE_SETTINGS", R.string.setting_datetime, R.string.setting_datetime_aliases),
            Quad("security", "android.settings.SECURITY_SETTINGS", R.string.setting_security, R.string.setting_security_aliases),
            Quad("accessibility", "android.settings.ACCESSIBILITY_SETTINGS", R.string.setting_accessibility, R.string.setting_accessibility_aliases),
            Quad("language", "android.settings.LOCALE_SETTINGS", R.string.setting_language, R.string.setting_language_aliases),
            Quad("about", "android.settings.DEVICE_INFO_SETTINGS", R.string.setting_about, R.string.setting_about_aliases),
            Quad("systemupdate", "android.settings.SYSTEM_UPDATE_SETTINGS", R.string.setting_systemupdate, R.string.setting_systemupdate_aliases),
            Quad("notifications", "android.settings.NOTIFICATION_SETTINGS", R.string.setting_notifications, R.string.setting_notifications_aliases),
            Quad("appnotifications", "android.settings.ALL_APPS_NOTIFICATION_SETTINGS", R.string.setting_app_notifications, R.string.setting_app_notifications_aliases),
            Quad("dnd", "android.settings.ZEN_MODE_SETTINGS", R.string.setting_dnd, R.string.setting_dnd_aliases),
            Quad("hotspot", "android.settings.TETHER_SETTINGS", R.string.setting_hotspot, R.string.setting_hotspot_aliases),
            Quad("nfc", "android.settings.NFC_SETTINGS", R.string.setting_nfc, R.string.setting_nfc_aliases),
            Quad("vpn", "android.settings.VPN_SETTINGS", R.string.setting_vpn, R.string.setting_vpn_aliases),
            Quad("mobile", "android.settings.NETWORK_OPERATOR_SETTINGS", R.string.setting_mobile, R.string.setting_mobile_aliases),
            Quad("airplane", "android.settings.AIRPLANE_MODE_SETTINGS", R.string.setting_airplane, R.string.setting_airplane_aliases),
            Quad("cast", "android.settings.CAST_SETTINGS", R.string.setting_cast, R.string.setting_cast_aliases),
            Quad("wallpaper", "android.settings.WALLPAPER_SETTINGS", R.string.setting_wallpaper, R.string.setting_wallpaper_aliases),
            Quad("darkmode", "android.settings.DARK_THEME_SETTINGS", R.string.setting_darkmode, R.string.setting_darkmode_aliases),
            Quad("nightlight", "android.settings.NIGHT_DISPLAY_SETTINGS", R.string.setting_nightlight, R.string.setting_nightlight_aliases),
            Quad("defaultapps", "android.settings.MANAGE_DEFAULT_APPS_SETTINGS", R.string.setting_defaultapps, R.string.setting_defaultapps_aliases),
            Quad("batteryusage", "android.intent.action.POWER_USAGE_SUMMARY", R.string.setting_batteryusage, R.string.setting_batteryusage_aliases),
            Quad("privacy", "android.settings.PRIVACY_SETTINGS", R.string.setting_privacy, R.string.setting_privacy_aliases),
            Quad("accounts", "android.settings.SYNC_SETTINGS", R.string.setting_accounts, R.string.setting_accounts_aliases),
            Quad("users", "android.settings.USER_SETTINGS", R.string.setting_users, R.string.setting_users_aliases),
            Quad("developer", "android.settings.APPLICATION_DEVELOPMENT_SETTINGS", R.string.setting_developer, R.string.setting_developer_aliases),
            Quad("usageaccess", "android.settings.USAGE_ACCESS_SETTINGS", R.string.setting_usageaccess, R.string.setting_usageaccess_aliases),
            Quad("overlay", "android.settings.action.MANAGE_OVERLAY_PERMISSION", R.string.setting_overlay, R.string.setting_overlay_aliases),
            Quad("notificationaccess", "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS", R.string.setting_notificationaccess, R.string.setting_notificationaccess_aliases),
            Quad("print", "android.settings.ACTION_PRINT_SETTINGS", R.string.setting_print, R.string.setting_print_aliases),
            Quad("input", "android.settings.INPUT_METHOD_SETTINGS", R.string.setting_input, R.string.setting_input_aliases),
            Quad("captions", "android.settings.CAPTIONING_SETTINGS", R.string.setting_captions, R.string.setting_captions_aliases),
            Quad("biometric", "android.settings.BIOMETRIC_ENROLL", R.string.setting_biometric, R.string.setting_biometric_aliases),
            Quad("screenlock", "android.app.action.SET_NEW_PASSWORD", R.string.setting_screenlock, R.string.setting_screenlock_aliases),
            Quad("homescreen", "android.settings.HOME_SETTINGS", R.string.setting_homescreen, R.string.setting_homescreen_aliases),
        )
    }
}

private data class Quad(val key: String, val action: String, val labelRes: Int, val aliasRes: Int)
