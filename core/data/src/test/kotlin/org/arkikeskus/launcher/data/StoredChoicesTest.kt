package org.arkikeskus.launcher.data

import com.google.common.truth.Truth.assertThat
import org.arkikeskus.launcher.model.AppOpenAnimation
import org.arkikeskus.launcher.model.AzPosition
import org.arkikeskus.launcher.model.BatterySaverMode
import org.arkikeskus.launcher.model.ClockMinuteColor
import org.arkikeskus.launcher.model.ClockStyle
import org.arkikeskus.launcher.model.CountStyle
import org.arkikeskus.launcher.model.DividerLock
import org.arkikeskus.launcher.model.DockAlignment
import org.arkikeskus.launcher.model.DotColor
import org.arkikeskus.launcher.model.DotPosition
import org.arkikeskus.launcher.model.DotShape
import org.arkikeskus.launcher.model.DrawerAlignment
import org.arkikeskus.launcher.model.DrawerHandle
import org.arkikeskus.launcher.model.DrawerLayout
import org.arkikeskus.launcher.model.DrawerStyle
import org.arkikeskus.launcher.model.DrawerTheme
import org.arkikeskus.launcher.model.FoldAction
import org.arkikeskus.launcher.model.FoldTransition
import org.arkikeskus.launcher.model.FolderAnimation
import org.arkikeskus.launcher.model.FolderPreview
import org.arkikeskus.launcher.model.FolderShape
import org.arkikeskus.launcher.model.FolderTheme
import org.arkikeskus.launcher.model.FontChoice
import org.arkikeskus.launcher.model.HapticsStrength
import org.arkikeskus.launcher.model.HomeGesture
import org.arkikeskus.launcher.model.IconShape
import org.arkikeskus.launcher.model.IconSwipe
import org.arkikeskus.launcher.model.LaunchGlow
import org.arkikeskus.launcher.model.LiveEffect
import org.arkikeskus.launcher.model.LiveHingeStyle
import org.arkikeskus.launcher.model.MinusOne
import org.arkikeskus.launcher.model.NudgeEffect
import org.arkikeskus.launcher.model.PageEffect
import org.arkikeskus.launcher.model.PeoplePrivacy
import org.arkikeskus.launcher.model.PickerOpen
import org.arkikeskus.launcher.model.PickerRecents
import org.arkikeskus.launcher.model.PickerSort
import org.arkikeskus.launcher.model.PopupTheme
import org.arkikeskus.launcher.model.PressEffect
import org.arkikeskus.launcher.model.RecentsAction
import org.arkikeskus.launcher.model.RecentsAttachedSide
import org.arkikeskus.launcher.model.RecentsCardLayout
import org.arkikeskus.launcher.model.RecentsEnter
import org.arkikeskus.launcher.model.RecentsSelect
import org.arkikeskus.launcher.model.RecentsSort
import org.arkikeskus.launcher.model.RecentsTheme
import org.arkikeskus.launcher.model.RingMode
import org.arkikeskus.launcher.model.RootMode
import org.arkikeskus.launcher.model.SearchEngine
import org.arkikeskus.launcher.model.SearchPosition
import org.arkikeskus.launcher.model.SettingsStyle
import org.arkikeskus.launcher.model.SizeChoice
import org.arkikeskus.launcher.model.StackIndicator
import org.arkikeskus.launcher.model.StoredChoice
import org.arkikeskus.launcher.model.TaskbarAnim
import org.arkikeskus.launcher.model.TaskbarDivider
import org.arkikeskus.launcher.model.TaskbarHandleStyle
import org.arkikeskus.launcher.model.TaskbarHandles
import org.arkikeskus.launcher.model.TaskbarMediaPosition
import org.arkikeskus.launcher.model.TaskbarMediaShape
import org.arkikeskus.launcher.model.TaskbarMediaTap
import org.arkikeskus.launcher.model.TaskbarRecentsButton
import org.arkikeskus.launcher.model.TaskbarSource
import org.arkikeskus.launcher.model.TaskbarSplitSide
import org.arkikeskus.launcher.model.TaskbarTheme
import org.arkikeskus.launcher.model.ThemePreset
import org.arkikeskus.launcher.model.ThemedStyle
import org.arkikeskus.launcher.model.UnlockStyle
import org.arkikeskus.launcher.model.WidgetTheme
import org.arkikeskus.launcher.model.storedChoice
import org.junit.Test

/**
 * The settings with a fixed set of choices are enums in the code, but DataStore keeps the same text
 * for each choice as before they were enums. These are those texts, copied from the old constants:
 * settings saved by an older Folark, and backups, must read back as the same choice.
 */
class StoredChoicesTest {

