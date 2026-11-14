package org.arkikeskus.launcher.data

import org.arkikeskus.launcher.model.ClockMinuteColor
import org.arkikeskus.launcher.model.ClockStyle
import org.arkikeskus.launcher.model.CountStyle
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.PeoplePrivacy
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.model.SizeChoice
import org.arkikeskus.launcher.model.StackIndicator
import org.arkikeskus.launcher.model.WidgetTheme

// The setters for the widgets.
// Split out of SettingsRepository as extension functions, like SettingsRepositoryFolders.kt:
// the same repository and the same calls, one file per settings group.

suspend fun SettingsRepository.setStackAutoRotateSeconds(value: Int, screenType: ScreenType = ScreenType.OUTER) = edit {
    it[Keys.stackAutoRotateSeconds(screenType)] = value.coerceIn(0, LauncherSettings.MAX_STACK_AUTO_ROTATE_SECONDS)
}
suspend fun SettingsRepository.setStackCornerRadius(value: Int, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.stackCornerRadius(screenType)] = value.coerceIn(0, LauncherSettings.MAX_STACK_CORNER_RADIUS) }
suspend fun SettingsRepository.setStackIndicator(value: StackIndicator, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.stackIndicator(screenType)] = value.stored }
suspend fun SettingsRepository.setStackShowPill(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.stackShowPill(screenType)] = value }
suspend fun SettingsRepository.setWidgetBackgroundOpacity(value: Float) = edit { it[Keys.WIDGET_BACKGROUND_OPACITY] = value.coerceIn(0f, 1f) }
suspend fun SettingsRepository.setClockWidgetStyle(value: ClockStyle) = edit { it[Keys.CLOCK_WIDGET_STYLE] = value.stored }
suspend fun SettingsRepository.setClockWidgetDarkText(value: Boolean) = edit { it[Keys.CLOCK_WIDGET_DARK_TEXT] = value }
suspend fun SettingsRepository.setClockWidgetGlassEffect(value: Boolean) = edit { it[Keys.CLOCK_WIDGET_GLASS_EFFECT] = value }
suspend fun SettingsRepository.setClockMinuteColor(value: ClockMinuteColor) = edit { it[Keys.CLOCK_MINUTE_COLOR] = value.stored }
suspend fun SettingsRepository.setClockMinuteOpacity(value: Float) = edit { it[Keys.CLOCK_MINUTE_OPACITY] = value.coerceIn(0.1f, 1f) }
suspend fun SettingsRepository.setWeatherWidgetAnimated(value: Boolean) = edit { it[Keys.WEATHER_WIDGET_ANIMATED] = value }
suspend fun SettingsRepository.setWeatherAwakeSeconds(value: Int) = edit { it[Keys.WEATHER_AWAKE_SECONDS] = value }
suspend fun SettingsRepository.setMotoWeatherTap(value: Boolean) = edit { it[Keys.MOTO_WEATHER_TAP] = value }
suspend fun SettingsRepository.setMediaWidgetTheme(value: WidgetTheme) = edit { it[Keys.MEDIA_WIDGET_THEME] = value.stored }
suspend fun SettingsRepository.setAppsWidgetTheme(value: WidgetTheme) = edit { it[Keys.APPS_WIDGET_THEME] = value.stored }
suspend fun SettingsRepository.setStatsWidgetTheme(value: WidgetTheme) = edit { it[Keys.STATS_WIDGET_THEME] = value.stored }
suspend fun SettingsRepository.setNoteWidgetTheme(value: WidgetTheme) = edit { it[Keys.NOTE_WIDGET_THEME] = value.stored }
suspend fun SettingsRepository.setMediaWidgetArtwork(value: Boolean) = edit { it[Keys.MEDIA_WIDGET_ARTWORK] = value }
suspend fun SettingsRepository.setAppsWidgetLabels(value: Boolean) = edit { it[Keys.APPS_WIDGET_LABELS] = value }
suspend fun SettingsRepository.setStatsShowBattery(value: Boolean) = edit { it[Keys.STATS_SHOW_BATTERY] = value }
suspend fun SettingsRepository.setStatsShowStorage(value: Boolean) = edit { it[Keys.STATS_SHOW_STORAGE] = value }
suspend fun SettingsRepository.setStatsShowMemory(value: Boolean) = edit { it[Keys.STATS_SHOW_MEMORY] = value }
suspend fun SettingsRepository.setStatsShowBluetooth(value: Boolean) = edit { it[Keys.STATS_SHOW_BLUETOOTH] = value }
suspend fun SettingsRepository.setNoteWidgetTextSize(value: SizeChoice) = edit { it[Keys.NOTE_WIDGET_TEXT_SIZE] = value.stored }
/** The quick-note widget's text; capped so a pasted wall of text cannot bloat every settings read. */
suspend fun SettingsRepository.setNoteWidgetText(value: String) = edit { it[Keys.NOTE_WIDGET_TEXT] = value.take(4000) }
suspend fun SettingsRepository.setPeopleBatchEnabled(value: Boolean) = edit { it[Keys.PEOPLE_BATCH_ENABLED] = value }
suspend fun SettingsRepository.setPeopleShowApps(value: Boolean) = edit { it[Keys.PEOPLE_SHOW_APPS] = value }
/** Batch delivery times; normalized through [BatchSchedule], falling back to the default when
 *  nothing parses so the batch can never silently hold messages forever. */
suspend fun SettingsRepository.setPeopleBatchTimes(raw: String) = edit { p ->
    val parsed = BatchSchedule.parse(raw)
    p[Keys.PEOPLE_BATCH_TIMES] = if (parsed.isEmpty()) LauncherSettings.DEFAULT_BATCH_TIMES else BatchSchedule.format(parsed)
}
/** What the people widget shows of a notification (see [PeoplePrivacy]). */
suspend fun SettingsRepository.setPeoplePrivacy(value: PeoplePrivacy) = edit { it[Keys.PEOPLE_PRIVACY] = value.stored }
/** Count indicator style of the built-in notifications widget (see [CountStyle]). */
suspend fun SettingsRepository.setWidgetTonalBackground(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.widgetTonalBackground(screenType)] = value }
suspend fun SettingsRepository.setNotificationWidgetCountStyle(style: CountStyle, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.notificationWidgetCountStyle(screenType)] = style.stored }
suspend fun SettingsRepository.setShowWeather(value: Boolean, screenType: ScreenType = ScreenType.OUTER) = edit { it[Keys.showWeather(screenType)] = value }
