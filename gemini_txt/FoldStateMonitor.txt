package org.arkikeskus.launcher.launcher.system

import android.app.Activity
import android.util.Log
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.arkikeskus.launcher.model.FoldState
import org.arkikeskus.launcher.model.ScreenType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors the device posture using [WindowInfoTracker] to distinguish between the folded (outer screen)
 * and unfolded (inner screen) states, allowing the launcher to maintain separate profiles.
 */
@Singleton
class FoldStateMonitor @Inject constructor() {

    private companion object {
        const val TAG = "FoldStateMonitor"

        /** The large-screen breakpoint: an unfolded book foldable is at least this wide (in dp). */
        const val INNER_MIN_SHORT_SIDE_DP = 600f
    }

    /**
     * Returns a [StateFlow] emitting the current [FoldState] for the given [activity].
     * Physical display switches (OUTER <-> INNER) emit immediately without delay.
     * Transient posture micro-vibrations on the same physical display (e.g. OPENED <-> HALF_OPENED) are debounced.
     */
    fun foldStateFlow(activity: Activity, scope: CoroutineScope): StateFlow<FoldState> {
        val initialFoldState = computeFoldState(activity, null)

        return WindowInfoTracker.getOrCreate(activity)
            .windowLayoutInfo(activity)
            .scan(initialFoldState) { previousFoldState, layoutInfo ->
                val foldingFeature = layoutInfo.displayFeatures
                    .filterIsInstance<FoldingFeature>()
                    .firstOrNull()

                computeFoldState(foldingFeature, previousFoldState, activity)
            }
            .distinctUntilChanged()
            .transformPosturesWithInstantDisplaySwitch()
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = initialFoldState
            )
    }

    /**
     * Determines the [ScreenType] for a given [FoldingFeature].
     * Evaluates to [ScreenType.INNER] when running on the inner display or folding feature state is FLAT/HALF_OPENED.
     * Otherwise evaluates to [ScreenType.OUTER].
     */
    fun determineScreenType(
        foldingFeature: FoldingFeature?,
        previousScreenType: ScreenType? = null,
        activity: Activity? = null
    ): ScreenType {
        val previousFoldState = when (previousScreenType) {
            ScreenType.INNER -> FoldState.OPENED
            ScreenType.OUTER -> FoldState.CLOSED
            null -> null
        }
        return computeFoldState(foldingFeature, previousFoldState, activity).screenType
    }

    /**
     * Computes the [FoldState] based on [activity] and optional [foldingFeature].
     * Evaluates immediately using [isInnerDisplay] when [activity] is provided.
     */
    fun computeFoldState(
        activity: Activity,
        foldingFeature: FoldingFeature? = null
    ): FoldState = computeFoldState(
        foldingFeature = foldingFeature,
        previousFoldState = null,
        activity = activity
    )

    /**
     * Computes the [FoldState] from the [foldingFeature] and the window metrics of [activity].
     *
     * A reported [FoldingFeature] is the authoritative signal for the inner display: only the
     * folding panel has a hinge, the outer screen never reports one. That is what makes clamshell
     * devices work — their inner panel is an ordinary tall phone screen (e.g. 1080 px wide) that no
     * size threshold can tell apart from a book foldable's outer screen. The size check stays as the
     * fallback for the first frame (before the first layout info arrives) and for devices that
     * report no folding feature at all.
     */
    fun computeFoldState(
        foldingFeature: FoldingFeature?,
        previousFoldState: FoldState? = null,
        activity: Activity? = null
    ): FoldState {
        val foldingState = foldingFeature?.state

        // 1. Explicit folding feature reported by WindowInfoTracker
        if (foldingFeature != null) {
            if (activity != null) {
                val bounds = activity.windowManager.currentWindowMetrics.bounds
                rememberInnerSize(bounds.width(), bounds.height())
            }
            return when (foldingState) {
                FoldingFeature.State.HALF_OPENED -> FoldState.HALF_OPENED
                FoldingFeature.State.FLAT -> FoldState.OPENED
                else -> FoldState.CLOSED
            }
        }

        // 2. If foldingFeature becomes null when previously OPENED/HALF_OPENED -> device was CLOSED!
        if (previousFoldState == FoldState.OPENED || previousFoldState == FoldState.HALF_OPENED) {
            return FoldState.CLOSED
        }

        // 3. Fallback for static displays (tablets vs phone outer screens) when no folding feature exists
        val isInner = if (activity != null) isInnerDisplay(activity) else false
        val result = if (isInner) FoldState.OPENED else FoldState.CLOSED

        if (result != previousFoldState) {
            Log.d(TAG, "fold state $previousFoldState -> $result (feature=$foldingState, isInner=$isInner)")
        }
        return result
    }

    /**
     * Whether [activity]'s window is on the inner (folding) display, judged by configuration:
     * at least [INNER_MIN_SHORT_SIDE_DP] smallest width in DP.
     */
    fun isInnerDisplay(activity: Activity?): Boolean {
        if (activity == null) return false
        val config = activity.resources?.configuration ?: return false
        if (config.smallestScreenWidthDp >= INNER_MIN_SHORT_SIDE_DP.toInt()) return true

        val density = activity.resources?.displayMetrics?.density ?: 0f
        if (density > 0f) {
            val bounds = activity.windowManager.currentWindowMetrics.bounds
            val width = bounds.width()
            val height = bounds.height()
            if (width > 0 && height > 0) {
                val shortSideDp = minOf(width, height) / density
                val longSideDp = maxOf(width, height) / density
                val aspectRatio = longSideDp / shortSideDp
                if (shortSideDp >= INNER_MIN_SHORT_SIDE_DP || (shortSideDp >= 500f && aspectRatio < 1.4f)) {
                    return true
                }
            }
        }
        return false
    }

    /** Window sizes (orientation-independent) that were seen with a folding feature, so the next
     *  cold start on that panel picks the inner profile on its very first frame. */
    private val knownInnerSizes: MutableSet<Long> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    private fun rememberInnerSize(width: Int, height: Int) {
        if (width > 0 && height > 0) knownInnerSizes.add(sizeKey(width, height))
    }

    private fun sizeKey(width: Int, height: Int): Long =
        (maxOf(width, height).toLong() shl 32) or minOf(width, height).toLong()

    private fun Flow<FoldState>.transformPosturesWithInstantDisplaySwitch(): Flow<FoldState> = channelFlow {
        var lastEmittedState: FoldState? = null
        var postureDebounceJob: Job? = null

        collect { incomingState ->
            val lastState = lastEmittedState
            if (lastState == null) {
                lastEmittedState = incomingState
                send(incomingState)
                return@collect
            }

            val prevScreenType = if (lastState == FoldState.CLOSED) ScreenType.OUTER else ScreenType.INNER
            val newScreenType = if (incomingState == FoldState.CLOSED) ScreenType.OUTER else ScreenType.INNER

            if (newScreenType != prevScreenType) {
                // Instant physical display switch: emit immediately without delay
                postureDebounceJob?.cancel()
                postureDebounceJob = null
                lastEmittedState = incomingState
                send(incomingState)
            } else if (incomingState != lastState) {
                // Transient posture change on same physical display: debounce 300ms
                postureDebounceJob?.cancel()
                postureDebounceJob = launch {
                    delay(300L)
                    lastEmittedState = incomingState
                    send(incomingState)
                }
            }
        }
    }
}
