package org.arkikeskus.launcher.xposed

import android.content.Context
import android.net.Uri
import android.os.Bundle
import org.arkikeskus.launcher.data.SplitEvents
import org.arkikeskus.launcher.data.SplitThree
import java.util.concurrent.Executors
import org.arkikeskus.launcher.model.catching

/**
 * What SplitThreeHook tells Folark from inside System UI (split out of SplitThreeHook.kt): the
 * answer to each request, and split changes gathered for a moment and sent together. All of it
 * goes out in order and off WM Shell's own thread.
 */
internal object ToFolark {
    /** How long split events gather before they go to Folark together. */
    private const val EVENT_BATCH_MS = 24L

    /** Everything sent to Folark goes out here, in order and off WM Shell's thread. */
    val outbox = Executors.newSingleThreadScheduledExecutor()
    /** Split events waiting to go to Folark together (E4: a three-app start fires about six). */
    private val pendingEvents = ArrayList<Bundle>()
    private var flushQueued = false
    /** SplitThreeHook's log, for an answer that didn't arrive. */
    @Volatile var log: (String) -> Unit = {}

    /** Passes a split change on to Folark (its RecentsHookState), off WM Shell's thread. */
    fun send(context: Context, extras: Bundle) {
        synchronized(pendingEvents) {
            pendingEvents += extras
            if (flushQueued) return
            flushQueued = true
        }
        outbox.schedule({ flush(context) }, EVENT_BATCH_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    /** Everything [send] collected in the last [EVENT_BATCH_MS], in order, in one call. */
    private fun flush(context: Context) {
        val batch = synchronized(pendingEvents) {
            flushQueued = false
            ArrayList(pendingEvents).also { pendingEvents.clear() }
        }
        if (batch.isEmpty()) return
        val extras = if (batch.size == 1) batch[0] else Bundle().apply { putParcelableArrayList(SplitEvents.EXTRA_BATCH, batch) }
        catching {
            context.contentResolver.call(Uri.parse("content://${RecentsHook.STATE_AUTHORITY}"), SplitEvents.METHOD_EVENT, null, extras)
        }
    }

    fun reply(context: Context, request: String, ok: Boolean, value: Int? = null, detail: String? = null) {
        // On the same ordered outbox the events use: one thread, answers never race each other.
        outbox.execute {
            catching {
                context.contentResolver.call(
                    Uri.parse("content://${RecentsHook.STATE_AUTHORITY}"), SplitThree.METHOD_RESULT, request,
                    Bundle().apply {
                        putBoolean(SplitThree.EXTRA_OK, ok)
                        if (value != null) putInt(SplitThree.EXTRA_VALUE, value)
                        if (detail != null) putString(SplitThree.EXTRA_DETAIL, detail)
                    },
                )
            }.onFailure { log("split hook: answer for $request didn't reach Folark: $it") }
        }
    }
}
