package org.arkikeskus.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

/** Motorola's pop-up windows (package com.motorola.freeform), on phones that have them. */
object MotoPopup {
    const val PACKAGE = "com.motorola.freeform"
    private val GESTURE_URI: Uri = Uri.parse("content://com.motorola.freeform.settings/touch_gesture_enable")
    private const val GESTURE_COLUMN = "touch_gesture_enable"

    fun isAvailable(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    }.getOrDefault(false)

    /** Whether Motorola's pop-up gesture is on; null if Motorola's setting can't be read. */
    fun gestureEnabled(context: Context): Boolean? = runCatching {
        context.contentResolver.query(GESTURE_URI, null, null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(GESTURE_COLUMN)
            if (column < 0) return@use null
            var value: String? = null
            while (cursor.moveToNext()) value = cursor.getString(column)
            value?.let { it == "1" }
        }
    }.getOrNull()

    /** Motorola's pop-up settings screen (its newer one first), or the app's info page. */
    fun settingsIntent(context: Context): Intent {
        val pm = context.packageManager
        val intent = SETTINGS_ACTIONS
            .map { Intent(it).setPackage(PACKAGE) }
            .firstOrNull { candidate ->
                runCatching { pm.resolveActivity(candidate, PackageManager.MATCH_DEFAULT_ONLY) != null }.getOrDefault(false)
            }
            ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + PACKAGE))
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private val SETTINGS_ACTIONS = listOf(
        "com.motorola.freeform.v3.ACTION_SETTINGS",
        "com.motorola.freeform.ACTION_SETTINGS",
    )
}
