package org.arkikeskus.launcher.data

import org.arkikeskus.launcher.model.RootMode

// The setters for launching apps and the root features.
// Split out of SettingsRepository as extension functions, like SettingsRepositoryFolders.kt:
// the same repository and the same calls, one file per settings group.

suspend fun SettingsRepository.setFloatingWindowSize(value: String) = edit { it[Keys.FLOATING_WINDOW_SIZE] = value }
suspend fun SettingsRepository.setThreeAppSplit(value: Boolean) = edit { it[Keys.THREE_APP_SPLIT] = value }
suspend fun SettingsRepository.setThreeAppNative(value: Boolean) = edit { it[Keys.THREE_APP_NATIVE] = value }
suspend fun SettingsRepository.setRootEnabled(value: Boolean) = edit { it[Keys.ROOT_ENABLED] = value }
suspend fun SettingsRepository.setAspectRatioMenu(value: Boolean) = edit { it[Keys.ASPECT_RATIO_MENU] = value }
suspend fun SettingsRepository.setRootKeepDump(value: Boolean) = edit { it[Keys.ROOT_KEEP_DUMP] = value }
suspend fun SettingsRepository.setRootKeepUsage(value: Boolean) = edit { it[Keys.ROOT_KEEP_USAGE] = value }
suspend fun SettingsRepository.setRootKeepSecureSettings(value: Boolean) = edit { it[Keys.ROOT_KEEP_SECURE] = value }
suspend fun SettingsRepository.setRootKeepNotifications(value: Boolean) = edit { it[Keys.ROOT_KEEP_NOTIFICATIONS] = value }
suspend fun SettingsRepository.setRootKeepAccessibility(value: Boolean) = edit { it[Keys.ROOT_KEEP_ACCESSIBILITY] = value }
suspend fun SettingsRepository.setRootKeepWidgets(value: Boolean) = edit { it[Keys.ROOT_KEEP_WIDGETS] = value }
suspend fun SettingsRepository.setSplitLaunchEnabled(value: Boolean) = edit { it[Keys.SPLIT_LAUNCH_ENABLED] = value }
suspend fun SettingsRepository.setFloatingLaunchEnabled(value: Boolean) = edit { it[Keys.FLOATING_LAUNCH_ENABLED] = value }
suspend fun SettingsRepository.setRootLockMode(value: RootMode) = edit { it[Keys.ROOT_LOCK_MODE] = value.stored }
suspend fun SettingsRepository.setRootRecentsMode(value: RootMode) = edit { it[Keys.ROOT_RECENTS_MODE] = value.stored }
suspend fun SettingsRepository.setRootShadeMode(value: RootMode) = edit { it[Keys.ROOT_SHADE_MODE] = value.stored }
