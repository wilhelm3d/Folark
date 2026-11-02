package org.arkikeskus.launcher.model

import android.content.ComponentName

/**
 * A saved scene: two apps (and an optional third) that open together, in [layout]. Called "app
 * pairs" in older versions; pairs saved then read back with the layout they had (half and half,
 * or peek).
 */
data class AppPair(
    val id: String,
    val name: String,
    val app1Key: String,
    val app1Package: String,
    val app1ClassName: String,
    val app1UserSerial: Long,
    val app2Key: String,
    val app2Package: String,
    val app2ClassName: String,
    val app2UserSerial: Long,
    // Optional third app, opened in a floating window on top of the split pair ("" = none).
    val app3Key: String = "",
    val app3Package: String = "",
    val app3ClassName: String = "",
    val app3UserSerial: Long = 0L,
    /** Opens as Android's peek view: the first app big, the second peeking in from the edge. */
    val peek: Boolean = false,
    /** How the two apps share the screen: one of LAYOUT_* (the first app's share). */
    val layout: String = if (peek) LAYOUT_PEEK else LAYOUT_EVEN,
    /** Where the third app goes: one of THIRD_* ([THIRD_AUTO] follows Settings ▸ Multi-window). */
    val thirdMode: String = THIRD_AUTO,
    /** On the cover screen: the split as on the inner screen, or the first app alone (COVER_*). */
    val coverMode: String = COVER_SPLIT,
) {
    val hasThirdApp: Boolean get() = app3Package.isNotEmpty() && app3ClassName.isNotEmpty()
    val app3ComponentName: ComponentName get() = ComponentName(app3Package, app3ClassName)
    val app1ComponentName: ComponentName get() = ComponentName(app1Package, app1ClassName)
    val app2ComponentName: ComponentName get() = ComponentName(app2Package, app2ClassName)

    /** The layout, peek kept in step with [peek] for pairs made by older code. */
    val effectiveLayout: String get() = when {
        layout in LAYOUTS && layout != LAYOUT_EVEN -> layout
        peek -> LAYOUT_PEEK
        else -> LAYOUT_EVEN
    }

    companion object {
        const val LAYOUT_EVEN = "even"
        /** The first app takes a third of the screen, the second two thirds. */
        const val LAYOUT_FIRST_THIRD = "first_third"
        /** The first app takes two thirds. */
        const val LAYOUT_FIRST_TWO_THIRDS = "first_two_thirds"
        /** Android's peek view: the first app big, the second peeking in from the edge. */
        const val LAYOUT_PEEK = "peek"
        val LAYOUTS = listOf(LAYOUT_EVEN, LAYOUT_FIRST_THIRD, LAYOUT_FIRST_TWO_THIRDS, LAYOUT_PEEK)

        const val THIRD_AUTO = "auto"
        /** The third app as Android's third pane (Vector module), else floating. */
        const val THIRD_PANE = "pane"
        /** The third app always in a floating window over the two. */
        const val THIRD_FLOAT = "float"
        val THIRD_MODES = listOf(THIRD_AUTO, THIRD_PANE, THIRD_FLOAT)

        const val COVER_SPLIT = "split"
        const val COVER_FIRST = "first"
        val COVER_MODES = listOf(COVER_SPLIT, COVER_FIRST)
    }
}
