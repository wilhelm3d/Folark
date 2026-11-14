package org.arkikeskus.launcher.data

import org.arkikeskus.launcher.data.SettingsRepository.Companion.MAX_COLUMNS
import org.arkikeskus.launcher.data.SettingsRepository.Companion.MIN_COLUMNS
import org.arkikeskus.launcher.model.DropZones
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.SplitPickerLook

// The setters for Recents and the split picker.
// Split out of SettingsRepository as extension functions, like SettingsRepositoryFolders.kt:
// the same repository and the same calls, one file per settings group.

suspend fun SettingsRepository.setRecentsEnabled(value: Boolean) = edit { it[Keys.RECENTS_ENABLED] = value }
suspend fun SettingsRepository.setRecentsReplaceSystem(value: Boolean) = edit { it[Keys.RECENTS_REPLACE_SYSTEM] = value }
suspend fun SettingsRepository.setNavHandleOpensSearch(value: Boolean) = edit { it[Keys.NAV_HANDLE_SEARCH] = value }
suspend fun SettingsRepository.setRecentsColumnsInner(value: Int) = edit {
    it[Keys.RECENTS_COLUMNS_INNER] = value.coerceIn(LauncherSettings.MIN_RECENTS_COLUMNS, LauncherSettings.MAX_RECENTS_COLUMNS_INNER)
}
suspend fun SettingsRepository.setRecentsColumnsOuter(value: Int) = edit {
    it[Keys.RECENTS_COLUMNS_OUTER] = value.coerceIn(LauncherSettings.MIN_RECENTS_COLUMNS, LauncherSettings.MAX_RECENTS_COLUMNS_OUTER)
}
suspend fun SettingsRepository.setRecentsMaxApps(value: Int) = edit {
    it[Keys.RECENTS_MAX_APPS] = value.coerceIn(LauncherSettings.MIN_RECENTS_APPS, LauncherSettings.MAX_RECENTS_APPS)
}
suspend fun SettingsRepository.setRecentsCardHeight(value: Int, cover: Boolean = false) = edit {
    it[if (cover) Keys.RECENTS_OUTER_CARD_HEIGHT else Keys.RECENTS_CARD_HEIGHT] = value.coerceIn(LauncherSettings.MIN_RECENTS_CARD_HEIGHT, LauncherSettings.MAX_RECENTS_CARD_HEIGHT)
}
suspend fun SettingsRepository.setRecentsCornerDp(value: Int, cover: Boolean = false) = edit { it[if (cover) Keys.RECENTS_OUTER_CORNER else Keys.RECENTS_CORNER] = value.coerceIn(0, LauncherSettings.MAX_RECENTS_CORNER_DP) }
suspend fun SettingsRepository.setRecentsBackdropDim(value: Float, cover: Boolean = false) = edit { it[if (cover) Keys.RECENTS_OUTER_BACKDROP else Keys.RECENTS_BACKDROP] = value.coerceIn(0f, 1f) }
suspend fun SettingsRepository.setRecentsSort(value: String) = edit { it[Keys.RECENTS_SORT] = value }
suspend fun SettingsRepository.setRecentsShowTime(value: Boolean, cover: Boolean = false) = edit { it[if (cover) Keys.RECENTS_OUTER_SHOW_TIME else Keys.RECENTS_SHOW_TIME] = value }
suspend fun SettingsRepository.setRecentsCardLayout(value: String, cover: Boolean = false) = edit { it[if (cover) Keys.RECENTS_OUTER_CARD_LAYOUT else Keys.RECENTS_CARD_LAYOUT] = value }
suspend fun SettingsRepository.setRecentsConfirmCloseAll(value: Boolean) = edit { it[Keys.RECENTS_CONFIRM_CLOSE_ALL] = value }
suspend fun SettingsRepository.setRecentsThirdInSplit(value: Boolean) = edit { it[Keys.RECENTS_THIRD_IN_SPLIT] = value }
suspend fun SettingsRepository.setRecentsThumbnails(value: Boolean) = edit { it[Keys.RECENTS_THUMBNAILS] = value }
suspend fun SettingsRepository.setRecentsSpeakerBadge(value: Boolean) = edit { it[Keys.RECENTS_SPEAKER_BADGE] = value }
suspend fun SettingsRepository.setRecentsMemoryBadge(value: Boolean) = edit { it[Keys.RECENTS_MEMORY_BADGE] = value }
suspend fun SettingsRepository.setRecentsGroupSplits(value: Boolean) = edit { it[Keys.RECENTS_GROUP_SPLITS] = value }
suspend fun SettingsRepository.setRecentsRootClose(value: Boolean) = edit { it[Keys.RECENTS_ROOT_CLOSE] = value }
suspend fun SettingsRepository.setRecentsEnterAnimation(value: String, cover: Boolean = false) = edit { it[if (cover) Keys.RECENTS_OUTER_ENTER else Keys.RECENTS_ENTER] = value }
suspend fun SettingsRepository.setRecentsSelectAnimation(value: String, cover: Boolean = false) = edit { it[if (cover) Keys.RECENTS_OUTER_SELECT else Keys.RECENTS_SELECT] = value }
suspend fun SettingsRepository.setRecentsAnimationSpeed(value: Float, cover: Boolean = false) = edit {
    it[if (cover) Keys.RECENTS_OUTER_ANIM_SPEED else Keys.RECENTS_ANIM_SPEED] = value.coerceIn(LauncherSettings.MIN_RECENTS_ANIM_SPEED, LauncherSettings.MAX_RECENTS_ANIM_SPEED)
}
suspend fun SettingsRepository.setRecentsTheme(value: String, cover: Boolean = false) = edit { it[if (cover) Keys.RECENTS_OUTER_THEME else Keys.RECENTS_THEME] = value }
suspend fun SettingsRepository.setRecentsPressAnimation(value: String, cover: Boolean = false) = edit { it[if (cover) Keys.RECENTS_OUTER_PRESS else Keys.RECENTS_PRESS] = value }
suspend fun SettingsRepository.setRecentsGlass(value: Boolean, cover: Boolean = false) = edit { it[if (cover) Keys.RECENTS_OUTER_GLASS else Keys.RECENTS_GLASS] = value }
suspend fun SettingsRepository.setRecentsGlassBlur(value: Int, cover: Boolean = false) = edit { it[if (cover) Keys.RECENTS_OUTER_GLASS_BLUR else Keys.RECENTS_GLASS_BLUR] = value.coerceIn(0, LauncherSettings.MAX_RECENTS_GLASS_BLUR) }
suspend fun SettingsRepository.setRecentsGlassTint(value: Float, cover: Boolean = false) = edit {
    it[if (cover) Keys.RECENTS_OUTER_GLASS_TINT else Keys.RECENTS_GLASS_TINT] = value.coerceIn(LauncherSettings.MIN_RECENTS_GLASS_TINT, LauncherSettings.MAX_RECENTS_GLASS_TINT)
}
suspend fun SettingsRepository.setRecentsDragDrop(value: Boolean) = edit { it[Keys.RECENTS_DRAG_DROP] = value }
suspend fun SettingsRepository.setRecentsOverlayLabels(value: Boolean, cover: Boolean = false) = edit { it[if (cover) Keys.RECENTS_OUTER_OVERLAY_LABELS else Keys.RECENTS_OVERLAY_LABELS] = value }
suspend fun SettingsRepository.setRecentsCardIcons(value: Boolean, cover: Boolean = false) = edit { it[if (cover) Keys.RECENTS_OUTER_CARD_ICONS else Keys.RECENTS_CARD_ICONS] = value }
suspend fun SettingsRepository.setRecentsCardShadow(value: Boolean, cover: Boolean = false) = edit { it[if (cover) Keys.RECENTS_OUTER_CARD_SHADOW else Keys.RECENTS_CARD_SHADOW] = value }
suspend fun SettingsRepository.setRecentsLabelScale(value: Float, cover: Boolean = false) = edit { it[if (cover) Keys.RECENTS_OUTER_LABEL_SCALE else Keys.RECENTS_LABEL_SCALE] = value.coerceIn(LauncherSettings.MIN_RECENTS_LABEL_SCALE, LauncherSettings.MAX_RECENTS_LABEL_SCALE) }
suspend fun SettingsRepository.setRecentsMenuMotion(value: Boolean, cover: Boolean = false) = edit { it[if (cover) Keys.RECENTS_OUTER_MENU_MOTION else Keys.RECENTS_MENU_MOTION] = value }
suspend fun SettingsRepository.setRecentsScrollTicks(value: Boolean) = edit { it[Keys.RECENTS_SCROLL_TICKS] = value }
suspend fun SettingsRepository.setRecentsBtnClose(value: Boolean, cover: Boolean = false) = edit { it[if (cover) Keys.RECENTS_OUTER_BTN_CLOSE else Keys.RECENTS_BTN_CLOSE] = value }
suspend fun SettingsRepository.setRecentsBtnSplit(value: Boolean, cover: Boolean = false) = edit { it[if (cover) Keys.RECENTS_OUTER_BTN_SPLIT else Keys.RECENTS_BTN_SPLIT] = value }
suspend fun SettingsRepository.setRecentsBtnPopup(value: Boolean, cover: Boolean = false) = edit { it[if (cover) Keys.RECENTS_OUTER_BTN_POPUP else Keys.RECENTS_BTN_POPUP] = value }
suspend fun SettingsRepository.setRecentsBtnUnsplit(value: Boolean, cover: Boolean = false) = edit { it[if (cover) Keys.RECENTS_OUTER_BTN_UNSPLIT else Keys.RECENTS_BTN_UNSPLIT] = value }
suspend fun SettingsRepository.setRecentsBtnSplitView(value: Boolean, cover: Boolean = false) = edit { it[if (cover) Keys.RECENTS_OUTER_BTN_SPLIT_VIEW else Keys.RECENTS_BTN_SPLIT_VIEW] = value }
suspend fun SettingsRepository.setRecentsBtnCloseAll(value: Boolean, cover: Boolean = false) = edit { it[if (cover) Keys.RECENTS_OUTER_BTN_CLOSE_ALL else Keys.RECENTS_BTN_CLOSE_ALL] = value }
suspend fun SettingsRepository.setRecentsSwipeUpAction(value: String) = edit { it[Keys.RECENTS_SWIPE_UP] = value }
suspend fun SettingsRepository.setRecentsSwipeDownAction(value: String) = edit { it[Keys.RECENTS_SWIPE_DOWN] = value }
suspend fun SettingsRepository.setRecentsSwipeLeftAction(value: String) = edit { it[Keys.RECENTS_SWIPE_LEFT] = value }
suspend fun SettingsRepository.setRecentsSwipeRightAction(value: String) = edit { it[Keys.RECENTS_SWIPE_RIGHT] = value }
suspend fun SettingsRepository.setRecentsLongPressAction(value: String) = edit { it[Keys.RECENTS_LONG_PRESS] = value }
suspend fun SettingsRepository.setRecentsDoubleTapAction(value: String) = edit { it[Keys.RECENTS_DOUBLE_TAP] = value }
suspend fun SettingsRepository.setRecentsOuterGestures(value: Boolean) = edit { it[Keys.RECENTS_OUTER_GESTURES] = value }
suspend fun SettingsRepository.setRecentsOuterSwipeUpAction(value: String) = edit { it[Keys.RECENTS_OUTER_SWIPE_UP] = value }
suspend fun SettingsRepository.setRecentsOuterSwipeDownAction(value: String) = edit { it[Keys.RECENTS_OUTER_SWIPE_DOWN] = value }
suspend fun SettingsRepository.setRecentsOuterSwipeLeftAction(value: String) = edit { it[Keys.RECENTS_OUTER_SWIPE_LEFT] = value }
suspend fun SettingsRepository.setRecentsOuterSwipeRightAction(value: String) = edit { it[Keys.RECENTS_OUTER_SWIPE_RIGHT] = value }
suspend fun SettingsRepository.setRecentsOuterLongPressAction(value: String) = edit { it[Keys.RECENTS_OUTER_LONG_PRESS] = value }
suspend fun SettingsRepository.setRecentsOuterDoubleTapAction(value: String) = edit { it[Keys.RECENTS_OUTER_DOUBLE_TAP] = value }
suspend fun SettingsRepository.setRecentsPinned(value: Set<String>) = edit { it[Keys.RECENTS_PINNED] = value.joinToString("\n") }
suspend fun SettingsRepository.setRecentsBtnPin(value: Boolean, cover: Boolean = false) = edit { it[if (cover) Keys.RECENTS_OUTER_BTN_PIN else Keys.RECENTS_BTN_PIN] = value }
suspend fun SettingsRepository.setRecentsPinKeepAlive(value: Boolean) = edit { it[Keys.RECENTS_PIN_KEEP_ALIVE] = value }
suspend fun SettingsRepository.setRecentsOuterLook(value: Boolean) = edit { it[Keys.RECENTS_OUTER_LOOK] = value }
suspend fun SettingsRepository.setRecentsAttachedSide(value: String) = edit { it[Keys.RECENTS_ATTACHED_SIDE] = value }
suspend fun SettingsRepository.setRecentsAttachedPeek(value: Float) = edit {
    it[Keys.RECENTS_ATTACHED_PEEK] = value.coerceIn(LauncherSettings.MIN_RECENTS_ATTACHED_PEEK, LauncherSettings.MAX_RECENTS_ATTACHED_PEEK)
}
suspend fun SettingsRepository.setRecentsAttachedNative(value: Boolean) = edit { it[Keys.RECENTS_ATTACHED_NATIVE] = value }
suspend fun SettingsRepository.setRecentsSplitEngine(value: Int) = edit { it[Keys.RECENTS_SPLIT_ENGINE] = value }
suspend fun SettingsRepository.setSplitPickerColumns(value: Int) = edit {
    it[Keys.SPLIT_PICKER_COLUMNS] = value.coerceIn(SplitPickerLook.MIN_COLUMNS, SplitPickerLook.MAX_COLUMNS)
}
suspend fun SettingsRepository.setSplitPickerIconSize(value: Int) = edit {
    it[Keys.SPLIT_PICKER_ICON] = value.coerceIn(SplitPickerLook.MIN_ICON, SplitPickerLook.MAX_ICON)
}
suspend fun SettingsRepository.setSplitPickerLabels(value: Boolean) = edit { it[Keys.SPLIT_PICKER_LABELS] = value }
suspend fun SettingsRepository.setSplitPickerFolders(value: Boolean) = edit { it[Keys.SPLIT_PICKER_FOLDERS] = value }
suspend fun SettingsRepository.setSplitPickerRecents(value: String) = edit { it[Keys.SPLIT_PICKER_RECENTS] = value }
suspend fun SettingsRepository.setSplitPickerRecentsCount(value: Int) = edit {
    it[Keys.SPLIT_PICKER_RECENTS_COUNT] = value.coerceIn(SplitPickerLook.MIN_RECENTS, SplitPickerLook.MAX_RECENTS)
}
suspend fun SettingsRepository.setSplitPickerSort(value: String) = edit { it[Keys.SPLIT_PICKER_SORT] = value }
suspend fun SettingsRepository.setSplitPickerSearch(value: Boolean) = edit { it[Keys.SPLIT_PICKER_SEARCH] = value }
suspend fun SettingsRepository.setDropZone(zone: String, on: Boolean) = edit { if (zone in DropZones.ALL) it[Keys.dropZone(zone)] = on }
suspend fun SettingsRepository.setDropZoneHaptics(value: Boolean) = edit { it[Keys.DROP_ZONE_HAPTICS] = value }
suspend fun SettingsRepository.setSplitUndo(value: Boolean) = edit { it[Keys.SPLIT_UNDO] = value }
suspend fun SettingsRepository.setDividerStops(value: Boolean) = edit { it[Keys.DIVIDER_STOPS] = value }
suspend fun SettingsRepository.setSplitThreeEven(value: Boolean) = edit { it[Keys.SPLIT_THREE_EVEN] = value }
suspend fun SettingsRepository.setSplitFreeDivider(value: Boolean) = edit { it[Keys.SPLIT_FREE_DIVIDER] = value }
suspend fun SettingsRepository.setSplitDividerLock(value: String) = edit { it[Keys.SPLIT_DIVIDER_LOCK] = value }
suspend fun SettingsRepository.setSplitDividerMarks(value: Boolean) = edit { it[Keys.SPLIT_DIVIDER_MARKS] = value }
suspend fun SettingsRepository.setSplitDividerHaptics(value: Boolean) = edit { it[Keys.SPLIT_DIVIDER_HAPTICS] = value }
suspend fun SettingsRepository.setSplitKeepSize(value: Boolean) = edit { it[Keys.SPLIT_KEEP_SIZE] = value }
suspend fun SettingsRepository.setSplitRememberSize(value: Boolean) = edit { it[Keys.SPLIT_REMEMBER_SIZE] = value }
suspend fun SettingsRepository.setSplitDragHint(value: Boolean) = edit { it[Keys.SPLIT_DRAG_HINT] = value }
suspend fun SettingsRepository.setPeekFlingClose(value: Boolean) = edit { it[Keys.PEEK_FLING_CLOSE] = value }
suspend fun SettingsRepository.setSplitRatioMenu(value: Boolean) = edit { it[Keys.SPLIT_RATIO_MENU] = value }
suspend fun SettingsRepository.setSplitOpenWith(value: Boolean) = edit { it[Keys.SPLIT_OPEN_WITH] = value }
suspend fun SettingsRepository.setNotificationSplit(value: Boolean) = edit { it[Keys.NOTIFICATION_SPLIT] = value }
suspend fun SettingsRepository.setSplitPickerFavorites(value: Boolean) = edit { it[Keys.SPLIT_PICKER_FAVORITES] = value }
suspend fun SettingsRepository.setSplitPickerRecentPairs(value: Boolean) = edit { it[Keys.SPLIT_PICKER_RECENT_PAIRS] = value }
suspend fun SettingsRepository.setSplitPickerPeekToggle(value: Boolean) = edit { it[Keys.SPLIT_PICKER_PEEK_TOGGLE] = value }
suspend fun SettingsRepository.setSplitPickerLongPress(value: Boolean) = edit { it[Keys.SPLIT_PICKER_LONG_PRESS] = value }
suspend fun SettingsRepository.setSplitPickerOpenAnim(value: String) = edit {
    if (value in SplitPickerLook.OPEN_ANIMS) it[Keys.SPLIT_PICKER_OPEN_ANIM] = value
}
suspend fun SettingsRepository.setSplitPickerOpacity(value: Float) = edit {
    it[Keys.SPLIT_PICKER_OPACITY] = value.coerceIn(SplitPickerLook.MIN_OPACITY, SplitPickerLook.MAX_OPACITY)
}
