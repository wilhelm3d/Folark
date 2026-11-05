package org.arkikeskus.launcher.data

import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Split screen as System UI reports it, live: Folark's Vector module listens to WM Shell's split
 * screen (its `SplitScreenListener`) and passes on every change — an app entering or leaving a split
 * stage, the split showing or hiding — so Folark waits for the split to form (or close) instead of
 * reading Android's window list again and again. Without the module nothing arrives here, [live]
 * stays false, and [SplitCoordinator] reads the window list as before.
 */
object SplitEvents {
    /** Provider method System UI calls on Folark with each change (see RecentsHookState). */
    const val METHOD_EVENT = "split_event"
    /** Several events in one call, in order (a ParcelableArrayList of event Bundles). */
    const val EXTRA_BATCH = "batch"
    const val EXTRA_KIND = "kind"
    const val EXTRA_TASK = "task"
    const val EXTRA_STAGE = "stage"
    const val EXTRA_VISIBLE = "visible"

    /** A task moved into a split stage, or out of it ([EXTRA_STAGE] [STAGE_NONE]). */
    const val KIND_STAGE = 1
    /** The split showed or hid ([EXTRA_VISIBLE]). */
    const val KIND_VISIBLE = 2
    /** The module's listener is (again) attached; the current state follows right before this. */
    const val KIND_ATTACHED = 3

    /** WM Shell's stage for "in no split stage" (SplitScreen.STAGE_TYPE_UNDEFINED). */
    const val STAGE_NONE = -1

    /** Task → its split stage as last reported ([STAGE_NONE]: it left its stage). */
    private val _stages = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val stages: StateFlow<Map<Int, Int>> = _stages.asStateFlow()

    /** When the module's listener last attached in this Folark process (0: not yet). */
    @Volatile var attachedAt: Long = 0L
        private set

    @Volatile var lastEventAt: Long = 0L
        private set

    @Volatile var splitVisible: Boolean = false
        private set

    /** Events are arriving: waits can use them. */
    val live: Boolean get() = attachedAt > 0L

    fun onEvent(extras: Bundle) {
        @Suppress("DEPRECATION")
        val batch = extras.getParcelableArrayList<Bundle>(EXTRA_BATCH)
        if (batch != null) {
            batch.forEach { onEvent(it) }
            return
        }
        // A split stage changed: a cached window list no longer says what is open.
        ShellSplit.forget()
        lastEventAt = System.currentTimeMillis()
        when (extras.getInt(EXTRA_KIND)) {
            KIND_STAGE -> {
                val task = extras.getInt(EXTRA_TASK, -1)
                if (task > 0) _stages.update { it + (task to extras.getInt(EXTRA_STAGE, STAGE_NONE)) }
            }
            KIND_VISIBLE -> splitVisible = extras.getBoolean(EXTRA_VISIBLE)
            KIND_ATTACHED -> attachedAt = System.currentTimeMillis()
        }
    }

    /** System UI restarted: what it said before no longer holds until its listener attaches again. */
    fun onSystemUiRestart() {
        attachedAt = 0L
        splitVisible = false
        _stages.value = emptyMap()
    }

    /** Forgets what is known about [ids], before asking for a change to them (no stale answer then). */
    fun forget(ids: Collection<Int>) {
        _stages.update { it - ids.toSet() }
    }

    /** Waits up to [timeoutMs] until every task of [ids] is reported in a split stage. */
    suspend fun awaitStaged(ids: Collection<Int>, timeoutMs: Long): Boolean =
        withTimeoutOrNull(timeoutMs) { stages.first { m -> ids.all { (m[it] ?: STAGE_NONE) != STAGE_NONE } } } != null

    /** Tasks reported in a split stage right now. */
    fun stagedNow(): Set<Int> = stages.value.filterValues { it != STAGE_NONE }.keys

    /**
     * Waits up to [timeoutMs] for a task not in [known] to be reported in a split stage (a third
     * app just started from its intent, whose task id isn't known yet); that task, or null.
     */
    suspend fun awaitNewStaged(known: Set<Int>, timeoutMs: Long): Int? = withTimeoutOrNull(timeoutMs) {
        stages.first { m -> m.any { (task, stage) -> stage != STAGE_NONE && task !in known } }
            .entries.first { (task, stage) -> stage != STAGE_NONE && task !in known }.key
    }

    /** Waits up to [timeoutMs] until every task of [ids] is reported out of its split stage. */
    suspend fun awaitUnstaged(ids: Collection<Int>, timeoutMs: Long): Boolean =
        withTimeoutOrNull(timeoutMs) { stages.first { m -> ids.all { m[it] == STAGE_NONE } } } != null
}
