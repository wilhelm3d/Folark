package org.arkikeskus.launcher.xposed

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.model.ScreenType

/**
 * Answers Folark's Vector module ([RecentsHook]) in the system launcher: is Settings ▸ Recents ▸
 * Replace Android's Recents on? Gives out that one yes/no, nothing else, and notes when the module
 * last asked (shown in Settings as "module active").
 */
class RecentsHookState : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun settingsRepository(): SettingsRepository
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != "state") return null
        val context = context ?: return null
        // Record that the module is alive first, so Folark shows it active even if reading the
        // settings below is briefly not ready (the launcher may call this very early).
        runCatching {
            context.getSharedPreferences(PREFS, 0).edit().putLong(SEEN_AT, System.currentTimeMillis()).apply()
        }
        val settings = runCatching {
            runBlocking {
                EntryPointAccessors.fromApplication(context, Deps::class.java)
                    .settingsRepository().settings(ScreenType.INNER).first()
            }
        }.getOrNull()
        return Bundle().apply {
            putBoolean("replace", settings?.recentsEnabled == true && settings.recentsReplaceSystem)
            putBoolean("search", settings?.navHandleOpensSearch == true)
        }
    }

    override fun onCreate(): Boolean = true
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<out String>?): Int = 0

    companion object {
        const val PREFS = "folark_recents_hook"
        const val SEEN_AT = "seen_at"
    }
}
