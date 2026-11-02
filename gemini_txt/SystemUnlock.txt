package org.arkikeskus.launcher.xposed

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The lock screen's exit, as SystemUI reports it to Folark (through Folark's Vector module, see
 * [UnlockHook] and RecentsHookState). Lives in Folark's own process; no Xposed classes here, so it
 * is safe to use from anywhere in the app.
 */
object SystemUnlock {
    /** SystemUI started the lock screen's exit animation at [atUptime] (SystemClock.uptimeMillis),
     *  uncovering [topPackage]; [bySwipe] = the finger is lifting the lock screen (not a canned one). */
    data class Exit(val atUptime: Long, val topPackage: String?, val bySwipe: Boolean)

    private val _exits = MutableSharedFlow<Exit>(extraBufferCapacity = 4)
    val exits: SharedFlow<Exit> = _exits.asSharedFlow()

    /** SystemUI has reported at least one unlock since Folark started: the module is live there. */
    @Volatile
    var live = false
        private set

    fun report(exit: Exit) {
        live = true
        _exits.tryEmit(exit)
    }

    const val METHOD_UNLOCK = "unlock"
    const val EXTRA_AT = "at"
    const val EXTRA_TOP = "top"
    const val EXTRA_SWIPE = "swipe"
}