    private val stored: Map<List<StoredChoice>, List<String>> = mapOf(
        AppOpenAnimation.entries.toList() to listOf("system", "icon", "reveal", "zoom", "slide", "fade", "pop", "unfold", "none"),
        AzPosition.entries.toList() to listOf("VERTICAL_RIGHT", "VERTICAL_LEFT", "HORIZONTAL_TOP", "HORIZONTAL_BOTTOM"),
        BatterySaverMode.entries.toList() to listOf("off", "auto", "on"),
        ClockMinuteColor.entries.toList() to listOf("style", "white", "light_grey", "grey", "dark_grey", "black"),
        ClockStyle.entries.toList() to listOf(
            "classic", "duo", "light", "dot", "stacked", "glass_pill", "outline", "vertical_stack", "neumorphic",
            "nothing_ndot",
        ),
        CountStyle.entries.toList() to listOf("number", "dot", "none"),
        DividerLock.entries.toList() to listOf("off", "two", "three", "all"),
        DockAlignment.entries.toList() to listOf("center", "left", "right", "floating"),
        DotColor.entries.toList() to listOf("red", "accent", "white"),
        DotPosition.entries.toList() to listOf("top_end", "top_start", "bottom_end", "bottom_start"),
        DotShape.entries.toList() to listOf("circle", "squircle", "square"),
        DrawerAlignment.entries.toList() to listOf("left", "center", "right"),
        DrawerHandle.entries.toList() to listOf("bar", "arrow"),
        DrawerLayout.entries.toList() to listOf("grid", "list"),
        DrawerStyle.entries.toList() to listOf("standard_grid", "one_ui", "nothing_os", "moto", "vertical", "horizontal"),
        DrawerTheme.entries.toList() to listOf(
            "system", "material_you", "default_dark", "obsidian_black", "light", "accent_tint", "monochrome", "custom",
        ),
        FoldAction.entries.toList() to listOf("page_0", "lock", "search", "drawer", "stay"),
        FoldTransition.entries.toList() to listOf(
            "morph_scale_fade", "book_unfold_sweep", "aquamorphic_ripple", "crossfade", "scale", "slide", "live_hinge",
        ),
        FolderAnimation.entries.toList() to listOf("sheet", "zoom", "fade", "none"),
        FolderPreview.entries.toList() to listOf("grid2", "grid3", "cover"),
        FolderShape.entries.toList() to listOf("icon", "squircle", "circle", "square"),
        FolderTheme.entries.toList() to listOf("glass", "dark", "light", "accent"),
        FontChoice.entries.toList() to listOf(
            "default", "ndot", "ntype", "space_grotesk", "serif", "monospace", "condensed", "light", "medium", "casual",
            "smallcaps", "cursive", "moto_rookery", "moto_work_sans", "moto_exo2", "moto_roboto_slab", "same",
        ),
        HapticsStrength.entries.toList() to listOf("light", "medium", "strong"),
        HomeGesture.entries.toList() to listOf(
            "notifications", "drawer", "search", "lock", "none", "recents", "moto_note", "moto_vault", "moto_ready_for",
            "moto_games", "moto_weather", "moto_styles",
        ),
        IconShape.entries.toList() to listOf(
            "system", "circle", "squircle", "rounded", "teardrop", "pebble", "square", "arch", "hexagon", "diamond",
            "cookie",
        ),
        IconSwipe.entries.toList() to listOf("none", "menu", "floating", "split"),
        LaunchGlow.entries.toList() to listOf("app", "accent", "white", "off"),
        LiveEffect.entries.toList() to listOf("glow", "shimmer", "progress", "orbit", "breathe", "aurora", "still"),
        LiveHingeStyle.entries.toList() to listOf("pages", "water", "ink", "fade", "drop", "doors", "portal", "zoom"),
        MinusOne.entries.toList() to listOf("none", "google", "smart_feed"),
        NudgeEffect.entries.toList() to listOf("bounce", "wiggle", "pulse", "glow", "ripple", "jelly", "tint"),
        PageEffect.entries.toList() to listOf("slide", "fade", "zoom", "cube", "stack"),
        PeoplePrivacy.entries.toList() to listOf("all", "sender", "count"),
        PopupTheme.entries.toList() to listOf("classic", "material", "dark", "light", "glass"),
        PressEffect.entries.toList() to listOf("shrink", "bounce", "glow", "ripple", "tilt", "jelly", "none"),
        RecentsAction.entries.toList() to listOf(
            "none", "menu", "close", "split", "popup", "unsplit", "pin", "info", "open", "peek", "split3",
            "close_others", "close_all",
        ),
        RecentsAttachedSide.entries.toList() to listOf("left", "right"),
        RecentsCardLayout.entries.toList() to listOf("header", "overlay"),
        RecentsEnter.entries.toList() to listOf("rise", "zoom", "cascade", "flip", "fan", "focus", "wave", "swing", "drop", "none"),
        RecentsSelect.entries.toList() to listOf("pop", "ring", "lift", "tilt", "glow", "jiggle", "none"),
        RecentsSort.entries.toList() to listOf("recent", "name"),
        RecentsTheme.entries.toList() to listOf(
            "folark", "ipad", "carousel", "deck", "compact", "switcher", "stack", "arc", "portrait", "spotlight",
            "gallery",
        ),
        RingMode.entries.toList() to listOf("off", "progress", "all"),
        SearchEngine.entries.toList() to listOf("system", "google", "duckduckgo", "bing", "startpage"),
        SearchPosition.entries.toList() to listOf("top", "bottom"),
        SettingsStyle.entries.toList() to listOf("standard", "studio"),
        SizeChoice.entries.toList() to listOf("small", "medium", "large"),
        StackIndicator.entries.toList() to listOf("dots", "bar", "none"),
        TaskbarAnim.entries.toList() to listOf("slide", "fade", "scale", "rise", "bounce", "flip", "unfold", "pop"),
        TaskbarDivider.entries.toList() to listOf("line", "bold", "dot", "gap", "none"),
        TaskbarHandleStyle.entries.toList() to listOf("line", "arrow", "double", "dots", "text"),
        TaskbarHandles.entries.toList() to listOf("both", "left", "right", "center"),
        TaskbarMediaPosition.entries.toList() to listOf("start", "middle", "end"),
        TaskbarMediaShape.entries.toList() to listOf("circle", "rounded"),
        TaskbarMediaTap.entries.toList() to listOf("open", "play_pause"),
        TaskbarRecentsButton.entries.toList() to listOf("off", "system", "desktop", "folark"),
        TaskbarSource.entries.toList() to listOf("folark", "system"),
        TaskbarSplitSide.entries.toList() to listOf("left", "right"),
        TaskbarTheme.entries.toList() to listOf("clear", "frosted", "dark", "light", "accent", "glass", "amoled", "neon"),
        ThemePreset.entries.toList() to listOf("glass", "dark", "light", "mono"),
        ThemedStyle.entries.toList() to listOf("classic", "inverted", "glass", "duotone", "own", "soft"),
        UnlockStyle.entries.toList() to listOf("display", "zoom", "rise", "bloom", "sweep", "focus"),
        WidgetTheme.entries.toList() to listOf("default", "dark", "light", "accent"),
        PickerRecents.entries.toList() to listOf("off", "icons", "previews"),
        PickerSort.entries.toList() to listOf("name", "usage"),
        PickerOpen.entries.toList() to listOf("zoom", "fade", "none"),
        RootMode.entries.toList() to listOf("off", "fallback", "always"),
    )

