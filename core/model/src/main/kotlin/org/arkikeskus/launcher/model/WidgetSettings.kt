package org.arkikeskus.launcher.model

import androidx.compose.runtime.Immutable

/** Folark's built-in widgets. */
@Immutable
data class WidgetSettings(
    /** Count indicator of the built-in notifications widget: [LauncherSettings.COUNT_NUMBER], [LauncherSettings.COUNT_DOT] or [LauncherSettings.COUNT_NONE]. */
    /** Use theme surfaces for built-in widgets; false keeps the translucent wallpaper style. */
    val tonalBackground: Boolean = false,
    val notificationCountStyle: String = LauncherSettings.COUNT_NUMBER,
    /** What the built-in people widget shows of a notification on the (shared) home screen:
     *  [LauncherSettings.PRIVACY_ALL] = sender + message text, [LauncherSettings.PRIVACY_SENDER] = sender only, [LauncherSettings.PRIVACY_COUNT] = the
     *  tile colors and counts but shows no text at all (pinned people keep their user-chosen name). */
    val peoplePrivacy: String = LauncherSettings.PRIVACY_ALL,
    /** Hold messages from people who aren't pinned and deliver them in batches at [peopleBatchTimes]
     *  in launcher surfaces only. Android notifications remain active so new messages from pinned
     *  people and one-time codes can bypass the batch. Missed calls always come through. */
    val peopleBatchEnabled: Boolean = false,
    /** Comma-separated "HH:mm" delivery times for the batch; see BatchSchedule. */
    val peopleBatchTimes: String = LauncherSettings.DEFAULT_BATCH_TIMES,
    /** Also show notifications that aren't from a person in the people widget, grouped by app. */
    val peopleShowApps: Boolean = true,
    /** Widget stacks: corner radius (dp), page indicator style and whether the options pill shows. */
    val stackCornerRadius: Int = 24,
    /** Seconds after which a widget stack moves on to its next widget by itself; 0 = never. */
    val stackAutoRotateSeconds: Int = 0,
    val stackIndicator: String = LauncherSettings.STACK_INDICATOR_DOTS,
    val stackShowPill: Boolean = false,
    // --- Folark's own widgets (both displays): a theme each, plus their options -----------------
    /** Now playing: theme, and whether the cover art fills the widget's background. */
    val mediaTheme: String = LauncherSettings.WIDGET_THEME_DEFAULT,
    val mediaArtwork: Boolean = true,
    /** Most used apps: theme, and whether the icons carry labels. */
    val appsTheme: String = LauncherSettings.WIDGET_THEME_DEFAULT,
    val appsLabels: Boolean = false,
    /** Device status: theme, and which gauges it shows. */
    val statsTheme: String = LauncherSettings.WIDGET_THEME_DEFAULT,
    val statsShowBattery: Boolean = true,
    val statsShowStorage: Boolean = true,
    val statsShowMemory: Boolean = true,
    val statsShowBluetooth: Boolean = true,
    /** Quick note: theme, text size (LauncherSettings.FOLDER_OPEN_SMALL/MEDIUM/LARGE) and the note itself. */
    val noteTheme: String = LauncherSettings.WIDGET_THEME_DEFAULT,
    val noteTextSize: String = LauncherSettings.FOLDER_OPEN_MEDIUM,
    val noteText: String = "",
    /** How solid the card behind Folark's own widgets is (both displays): 0 = see-through, 1 = as themed. */
    val backgroundOpacity: Float = 0f,
    /** Digital clock widget (both displays): dark text instead of white. */
    val clockDarkText: Boolean = false,
    /** Digital clock widget (both displays): its look, one of CLOCK_STYLE_*. */
    val clockStyle: String = LauncherSettings.CLOCK_STYLE_CLASSIC,
    /** Digital clock widget (both displays): frosted liquid glass card backdrop. */
    val clockGlassEffect: Boolean = true,
    /** Digital clock widget (both displays): the minutes' colour, one of CLOCK_MINUTE_*. */
    val clockMinuteColor: String = LauncherSettings.CLOCK_MINUTE_STYLE,
    /** Digital clock widget (both displays): how opaque the minutes are, 0.1..1. */
    val clockMinuteOpacity: Float = 1f,
    /** Weather day widget (both displays): animated conditions behind it. */
    val weatherAnimated: Boolean = true,
    /** How long the sky moves each time home comes back, in seconds (0 = as long as home shows). */
    val weatherAwakeSeconds: Int = 45,
    /** Show the current weather (Open-Meteo; needs a location permission) in the smartspace widget. */
    val showWeather: Boolean = true,
    /** A tap on Folark's weather opens Motorola Weather (where the phone has it). */
    val motoWeatherTap: Boolean = true,
)
