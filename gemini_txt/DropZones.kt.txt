package org.arkikeskus.launcher.model

import androidx.compose.runtime.Immutable

/**
 * Where an app can be dropped while it is dragged (Recents cards, taskbar icons, and the drop bar of
 * the home screen, dock and drawer): each of the six zones on or off, and a haptic tick on entering
 * one. Settings ▸ Recents ▸ Split screen ▸ Drop zones. With a zone off, its part of the screen
 * falls to the zone around it (peek off: the top-centre opens full screen), or to nothing.
 */
@Immutable
data class DropZones(
    /** Left / right half: the app there, Folark's split picker in the other half. */
    val left: Boolean = true,
    val right: Boolean = true,
    /** The top: full screen (on a split: the app joins it as a third pane). */
    val top: Boolean = true,
    /** The bottom: a floating pop-up window. */
    val bottom: Boolean = true,
    /** Top-centre: peek view. */
    val peek: Boolean = true,
    /** Bottom-centre: three-app split (the picker chooses two more apps). */
    val three: Boolean = true,
    /** A short tick when the dragged app enters a zone. */
    val haptics: Boolean = true,
) {
    fun isOn(zone: String): Boolean = when (zone) {
        LEFT -> left
        RIGHT -> right
        TOP -> top
        BOTTOM -> bottom
        PEEK -> peek
        THREE -> three
        else -> false
    }

    fun with(zone: String, on: Boolean): DropZones = when (zone) {
        LEFT -> copy(left = on)
        RIGHT -> copy(right = on)
        TOP -> copy(top = on)
        BOTTOM -> copy(bottom = on)
        PEEK -> copy(peek = on)
        THREE -> copy(three = on)
        else -> this
    }

    companion object {
        const val LEFT = "left"
        const val RIGHT = "right"
        const val TOP = "top"
        const val BOTTOM = "bottom"
        const val PEEK = "peek"
        const val THREE = "three"
        val ALL = listOf(LEFT, RIGHT, TOP, BOTTOM, PEEK, THREE)
    }
}
