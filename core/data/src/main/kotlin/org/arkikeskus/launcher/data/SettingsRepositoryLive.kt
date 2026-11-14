package org.arkikeskus.launcher.data

import org.arkikeskus.launcher.model.LiveEffect

// The setters for live activities and notification extras.
// Split out of SettingsRepository as extension functions, like SettingsRepositoryFolders.kt:
// the same repository and the same calls, one file per settings group.

suspend fun SettingsRepository.setPopupNotifications(value: Boolean) = edit { it[Keys.POPUP_NOTIFICATIONS] = value }
suspend fun SettingsRepository.setLiveActivities(value: Boolean) = edit { it[Keys.LIVE_ACTIVITIES] = value }
suspend fun SettingsRepository.setLiveActivitiesTaskbar(value: Boolean) = edit { it[Keys.LIVE_ACTIVITIES_TASKBAR] = value }
suspend fun SettingsRepository.setLiveExpand(value: Boolean) = edit { it[Keys.LIVE_EXPAND] = value }
suspend fun SettingsRepository.setLiveSkipSystemChips(value: Boolean) = edit { it[Keys.LIVE_SKIP_SYSTEM_CHIPS] = value }
suspend fun SettingsRepository.setLiveActivityEffect(value: LiveEffect) = edit { it[Keys.LIVE_ACTIVITY_EFFECT] = value.stored }
