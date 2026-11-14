package org.arkikeskus.launcher.model

import androidx.compose.runtime.Immutable

/** Folark's built-in widgets. */
@Immutable
data class WidgetSettings(
    /** Count indicator of the built-in notifications widget: [CountStyle.NUMBER], [CountStyle.DOT] or [CountStyle.NONE]. */
    /** Use theme surfaces for built-in widgets; false keeps the translucent wallpaper style. */
    val tonalBackground: Boolean = false,
    val notificationCountStyle: CountStyle = CountStyle.NUMBER,
    /** What the built-in people widget shows of a notification on the (shared) home screen:
     *  [PeoplePrivacy.ALL] = sender + message text, [PeoplePrivacy.SENDER] = sender only, [PeoplePrivacy.COUNT] = the
     *  tile colors and counts but shows no text at all (pinned people keep their user-chosen name). */
    val peoplePrivacy: PeoplePrivacy = PeoplePrivacy.ALL,
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
    val stackIndicator: StackIndicator = StackIndicator.DOTS,
    val stackShowPill: Boolean = false,
    // --- Folark's own widgets (both displays): a theme each, plus their options -----------------
    /** Now playing: theme, and whether the cover art fills the widget's background. */
    val mediaTheme: WidgetTheme = WidgetTheme.DEFAULT,
    val mediaArtwork: Boolean = true,
    /** Most used apps: theme, and whether the icons carry labels. */
    val appsTheme: WidgetTheme = WidgetTheme.DEFAULT,
    val appsLabels: Boolean = false,
    /** Device status: theme, and which gauges it shows. */
    val statsTheme: WidgetTheme = WidgetTheme.DEFAULT,
    val statsShowBattery: Boolean = true,
    val statsShowStorage: Boolean = true,
    val statsShowMemory: Boolean = true,
    val statsShowBluetooth: Boolean = true,
    /** Quick note: theme, text size (SizeChoice.SMALL/MEDIUM/LARGE) and the note itself. */
    val noteTheme: WidgetTheme = WidgetTheme.DEFAULT,
    val noteTextSize: SizeChoice = SizeChoice.MEDIUM,
    val noteText: String = "",
    /** How solid the card behind Folark's own widgets is (both displays): 0 = see-through, 1 = as themed. */
    val backgroundOpacity: Float = 0f,
    /** Digital clock widget (both displays): dark text instead of white. */
    val clockDarkText: Boolean = false,
    /** Digital clock widget (both displays): its look, one of [ClockStyle]. */
    val clockStyle: ClockStyle = ClockStyle.CLASSIC,
    /** Digital clock widget (both displays): frosted liquid glass card backdrop. */
    val clockGlassEffect: Boolean = true,
    /** Digital clock widget (both displays): the minutes' colour, one of [ClockMinuteColor]. */
    val clockMinuteColor: ClockMinuteColor = ClockMinuteColor.STYLE,
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

/** The clock widget's minutes: as its style colours them (accent or like the hours), or a grey. */
enum class ClockMinuteColor(override val stored: String) : StoredChoice {
    STYLE("style"),
    WHITE("white"),
    LIGHT_GREY("light_grey"),
    GREY("grey"),
    DARK_GREY("dark_grey"),
    BLACK("black"),
}

/** The clock widget's style. */
enum class ClockStyle(override val stored: String) : StoredChoice {
    CLASSIC("classic"),
    DUO("duo"),
    LIGHT("light"),
    DOT("dot"),
    STACKED("stacked"),
    GLASS_PILL("glass_pill"),
    OUTLINE("outline"),
    VERTICAL_STACK("vertical_stack"),
    NEUMORPHIC("neumorphic"),
    NOTHING_NDOT("nothing_ndot"),
}

/** How the notification widget shows how many notifications an app has. */
enum class CountStyle(override val stored: String) : StoredChoice {
    NUMBER("number"),
    DOT("dot"),
    NONE("none"),
}

/** How much the people widget shows of a message. */
enum class PeoplePrivacy(override val stored: String) : StoredChoice {
    ALL("all"),
    SENDER("sender"),
    COUNT("count"),
}

/** A widget stack's page indicator. */
enum class StackIndicator(override val stored: String) : StoredChoice {
    DOTS("dots"),
    BAR("bar"),
    NONE("none"),
}

/** Themes of Folark's own widgets: the standard translucent card, or a solid one. */
enum class WidgetTheme(override val stored: String) : StoredChoice {
    DEFAULT("default"),
    DARK("dark"),
    LIGHT("light"),
    ACCENT("accent"),
}
