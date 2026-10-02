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
        val screenType = computeFoldState(foldingFeature, previousFoldState, activity).screenType
        Log.d(
            "FolarkFoldDiagnostics",
            "determineScreenType: foldingFeature=$foldingFeature, state=${foldingFeature?.state}, activity=$activity, screenType=$screenType"
        )
        return screenType
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
     * Computes the [FoldState] based on [foldingFeature] and physical display window metrics from [activity].
     */
    fun computeFoldState(
        foldingFeature: FoldingFeature?,
        previousFoldState: FoldState? = null,
        activity: Activity? = null
    ): FoldState {
        val bounds = activity?.windowManager?.currentWindowMetrics?.bounds
        val maxDimension = if (bounds != null) maxOf(bounds.width(), bounds.height()) else null
        val isInner = isInnerDisplay(activity)
        val foldingState = foldingFeature?.state

        val result = if (activity != null) {
            if (!isInner) {
                FoldState.CLOSED
            } else {
                when (foldingState) {
                    FoldingFeature.State.HALF_OPENED -> FoldState.HALF_OPENED
                    else -> FoldState.OPENED
                }
            }
        } else {
            when (foldingState) {
                FoldingFeature.State.HALF_OPENED -> FoldState.HALF_OPENED
                FoldingFeature.State.FLAT -> FoldState.OPENED
                else -> FoldState.CLOSED
            }
        }

        Log.d(
            "FolarkFoldDiagnostics",
            "computeFoldState: activity=$activity, bounds=${bounds?.let { "${it.width()}x${it.height()}" }}, maxDimension=$maxDimension, isInnerDisplay=$isInner, foldingFeature=$foldingFeature, foldingFeatureState=$foldingState, computedFoldState=$result, screenType=${result.screenType}"
        )

        return result
    }

    /**
     * Validates whether the given [activity] is currently displayed on the inner main display
     * based on physical window dimensions.
     */
    fun isInnerDisplay(activity: Activity?): Boolean {
        if (activity == null) {
            Log.d("FolarkFoldDiagnostics", "isInnerDisplay: activity is null -> result=false")
            return false
        }
        val bounds = activity.windowManager.currentWindowMetrics.bounds
        // On device ZY22MPZ92C:
        // Inner display width = 2232px (> 1800px)
        // Outer cover display width = 1080px (<= 1800px)
        val isInner = bounds.width() > 1800 || minOf(bounds.width(), bounds.height()) > 1500
        Log.d(
            "FolarkFoldDiagnostics",
            "isInnerDisplay: activity=$activity, bounds=${bounds.width()}x${bounds.height()}, result=$isInner"
        )
        return isInner
    }

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
