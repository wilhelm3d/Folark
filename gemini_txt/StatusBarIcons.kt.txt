package org.arkikeskus.launcher.data

import android.content.Context
import android.provider.Settings
import org.arkikeskus.launcher.data.root.RootShell

/**
 * Hiding some of Android's own status bar icons (VPN key, NFC, alarm...). Android's SystemUI keeps a
 * list of hidden icons in the secure setting `icon_blacklist` (the same one its hidden "System UI
 * tuner" used); Folark changes that list with the one-time WRITE_SECURE_SETTINGS grant (Settings ▸
 * Permissions ▸ System controls) or, if that isn't granted, with root. Only the icons listed in
 * [SLOTS] are touched: anything else already in the list is kept as it is.
 */
object StatusBarIcons {
    private const val KEY = "icon_blacklist"

    /** The icons Folark offers, by Android's slot name, with their names in settings. */
    val SLOTS: List<Pair<String, String>> = listOf(
        "vpn" to "VPN key",
        "nfc" to "NFC",
        "alarm_clock" to "Alarm",
        "bluetooth" to "Bluetooth",
        "headset" to "Headphones",
        "hotspot" to "Hotspot",
        "location" to "Location",
        "data_saver" to "Data saver",
        "zen" to "Do Not Disturb",
        "volume" to "Vibrate and silent",
        "cast" to "Cast",
        "screen_record" to "Screen recording",
        "managed_profile" to "Work profile",
        "airplane" to "Airplane mode",
        "rotate" to "Rotation suggestion",
        "sensors_off" to "Sensors off",
        "wifi" to "Wi-Fi",
        "mobile" to "Mobile signal",
        "battery" to "Battery",
        "clock" to "Clock",
    )

    private val slotNames = SLOTS.map { it.first }.toSet()

    /** Everything in Android's hidden-icons list right now. */
    fun hidden(context: Context): Set<String> = runCatching {
        Settings.Secure.getString(context.contentResolver, KEY).orEmpty()
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }.getOrDefault(emptySet())

    /** Whether Folark may change the list (the grant, or root). Can run `su`: call off the main thread. */
    fun canChange(context: Context): Boolean = SystemControls.canWrite(context) || RootShell.available()

    /** Hides or shows one icon. Off the main thread. */
    fun setHidden(context: Context, slot: String, hide: Boolean): Boolean {
        val current = hidden(context)
        return write(context, if (hide) current + slot else current - slot)
    }

    /** Shows every icon Folark offers again (others hidden by someone else stay hidden). */
    fun showAll(context: Context): Boolean = write(context, hidden(context) - slotNames)

    private fun write(context: Context, slots: Set<String>): Boolean {
        val value = slots.joinToString(",")
        if (SystemControls.canWrite(context)) {
            return runCatching {
                Settings.Secure.putString(context.contentResolver, KEY, value.ifEmpty { null })
            }.getOrDefault(false)
        }
        if (!RootShell.available()) return false
        // Slot names are Folark's own fixed list (letters and underscores), safe in the command.
        val safe = slots.filter { it.all { c -> c.isLetterOrDigit() || c == '_' } }.joinToString(",")
        val command = if (safe.isEmpty()) "settings delete secure $KEY" else "settings put secure $KEY $safe"
        return RootShell.run(command) != null
    }
}
