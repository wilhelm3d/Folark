package org.arkikeskus.launcher.data

import android.content.Context
import android.content.Intent
import org.arkikeskus.launcher.model.LauncherSettings
import java.io.File

/**
 * Motorola's own screens that any app may open (each found in the phone's system image, exported
 * without a permission), and the wallpapers that ship with the phone. On other phones nothing
 * resolves and the callers hide their rows.
 */
object MotoLinks {
    /** Motorola Weather (the app behind Motorola's weather widgets). */
    const val WEATHER = "com.motorola.commandcenter.action.START_WEATHER_APPLICATION"
    /** Moto Styles: colours, fonts, icon shape and grid for the whole phone. */
    const val STYLES = "com.motorola.personalize.action.STYLES"
    /** Wallpaper picker for the second display (a flip's or fold's outer screen). */
    const val SECOND_SCREEN_WALLPAPER = "com.motorola.intent.action.SET_SECONDARY_WALLPAPER"
    /** Clock faces for the second display. */
    const val SECOND_SCREEN_CLOCK = "com.motorola.intent.action.SECONDARY_CLOCKFACE_PICKER"
    /** Moto gestures (chop for the torch, twist for the camera, ...). */
    const val GESTURES = "com.motorola.actions.ACTION_SETTINGS"
    /** Motorola's swipe-to-split gesture. */
    const val SWIPE_TO_SPLIT = "com.motorola.actions.swipe_to_split.SETTINGS"

    private val WALLPAPER_DIR = File("/product/wallpaper/image")

    fun intent(action: String): Intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Whether this phone has a screen for [action]. */
    fun available(context: Context, action: String): Boolean = runCatching {
        context.packageManager.resolveActivity(Intent(action), 0) != null
    }.getOrDefault(false)

    /** Opens [action]; false if this phone has no such screen. */
    fun open(context: Context, action: String): Boolean = runCatching {
        context.startActivity(intent(action))
        true
    }.getOrDefault(false)

    /** The wallpapers that came with the phone (readable pictures only). */
    fun wallpapers(): List<File> = runCatching {
        WALLPAPER_DIR.listFiles()
            ?.filter { it.isFile && it.canRead() && it.extension.lowercase() in PICTURE_TYPES }
            ?.sortedBy { it.name }
            .orEmpty()
    }.getOrDefault(emptyList())

    private val PICTURE_TYPES = setOf("jpg", "jpeg", "png", "webp")

    /** What each Motorola home gesture (LauncherSettings.GESTURE_MOTO_*) opens: the first the phone has. */
    private fun gestureIntents(context: Context, gesture: String): List<Intent> = when (gesture) {
        // Moto Note (Journal), or the older stylus notes app: a new drawing, else the notes list.
        LauncherSettings.GESTURE_MOTO_NOTE -> listOf(
            "com.motorola.journal.DRAW_NOTE",
            "com.motorola.stylus.DRAW_NOTE",
            "com.motorola.journal.OPEN",
            "com.motorola.stylus.OPEN",
        ).map(::intent)
        LauncherSettings.GESTURE_MOTO_VAULT -> listOf(intent("com.motorola.securevault.LAUNCH_SECURE_VAULT"))
        LauncherSettings.GESTURE_MOTO_READY_FOR -> listOf(intent("com.motorola.readyfor.OPEN_READY_FOR"))
        LauncherSettings.GESTURE_MOTO_GAMES -> listOfNotNull(
            runCatching { context.packageManager.getLaunchIntentForPackage(GAME_MODE_PACKAGE) }.getOrNull()
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        LauncherSettings.GESTURE_MOTO_WEATHER -> listOf(intent(WEATHER))
        LauncherSettings.GESTURE_MOTO_STYLES -> listOf(intent(STYLES))
        else -> emptyList()
    }

    private fun resolves(context: Context, intent: Intent): Boolean = runCatching {
        context.packageManager.resolveActivity(intent, 0) != null
    }.getOrDefault(false)

    /** Whether this phone has what [gesture] opens (Settings offers only those). */
    fun gestureAvailable(context: Context, gesture: String): Boolean =
        gestureIntents(context, gesture).any { resolves(context, it) }

    /** Runs a Motorola home gesture; false if the phone has nothing for it. */
    fun runGesture(context: Context, gesture: String): Boolean {
        val target = gestureIntents(context, gesture).firstOrNull { resolves(context, it) } ?: return false
        return runCatching { context.startActivity(target) }.isSuccess
    }

    private const val GAME_MODE_PACKAGE = "com.motorola.gamemode"
}
