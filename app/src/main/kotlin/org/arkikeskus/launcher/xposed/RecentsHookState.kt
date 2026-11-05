package org.arkikeskus.launcher.xposed

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.data.SplitEvents
import org.arkikeskus.launcher.data.SplitThree
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
        if (method == SystemUnlock.METHOD_UNLOCK) return onUnlock(extras)
        if (method == SplitThree.METHOD_READY || method == SplitThree.METHOD_RESULT || method == SplitEvents.METHOD_EVENT) {
            // Only SystemUI (Folark's module there) answers split requests and reports split changes.
            if (callingPackage != RecentsHook.SYSTEMUI) return null
            val context = context ?: return null
            when (method) {
                SplitThree.METHOD_READY -> if (SplitThree.onReady(context)) {
                    // System UI restarted right after a split request: the crash guard paused them.
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, org.arkikeskus.launcher.R.string.split_native_paused, Toast.LENGTH_LONG).show()
                    }
                }
                SplitThree.METHOD_RESULT -> SplitThree.onResult(
                    context, arg, extras?.getBoolean(SplitThree.EXTRA_OK) == true,
                    extras?.takeIf { it.containsKey(SplitThree.EXTRA_VALUE) }?.getInt(SplitThree.EXTRA_VALUE),
                    extras?.getString(SplitThree.EXTRA_DETAIL),
                )
                else -> extras?.let { SplitEvents.onEvent(it) }
            }
            return null
        }
        if (method == StatusBarPaddingHook.METHOD) return statusBarPadding()
        if (method == DividerStopsHook.METHOD) return dividerStops()
        if (method != "state") return null
        val context = context ?: return null
        // Only the launchers in the module's scope ask this (anyone else could fake "module active").
        val allowed = runCatching { context.resources.getStringArray(org.arkikeskus.launcher.R.array.xposed_scope).toSet() }
            .getOrDefault(emptySet())
        if (callingPackage !in allowed) return null
        // Record that the module is alive first, so Folark shows it active even if reading the
        // settings below is briefly not ready (the launcher may call this very early).
        runCatching {
            val edit = context.getSharedPreferences(PREFS, 0).edit()
                .putLong(SEEN_AT, System.currentTimeMillis())
            // Which gesture hooks this launcher build allowed (RecentsHook.HOOK_*); shown in Settings.
            if (extras?.containsKey(RecentsHook.EXTRA_COMPAT) == true) {
                edit.putInt(COMPAT, extras.getInt(RecentsHook.EXTRA_COMPAT))
            }
            edit.apply()
        }
        val settings = runCatching {
            runBlocking {
                EntryPointAccessors.fromApplication(context, Deps::class.java)
                    .settingsRepository().settings(ScreenType.INNER).first()
            }
        }.getOrNull()
        return Bundle().apply {
            putBoolean("replace", settings?.recents?.enabled == true && settings.recents.replaceSystem)
            putBoolean("search", settings?.recents?.navHandleOpensSearch == true)
        }
    }

    /** System UI (the module's [StatusBarPaddingHook]) asks how much room to keep at the status bar's ends. */
    private fun statusBarPadding(): Bundle? {
        if (callingPackage != RecentsHook.SYSTEMUI) return null
        val context = context ?: return null
        val s = runCatching {
            runBlocking {
                EntryPointAccessors.fromApplication(context, Deps::class.java)
                    .settingsRepository().settings(ScreenType.INNER).first().statusBar
            }
        }.getOrNull() ?: return null
        return Bundle().apply {
            putBoolean(StatusBarPaddingHook.EXTRA_ON, s.systemPadding)
            putInt(StatusBarPaddingHook.EXTRA_INNER, s.systemPaddingInner)
            putInt(StatusBarPaddingHook.EXTRA_COVER, s.systemPaddingCover)
        }
    }

    /** For the module's [DividerStopsHook] (System UI only): whether the divider stops at 30/50/70. */
    private fun dividerStops(): Bundle? {
        if (callingPackage != RecentsHook.SYSTEMUI) return null
        val context = context ?: return null
        val recents = runCatching {
            runBlocking {
                EntryPointAccessors.fromApplication(context, Deps::class.java)
                    .settingsRepository().settings(ScreenType.INNER).first().recents
            }
        }.getOrNull()
        return Bundle().apply {
            putBoolean(DividerStopsHook.EXTRA_ON, recents?.dividerStops ?: true)
            putBoolean(DividerStopsHook.EXTRA_FREE, recents?.freeDivider ?: true)
        }
    }

    /** SystemUI (the module's [UnlockHook]) says the lock screen has started to leave. */
    private fun onUnlock(extras: Bundle?): Bundle? {
        if (callingPackage != RecentsHook.SYSTEMUI || extras == null) return null
        val context = context ?: return null
        SystemUnlock.report(
            SystemUnlock.Exit(
                atUptime = extras.getLong(SystemUnlock.EXTRA_AT),
                topPackage = extras.getString(SystemUnlock.EXTRA_TOP),
                bySwipe = extras.getBoolean(SystemUnlock.EXTRA_SWIPE),
            ),
        )
        runCatching {
            context.getSharedPreferences(PREFS, 0).edit().putLong(UNLOCK_SEEN_AT, System.currentTimeMillis()).apply()
        }
        return null
    }

    override fun onCreate(): Boolean = true
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<out String>?): Int = 0

    companion object {
        /** Watched by the module in the launcher: changes when Replace Android's Recents or the
         *  gesture handle's search switch does, so it asks again (see LauncherApplication). */
        val STATE_URI: Uri = Uri.parse("content://${RecentsHook.STATE_AUTHORITY}/state")

        const val PREFS = "folark_recents_hook"
        const val SEEN_AT = "seen_at"
        const val COMPAT = "compat"
        const val UNLOCK_SEEN_AT = "unlock_seen_at"
    }
}