    @Test
    fun `each choice is stored as the text it always had`() {
        for ((choices, texts) in stored) {
            assertThat(choices.map { it.stored }).containsExactlyElementsIn(texts).inOrder()
        }
    }

    @Test
    fun `each stored text reads back as its own choice, anything else as the default`() {
        for (choice in AppOpenAnimation.entries) assertThat(storedChoice(choice.stored, AppOpenAnimation.NONE)).isEqualTo(choice)
        assertThat(storedChoice(null, AppOpenAnimation.SYSTEM)).isEqualTo(AppOpenAnimation.SYSTEM)
        assertThat(storedChoice("no such choice", AppOpenAnimation.NONE)).isEqualTo(AppOpenAnimation.NONE)
        for (choice in AzPosition.entries) assertThat(storedChoice(choice.stored, AzPosition.HORIZONTAL_BOTTOM)).isEqualTo(choice)
        assertThat(storedChoice(null, AzPosition.VERTICAL_RIGHT)).isEqualTo(AzPosition.VERTICAL_RIGHT)
        assertThat(storedChoice("no such choice", AzPosition.HORIZONTAL_BOTTOM)).isEqualTo(AzPosition.HORIZONTAL_BOTTOM)
        for (choice in BatterySaverMode.entries) assertThat(storedChoice(choice.stored, BatterySaverMode.ON)).isEqualTo(choice)
        assertThat(storedChoice(null, BatterySaverMode.OFF)).isEqualTo(BatterySaverMode.OFF)
        assertThat(storedChoice("no such choice", BatterySaverMode.ON)).isEqualTo(BatterySaverMode.ON)
        for (choice in ClockMinuteColor.entries) assertThat(storedChoice(choice.stored, ClockMinuteColor.BLACK)).isEqualTo(choice)
        assertThat(storedChoice(null, ClockMinuteColor.STYLE)).isEqualTo(ClockMinuteColor.STYLE)
        assertThat(storedChoice("no such choice", ClockMinuteColor.BLACK)).isEqualTo(ClockMinuteColor.BLACK)
        for (choice in ClockStyle.entries) assertThat(storedChoice(choice.stored, ClockStyle.NOTHING_NDOT)).isEqualTo(choice)
        assertThat(storedChoice(null, ClockStyle.CLASSIC)).isEqualTo(ClockStyle.CLASSIC)
        assertThat(storedChoice("no such choice", ClockStyle.NOTHING_NDOT)).isEqualTo(ClockStyle.NOTHING_NDOT)
        for (choice in CountStyle.entries) assertThat(storedChoice(choice.stored, CountStyle.NONE)).isEqualTo(choice)
        assertThat(storedChoice(null, CountStyle.NUMBER)).isEqualTo(CountStyle.NUMBER)
        assertThat(storedChoice("no such choice", CountStyle.NONE)).isEqualTo(CountStyle.NONE)
        for (choice in DividerLock.entries) assertThat(storedChoice(choice.stored, DividerLock.ALL)).isEqualTo(choice)
        assertThat(storedChoice(null, DividerLock.OFF)).isEqualTo(DividerLock.OFF)
        assertThat(storedChoice("no such choice", DividerLock.ALL)).isEqualTo(DividerLock.ALL)
        for (choice in DockAlignment.entries) assertThat(storedChoice(choice.stored, DockAlignment.FLOATING)).isEqualTo(choice)
        assertThat(storedChoice(null, DockAlignment.CENTER)).isEqualTo(DockAlignment.CENTER)
        assertThat(storedChoice("no such choice", DockAlignment.FLOATING)).isEqualTo(DockAlignment.FLOATING)
        for (choice in DotColor.entries) assertThat(storedChoice(choice.stored, DotColor.WHITE)).isEqualTo(choice)
        assertThat(storedChoice(null, DotColor.RED)).isEqualTo(DotColor.RED)
        assertThat(storedChoice("no such choice", DotColor.WHITE)).isEqualTo(DotColor.WHITE)
        for (choice in DotPosition.entries) assertThat(storedChoice(choice.stored, DotPosition.BOTTOM_START)).isEqualTo(choice)
        assertThat(storedChoice(null, DotPosition.TOP_END)).isEqualTo(DotPosition.TOP_END)
        assertThat(storedChoice("no such choice", DotPosition.BOTTOM_START)).isEqualTo(DotPosition.BOTTOM_START)
        for (choice in DotShape.entries) assertThat(storedChoice(choice.stored, DotShape.SQUARE)).isEqualTo(choice)
        assertThat(storedChoice(null, DotShape.CIRCLE)).isEqualTo(DotShape.CIRCLE)
        assertThat(storedChoice("no such choice", DotShape.SQUARE)).isEqualTo(DotShape.SQUARE)
        for (choice in DrawerAlignment.entries) assertThat(storedChoice(choice.stored, DrawerAlignment.RIGHT)).isEqualTo(choice)
        assertThat(storedChoice(null, DrawerAlignment.LEFT)).isEqualTo(DrawerAlignment.LEFT)
        assertThat(storedChoice("no such choice", DrawerAlignment.RIGHT)).isEqualTo(DrawerAlignment.RIGHT)
        for (choice in DrawerHandle.entries) assertThat(storedChoice(choice.stored, DrawerHandle.ARROW)).isEqualTo(choice)
        assertThat(storedChoice(null, DrawerHandle.BAR)).isEqualTo(DrawerHandle.BAR)
        assertThat(storedChoice("no such choice", DrawerHandle.ARROW)).isEqualTo(DrawerHandle.ARROW)
        for (choice in DrawerLayout.entries) assertThat(storedChoice(choice.stored, DrawerLayout.LIST)).isEqualTo(choice)
        assertThat(storedChoice(null, DrawerLayout.GRID)).isEqualTo(DrawerLayout.GRID)
        assertThat(storedChoice("no such choice", DrawerLayout.LIST)).isEqualTo(DrawerLayout.LIST)
        for (choice in DrawerStyle.entries) assertThat(storedChoice(choice.stored, DrawerStyle.HORIZONTAL)).isEqualTo(choice)
        assertThat(storedChoice(null, DrawerStyle.STANDARD_GRID)).isEqualTo(DrawerStyle.STANDARD_GRID)
        assertThat(storedChoice("no such choice", DrawerStyle.HORIZONTAL)).isEqualTo(DrawerStyle.HORIZONTAL)
        for (choice in DrawerTheme.entries) assertThat(storedChoice(choice.stored, DrawerTheme.CUSTOM)).isEqualTo(choice)
        assertThat(storedChoice(null, DrawerTheme.SYSTEM)).isEqualTo(DrawerTheme.SYSTEM)
        assertThat(storedChoice("no such choice", DrawerTheme.CUSTOM)).isEqualTo(DrawerTheme.CUSTOM)
        for (choice in FoldAction.entries) assertThat(storedChoice(choice.stored, FoldAction.STAY)).isEqualTo(choice)
        assertThat(storedChoice(null, FoldAction.PAGE_0)).isEqualTo(FoldAction.PAGE_0)
        assertThat(storedChoice("no such choice", FoldAction.STAY)).isEqualTo(FoldAction.STAY)
        for (choice in FoldTransition.entries) assertThat(storedChoice(choice.stored, FoldTransition.LIVE_HINGE)).isEqualTo(choice)
        assertThat(storedChoice(null, FoldTransition.MORPH_SCALE_FADE)).isEqualTo(FoldTransition.MORPH_SCALE_FADE)
        assertThat(storedChoice("no such choice", FoldTransition.LIVE_HINGE)).isEqualTo(FoldTransition.LIVE_HINGE)
        for (choice in FolderAnimation.entries) assertThat(storedChoice(choice.stored, FolderAnimation.NONE)).isEqualTo(choice)
        assertThat(storedChoice(null, FolderAnimation.SHEET)).isEqualTo(FolderAnimation.SHEET)
        assertThat(storedChoice("no such choice", FolderAnimation.NONE)).isEqualTo(FolderAnimation.NONE)
        for (choice in FolderPreview.entries) assertThat(storedChoice(choice.stored, FolderPreview.COVER)).isEqualTo(choice)
        assertThat(storedChoice(null, FolderPreview.GRID_2)).isEqualTo(FolderPreview.GRID_2)
        assertThat(storedChoice("no such choice", FolderPreview.COVER)).isEqualTo(FolderPreview.COVER)
        for (choice in FolderShape.entries) assertThat(storedChoice(choice.stored, FolderShape.SQUARE)).isEqualTo(choice)
        assertThat(storedChoice(null, FolderShape.SQUIRCLE)).isEqualTo(FolderShape.SQUIRCLE)
        assertThat(storedChoice("no such choice", FolderShape.SQUARE)).isEqualTo(FolderShape.SQUARE)
        for (choice in FolderTheme.entries) assertThat(storedChoice(choice.stored, FolderTheme.ACCENT)).isEqualTo(choice)
        assertThat(storedChoice(null, FolderTheme.GLASS)).isEqualTo(FolderTheme.GLASS)
        assertThat(storedChoice("no such choice", FolderTheme.ACCENT)).isEqualTo(FolderTheme.ACCENT)
        for (choice in FontChoice.entries) assertThat(storedChoice(choice.stored, FontChoice.SAME)).isEqualTo(choice)
        assertThat(storedChoice(null, FontChoice.DEFAULT)).isEqualTo(FontChoice.DEFAULT)
        assertThat(storedChoice("no such choice", FontChoice.SAME)).isEqualTo(FontChoice.SAME)
        for (choice in HapticsStrength.entries) assertThat(storedChoice(choice.stored, HapticsStrength.STRONG)).isEqualTo(choice)
        assertThat(storedChoice(null, HapticsStrength.LIGHT)).isEqualTo(HapticsStrength.LIGHT)
        assertThat(storedChoice("no such choice", HapticsStrength.STRONG)).isEqualTo(HapticsStrength.STRONG)
        for (choice in HomeGesture.entries) assertThat(storedChoice(choice.stored, HomeGesture.MOTO_STYLES)).isEqualTo(choice)
        assertThat(storedChoice(null, HomeGesture.NOTIFICATIONS)).isEqualTo(HomeGesture.NOTIFICATIONS)
        assertThat(storedChoice("no such choice", HomeGesture.MOTO_STYLES)).isEqualTo(HomeGesture.MOTO_STYLES)
        for (choice in IconShape.entries) assertThat(storedChoice(choice.stored, IconShape.COOKIE)).isEqualTo(choice)
        assertThat(storedChoice(null, IconShape.SYSTEM)).isEqualTo(IconShape.SYSTEM)
        assertThat(storedChoice("no such choice", IconShape.COOKIE)).isEqualTo(IconShape.COOKIE)
        for (choice in IconSwipe.entries) assertThat(storedChoice(choice.stored, IconSwipe.SPLIT)).isEqualTo(choice)
        assertThat(storedChoice(null, IconSwipe.NONE)).isEqualTo(IconSwipe.NONE)
        assertThat(storedChoice("no such choice", IconSwipe.SPLIT)).isEqualTo(IconSwipe.SPLIT)
        for (choice in LaunchGlow.entries) assertThat(storedChoice(choice.stored, LaunchGlow.OFF)).isEqualTo(choice)
        assertThat(storedChoice(null, LaunchGlow.APP)).isEqualTo(LaunchGlow.APP)
        assertThat(storedChoice("no such choice", LaunchGlow.OFF)).isEqualTo(LaunchGlow.OFF)
        for (choice in LiveEffect.entries) assertThat(storedChoice(choice.stored, LiveEffect.STILL)).isEqualTo(choice)
        assertThat(storedChoice(null, LiveEffect.GLOW)).isEqualTo(LiveEffect.GLOW)
        assertThat(storedChoice("no such choice", LiveEffect.STILL)).isEqualTo(LiveEffect.STILL)
        for (choice in LiveHingeStyle.entries) assertThat(storedChoice(choice.stored, LiveHingeStyle.ZOOM)).isEqualTo(choice)
        assertThat(storedChoice(null, LiveHingeStyle.PAGES)).isEqualTo(LiveHingeStyle.PAGES)
        assertThat(storedChoice("no such choice", LiveHingeStyle.ZOOM)).isEqualTo(LiveHingeStyle.ZOOM)
        for (choice in MinusOne.entries) assertThat(storedChoice(choice.stored, MinusOne.SMART_FEED)).isEqualTo(choice)
        assertThat(storedChoice(null, MinusOne.NONE)).isEqualTo(MinusOne.NONE)
        assertThat(storedChoice("no such choice", MinusOne.SMART_FEED)).isEqualTo(MinusOne.SMART_FEED)
        for (choice in NudgeEffect.entries) assertThat(storedChoice(choice.stored, NudgeEffect.TINT)).isEqualTo(choice)
        assertThat(storedChoice(null, NudgeEffect.BOUNCE)).isEqualTo(NudgeEffect.BOUNCE)
        assertThat(storedChoice("no such choice", NudgeEffect.TINT)).isEqualTo(NudgeEffect.TINT)
        for (choice in PageEffect.entries) assertThat(storedChoice(choice.stored, PageEffect.STACK)).isEqualTo(choice)
        assertThat(storedChoice(null, PageEffect.SLIDE)).isEqualTo(PageEffect.SLIDE)
        assertThat(storedChoice("no such choice", PageEffect.STACK)).isEqualTo(PageEffect.STACK)
        for (choice in PeoplePrivacy.entries) assertThat(storedChoice(choice.stored, PeoplePrivacy.COUNT)).isEqualTo(choice)
        assertThat(storedChoice(null, PeoplePrivacy.ALL)).isEqualTo(PeoplePrivacy.ALL)
        assertThat(storedChoice("no such choice", PeoplePrivacy.COUNT)).isEqualTo(PeoplePrivacy.COUNT)
        for (choice in PopupTheme.entries) assertThat(storedChoice(choice.stored, PopupTheme.GLASS)).isEqualTo(choice)
        assertThat(storedChoice(null, PopupTheme.CLASSIC)).isEqualTo(PopupTheme.CLASSIC)
        assertThat(storedChoice("no such choice", PopupTheme.GLASS)).isEqualTo(PopupTheme.GLASS)
        for (choice in PressEffect.entries) assertThat(storedChoice(choice.stored, PressEffect.NONE)).isEqualTo(choice)
        assertThat(storedChoice(null, PressEffect.SHRINK)).isEqualTo(PressEffect.SHRINK)
        assertThat(storedChoice("no such choice", PressEffect.NONE)).isEqualTo(PressEffect.NONE)
        for (choice in RecentsAction.entries) assertThat(storedChoice(choice.stored, RecentsAction.CLOSE_ALL)).isEqualTo(choice)
        assertThat(storedChoice(null, RecentsAction.NONE)).isEqualTo(RecentsAction.NONE)
        assertThat(storedChoice("no such choice", RecentsAction.CLOSE_ALL)).isEqualTo(RecentsAction.CLOSE_ALL)
        for (choice in RecentsAttachedSide.entries) assertThat(storedChoice(choice.stored, RecentsAttachedSide.RIGHT)).isEqualTo(choice)
        assertThat(storedChoice(null, RecentsAttachedSide.LEFT)).isEqualTo(RecentsAttachedSide.LEFT)
        assertThat(storedChoice("no such choice", RecentsAttachedSide.RIGHT)).isEqualTo(RecentsAttachedSide.RIGHT)
        for (choice in RecentsCardLayout.entries) assertThat(storedChoice(choice.stored, RecentsCardLayout.OVERLAY)).isEqualTo(choice)
        assertThat(storedChoice(null, RecentsCardLayout.HEADER)).isEqualTo(RecentsCardLayout.HEADER)
        assertThat(storedChoice("no such choice", RecentsCardLayout.OVERLAY)).isEqualTo(RecentsCardLayout.OVERLAY)
        for (choice in RecentsEnter.entries) assertThat(storedChoice(choice.stored, RecentsEnter.NONE)).isEqualTo(choice)
        assertThat(storedChoice(null, RecentsEnter.RISE)).isEqualTo(RecentsEnter.RISE)
        assertThat(storedChoice("no such choice", RecentsEnter.NONE)).isEqualTo(RecentsEnter.NONE)
        for (choice in RecentsSelect.entries) assertThat(storedChoice(choice.stored, RecentsSelect.NONE)).isEqualTo(choice)
        assertThat(storedChoice(null, RecentsSelect.POP)).isEqualTo(RecentsSelect.POP)
        assertThat(storedChoice("no such choice", RecentsSelect.NONE)).isEqualTo(RecentsSelect.NONE)
        for (choice in RecentsSort.entries) assertThat(storedChoice(choice.stored, RecentsSort.NAME)).isEqualTo(choice)
        assertThat(storedChoice(null, RecentsSort.RECENT)).isEqualTo(RecentsSort.RECENT)
        assertThat(storedChoice("no such choice", RecentsSort.NAME)).isEqualTo(RecentsSort.NAME)
        for (choice in RecentsTheme.entries) assertThat(storedChoice(choice.stored, RecentsTheme.GALLERY)).isEqualTo(choice)
        assertThat(storedChoice(null, RecentsTheme.FOLARK)).isEqualTo(RecentsTheme.FOLARK)
        assertThat(storedChoice("no such choice", RecentsTheme.GALLERY)).isEqualTo(RecentsTheme.GALLERY)
        for (choice in RingMode.entries) assertThat(storedChoice(choice.stored, RingMode.ALL)).isEqualTo(choice)
        assertThat(storedChoice(null, RingMode.OFF)).isEqualTo(RingMode.OFF)
        assertThat(storedChoice("no such choice", RingMode.ALL)).isEqualTo(RingMode.ALL)
        for (choice in SearchEngine.entries) assertThat(storedChoice(choice.stored, SearchEngine.STARTPAGE)).isEqualTo(choice)
        assertThat(storedChoice(null, SearchEngine.SYSTEM)).isEqualTo(SearchEngine.SYSTEM)
        assertThat(storedChoice("no such choice", SearchEngine.STARTPAGE)).isEqualTo(SearchEngine.STARTPAGE)
        for (choice in SearchPosition.entries) assertThat(storedChoice(choice.stored, SearchPosition.BOTTOM)).isEqualTo(choice)
        assertThat(storedChoice(null, SearchPosition.TOP)).isEqualTo(SearchPosition.TOP)
        assertThat(storedChoice("no such choice", SearchPosition.BOTTOM)).isEqualTo(SearchPosition.BOTTOM)
        for (choice in SettingsStyle.entries) assertThat(storedChoice(choice.stored, SettingsStyle.STUDIO)).isEqualTo(choice)
        assertThat(storedChoice(null, SettingsStyle.STANDARD)).isEqualTo(SettingsStyle.STANDARD)
        assertThat(storedChoice("no such choice", SettingsStyle.STUDIO)).isEqualTo(SettingsStyle.STUDIO)
        for (choice in SizeChoice.entries) assertThat(storedChoice(choice.stored, SizeChoice.LARGE)).isEqualTo(choice)
        assertThat(storedChoice(null, SizeChoice.SMALL)).isEqualTo(SizeChoice.SMALL)
        assertThat(storedChoice("no such choice", SizeChoice.LARGE)).isEqualTo(SizeChoice.LARGE)
        for (choice in StackIndicator.entries) assertThat(storedChoice(choice.stored, StackIndicator.NONE)).isEqualTo(choice)
        assertThat(storedChoice(null, StackIndicator.DOTS)).isEqualTo(StackIndicator.DOTS)
        assertThat(storedChoice("no such choice", StackIndicator.NONE)).isEqualTo(StackIndicator.NONE)
        for (choice in TaskbarAnim.entries) assertThat(storedChoice(choice.stored, TaskbarAnim.POP)).isEqualTo(choice)
        assertThat(storedChoice(null, TaskbarAnim.SLIDE)).isEqualTo(TaskbarAnim.SLIDE)
        assertThat(storedChoice("no such choice", TaskbarAnim.POP)).isEqualTo(TaskbarAnim.POP)
        for (choice in TaskbarDivider.entries) assertThat(storedChoice(choice.stored, TaskbarDivider.NONE)).isEqualTo(choice)
        assertThat(storedChoice(null, TaskbarDivider.LINE)).isEqualTo(TaskbarDivider.LINE)
        assertThat(storedChoice("no such choice", TaskbarDivider.NONE)).isEqualTo(TaskbarDivider.NONE)
        for (choice in TaskbarHandleStyle.entries) assertThat(storedChoice(choice.stored, TaskbarHandleStyle.TEXT)).isEqualTo(choice)
        assertThat(storedChoice(null, TaskbarHandleStyle.LINE)).isEqualTo(TaskbarHandleStyle.LINE)
        assertThat(storedChoice("no such choice", TaskbarHandleStyle.TEXT)).isEqualTo(TaskbarHandleStyle.TEXT)
        for (choice in TaskbarHandles.entries) assertThat(storedChoice(choice.stored, TaskbarHandles.CENTER)).isEqualTo(choice)
        assertThat(storedChoice(null, TaskbarHandles.BOTH)).isEqualTo(TaskbarHandles.BOTH)
        assertThat(storedChoice("no such choice", TaskbarHandles.CENTER)).isEqualTo(TaskbarHandles.CENTER)
        for (choice in TaskbarMediaPosition.entries) assertThat(storedChoice(choice.stored, TaskbarMediaPosition.END)).isEqualTo(choice)
        assertThat(storedChoice(null, TaskbarMediaPosition.START)).isEqualTo(TaskbarMediaPosition.START)
        assertThat(storedChoice("no such choice", TaskbarMediaPosition.END)).isEqualTo(TaskbarMediaPosition.END)
        for (choice in TaskbarMediaShape.entries) assertThat(storedChoice(choice.stored, TaskbarMediaShape.ROUNDED)).isEqualTo(choice)
        assertThat(storedChoice(null, TaskbarMediaShape.CIRCLE)).isEqualTo(TaskbarMediaShape.CIRCLE)
        assertThat(storedChoice("no such choice", TaskbarMediaShape.ROUNDED)).isEqualTo(TaskbarMediaShape.ROUNDED)
        for (choice in TaskbarMediaTap.entries) assertThat(storedChoice(choice.stored, TaskbarMediaTap.PLAY_PAUSE)).isEqualTo(choice)
        assertThat(storedChoice(null, TaskbarMediaTap.OPEN)).isEqualTo(TaskbarMediaTap.OPEN)
        assertThat(storedChoice("no such choice", TaskbarMediaTap.PLAY_PAUSE)).isEqualTo(TaskbarMediaTap.PLAY_PAUSE)
        for (choice in TaskbarRecentsButton.entries) assertThat(storedChoice(choice.stored, TaskbarRecentsButton.FOLARK)).isEqualTo(choice)
        assertThat(storedChoice(null, TaskbarRecentsButton.OFF)).isEqualTo(TaskbarRecentsButton.OFF)
        assertThat(storedChoice("no such choice", TaskbarRecentsButton.FOLARK)).isEqualTo(TaskbarRecentsButton.FOLARK)
        for (choice in TaskbarSource.entries) assertThat(storedChoice(choice.stored, TaskbarSource.SYSTEM)).isEqualTo(choice)
        assertThat(storedChoice(null, TaskbarSource.FOLARK)).isEqualTo(TaskbarSource.FOLARK)
        assertThat(storedChoice("no such choice", TaskbarSource.SYSTEM)).isEqualTo(TaskbarSource.SYSTEM)
        for (choice in TaskbarSplitSide.entries) assertThat(storedChoice(choice.stored, TaskbarSplitSide.RIGHT)).isEqualTo(choice)
        assertThat(storedChoice(null, TaskbarSplitSide.LEFT)).isEqualTo(TaskbarSplitSide.LEFT)
        assertThat(storedChoice("no such choice", TaskbarSplitSide.RIGHT)).isEqualTo(TaskbarSplitSide.RIGHT)
        for (choice in TaskbarTheme.entries) assertThat(storedChoice(choice.stored, TaskbarTheme.NEON)).isEqualTo(choice)
        assertThat(storedChoice(null, TaskbarTheme.CLEAR)).isEqualTo(TaskbarTheme.CLEAR)
        assertThat(storedChoice("no such choice", TaskbarTheme.NEON)).isEqualTo(TaskbarTheme.NEON)
        for (choice in ThemePreset.entries) assertThat(storedChoice(choice.stored, ThemePreset.MONO)).isEqualTo(choice)
        assertThat(storedChoice(null, ThemePreset.GLASS)).isEqualTo(ThemePreset.GLASS)
        assertThat(storedChoice("no such choice", ThemePreset.MONO)).isEqualTo(ThemePreset.MONO)
        for (choice in ThemedStyle.entries) assertThat(storedChoice(choice.stored, ThemedStyle.SOFT)).isEqualTo(choice)
        assertThat(storedChoice(null, ThemedStyle.CLASSIC)).isEqualTo(ThemedStyle.CLASSIC)
        assertThat(storedChoice("no such choice", ThemedStyle.SOFT)).isEqualTo(ThemedStyle.SOFT)
        for (choice in UnlockStyle.entries) assertThat(storedChoice(choice.stored, UnlockStyle.FOCUS)).isEqualTo(choice)
        assertThat(storedChoice(null, UnlockStyle.DISPLAY)).isEqualTo(UnlockStyle.DISPLAY)
        assertThat(storedChoice("no such choice", UnlockStyle.FOCUS)).isEqualTo(UnlockStyle.FOCUS)
        for (choice in WidgetTheme.entries) assertThat(storedChoice(choice.stored, WidgetTheme.ACCENT)).isEqualTo(choice)
        assertThat(storedChoice(null, WidgetTheme.DEFAULT)).isEqualTo(WidgetTheme.DEFAULT)
        assertThat(storedChoice("no such choice", WidgetTheme.ACCENT)).isEqualTo(WidgetTheme.ACCENT)
        for (choice in PickerRecents.entries) assertThat(storedChoice(choice.stored, PickerRecents.ICONS)).isEqualTo(choice)
        assertThat(storedChoice("no such choice", PickerRecents.ICONS)).isEqualTo(PickerRecents.ICONS)
        for (choice in PickerSort.entries) assertThat(storedChoice(choice.stored, PickerSort.NAME)).isEqualTo(choice)
        assertThat(storedChoice("no such choice", PickerSort.NAME)).isEqualTo(PickerSort.NAME)
        for (choice in PickerOpen.entries) assertThat(storedChoice(choice.stored, PickerOpen.ZOOM)).isEqualTo(choice)
        assertThat(storedChoice("no such choice", PickerOpen.ZOOM)).isEqualTo(PickerOpen.ZOOM)
        for (choice in RootMode.entries) assertThat(storedChoice(choice.stored, RootMode.FALLBACK)).isEqualTo(choice)
        assertThat(storedChoice(null, RootMode.FALLBACK)).isEqualTo(RootMode.FALLBACK)
    }
}
