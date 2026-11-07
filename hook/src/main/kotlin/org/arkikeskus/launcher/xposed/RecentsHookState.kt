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
import org.arkikeskus.launcher.data.SplitOutcome
import org.arkikeskus.launcher.data.SplitThree
import org.arkikeskus.launcher.model.ScreenType

/**
 * Answers Folark's Vector module ([RecentsHook]) in the system launcher: is Settings ▸ Recents ▸
 * Replace Android's Recents on? Gives out that one yes/no, nothing else, and notes when the module
 * last asked (shown in Settings as "module active").
 *
 * Every method is dispatched through [handlers] and checks who is calling before anything else.
 * The settings it hands out come from [HookStateCache] (kept current by the app's own settings
 * stream); only when the app has not produced a snapshot yet does a question read DataStore once,
 * briefly, on the binder thread.
 */
class RecentsHookState : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun settingsRepository(): SettingsRepository
    }

    /** One handler per call method, all behind the caller checks they each need. */
    private val handlers: Map<String, (arg: String?, extras: Bundle?) -> Bundle?> by lazy {
        buildMap {
            put("state") { _, extras -> state(extras) }
            put(SystemUnlock.METHOD_UNLOCK) { _, extras -> onUnlock(extras) }
            for (method in listOf(SplitThree.METHOD_READY, SplitThree.METHOD_RESULT, SplitEvents.METHOD_EVENT)) {
                put(method) { arg, extras -> onSplit(method, arg, extras) }
            }
            put(StatusBarPaddingHook.METHOD) { _, _ -> statusBarPadding() }
            put(DividerStopsHook.METHOD) { _, _ -> dividerStops() }
        }
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? =
        handlers[method]?.invoke(arg, extras)

    /** The launcher's two Recents questions (and a note that the module is alive). */
    private fun state(extras: Bundle?): Bundle? {
        val context = context ?: return null
        // Only the launchers in the module's scope ask this (anyone else could fake "module active").
        val allowed = runCatching { context.resources.getStringArray(org.arkikeskus.launcher.hook.R.array.xposed_scope).toSet() }
            .getOrDefault(emptySet())
        if (callingPackage !in allowed) return null
        // Record that the module is alive first, so Folark shows it active even if the settings
        // below are briefly not ready (the launcher may call this very early).
        runCatching {
            val edit = context.getSharedPreferences(PREFS, 0).edit()
                .putLong(SEEN_AT, System.currentTimeMillis())
            // Which gesture hooks this launcher build allowed (RecentsHook.HOOK_*); shown in Settings.
            if (extras?.containsKey(RecentsHook.EXTRA_COMPAT) == true) {
                edit.putInt(COMPAT, extras.getInt(RecentsHook.EXTRA_COMPAT))
            }
            edit.apply()
        }
        ensureCache()
        return Bundle().apply {
            putBoolean("replace", HookStateCache.replaceRecents ?: false)
            putBoolean("search", HookStateCache.navHandleSearch ?: false)
        }
    }

    /** System UI only: split requests and reports (the module's [SplitThreeHook] there). */
    private fun onSplit(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (callingPackage != RecentsHook.SYSTEMUI) return null
        val context = context ?: return null
        when (method) {
            SplitThree.METHOD_READY -> if (SplitThree.onReady(context)) {
                // System UI restarted right after a split request: the crash guard paused them.
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, org.arkikeskus.launcher.hook.R.string.split_native_paused, Toast.LENGTH_LONG).show()
                }
            }
            SplitThree.METHOD_RESULT -> SplitThree.onResult(context, arg, SplitOutcome.from(extras))
            else -> extras?.let { SplitEvents.onEvent(it) }
        }
        return null
    }

    /** System UI (the module's [StatusBarPaddingHook]) asks how much room to keep at the status bar's ends. */
    private fun statusBarPadding(): Bundle? {
        if (callingPackage != RecentsHook.SYSTEMUI) return null
        ensureCache()
        val on = HookStateCache.statusBarOn ?: return null
        return Bundle().apply {
            putBoolean(StatusBarPaddingHook.EXTRA_ON, on)
            putInt(StatusBarPaddingHook.EXTRA_INNER, HookStateCache.statusBarInner ?: 0)
            putInt(StatusBarPaddingHook.EXTRA_COVER, HookStateCache.statusBarCover ?: 0)
        }
    }

    /** For the module's [DividerStopsHook] (System UI only): whether the divider stops at 30/50/70. */
    private fun dividerStops(): Bundle? {
        if (callingPackage != RecentsHook.SYSTEMUI) return null
        ensureCache()
        return Bundle().apply {
            putBoolean(DividerStopsHook.EXTRA_ON, HookStateCache.dividerOn ?: true)
            putBoolean(DividerStopsHook.EXTRA_FREE, HookStateCache.dividerFree ?: true)
            putString(DividerStopsHook.EXTRA_LOCK, HookStateCache.dividerLock ?: "off")
            putBoolean(DividerStopsHook.EXTRA_MARKS, HookStateCache.dividerMarks ?: true)
            putBoolean(DividerStopsHook.EXTRA_HAPTICS, HookStateCache.dividerHaptics ?: true)
            putBoolean(DividerStopsHook.EXTRA_KEEP, HookStateCache.keepSplitSize ?: true)
            putBoolean(DividerStopsHook.EXTRA_PEEK_FLING, HookStateCache.peekFlingClose ?: false)
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

    /**
     * The cache has every answer as soon as the app's settings stream has run once. Before that
     * (the app may have been started by the module's question itself), one short blocking read
     * fills it; afterwards the stream keeps it current and no binder thread ever waits.
     */
    private fun ensureCache() {
        if (HookStateCache.replaceRecents != null) return
        val context = context ?: return
        runCatching {
            runBlocking { kotlinx.coroutines.withTimeoutOrNull(PROVIDER_WAIT_MS) {
                HookStateCache.update(
                    EntryPointAccessors.fromApplication(context, Deps::class.java)
                        .settingsRepository().settings(ScreenType.INNER).first(),
                )
            } }
        }
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

/** How long System UI's first question may wait for Folark's settings on the binder thread (E5). */
private const val PROVIDER_WAIT_MS = 400L
