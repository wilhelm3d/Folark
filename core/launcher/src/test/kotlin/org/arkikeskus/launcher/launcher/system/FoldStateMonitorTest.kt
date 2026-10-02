package org.arkikeskus.launcher.launcher.system

import android.app.Activity
import android.graphics.Rect
import androidx.window.layout.FoldingFeature
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.arkikeskus.launcher.model.FoldState
import org.arkikeskus.launcher.model.ScreenType
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class FoldStateMonitorTest {

    private class FakeFoldingFeature(
        override val state: FoldingFeature.State,
        override val isSeparating: Boolean = false,
        override val occlusionType: FoldingFeature.OcclusionType = FoldingFeature.OcclusionType.NONE,
        override val orientation: FoldingFeature.Orientation = FoldingFeature.Orientation.HORIZONTAL,
        override val bounds: Rect = Rect(0, 0, 1080, 10),
    ) : FoldingFeature

    @Test
    fun `initialFoldState defaults to CLOSED and OUTER when no folding feature present`() = runTest {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()

        val monitor = FoldStateMonitor()
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val flow = monitor.foldStateFlow(activity, TestScope(testDispatcher))

        val foldState = flow.value
        val screenType = foldState.screenType

        assertThat(foldState).isEqualTo(FoldState.CLOSED)
        assertThat(screenType).isEqualTo(ScreenType.OUTER)
    }

    @Test
    fun `computeFoldState returns OPENED when folding feature state is FLAT`() {
        val monitor = FoldStateMonitor()
        val feature = FakeFoldingFeature(FoldingFeature.State.FLAT)

        val foldState = monitor.computeFoldState(feature)

        assertThat(foldState).isEqualTo(FoldState.OPENED)
    }

    @Test
    fun `computeFoldState returns HALF_OPENED when folding feature state is HALF_OPENED`() {
        val monitor = FoldStateMonitor()
        val feature = FakeFoldingFeature(FoldingFeature.State.HALF_OPENED)

        val foldState = monitor.computeFoldState(feature)

        assertThat(foldState).isEqualTo(FoldState.HALF_OPENED)
    }

    @Test
    fun `computeFoldState returns CLOSED when folding feature is null`() {
        val monitor = FoldStateMonitor()

        val foldState = monitor.computeFoldState(null)

        assertThat(foldState).isEqualTo(FoldState.CLOSED)
    }

    @Test
    fun `screenType returns INNER for OPENED foldState`() {
        val screenType = FoldState.OPENED.screenType

        assertThat(screenType).isEqualTo(ScreenType.INNER)
    }

    @Test
    fun `screenType returns INNER for HALF_OPENED foldState`() {
        val screenType = FoldState.HALF_OPENED.screenType

        assertThat(screenType).isEqualTo(ScreenType.INNER)
    }

    @Test
    fun `screenType returns OUTER for CLOSED foldState`() {
        val screenType = FoldState.CLOSED.screenType

        assertThat(screenType).isEqualTo(ScreenType.OUTER)
    }

    @Test
    fun `determineScreenType returns INNER when folding feature state is FLAT or HALF_OPENED`() {
        val monitor = FoldStateMonitor()

        val flatType = monitor.determineScreenType(FakeFoldingFeature(FoldingFeature.State.FLAT))
        val halfType = monitor.determineScreenType(FakeFoldingFeature(FoldingFeature.State.HALF_OPENED))

        assertThat(flatType).isEqualTo(ScreenType.INNER)
        assertThat(halfType).isEqualTo(ScreenType.INNER)
    }

    @Test
    fun `determineScreenType returns OUTER when folding feature is null`() {
        val monitor = FoldStateMonitor()

        val screenType = monitor.determineScreenType(foldingFeature = null)

        assertThat(screenType).isEqualTo(ScreenType.OUTER)
    }

    @Test
    fun `computeFoldState returns CLOSED when folding feature is null even if previousFoldState was OPENED`() {
        val monitor = FoldStateMonitor()

        val foldState = monitor.computeFoldState(
            foldingFeature = null,
            previousFoldState = FoldState.OPENED
        )

        assertThat(foldState).isEqualTo(FoldState.CLOSED)
        assertThat(foldState.screenType).isEqualTo(ScreenType.OUTER)
    }

    @Test
    fun `computeFoldState returns CLOSED when folding feature is null even if previousFoldState was HALF_OPENED`() {
        val monitor = FoldStateMonitor()

        val foldState = monitor.computeFoldState(
            foldingFeature = null,
            previousFoldState = FoldState.HALF_OPENED
        )

        assertThat(foldState).isEqualTo(FoldState.CLOSED)
        assertThat(foldState.screenType).isEqualTo(ScreenType.OUTER)
    }

    @Test
    fun `isInnerDisplay returns false when activity is null`() {
        val monitor = FoldStateMonitor()
        assertThat(monitor.isInnerDisplay(null)).isFalse()
    }

    @Test
    @Config(qualifiers = "w2232dp-h2484dp")
    fun `isInnerDisplay returns true when width exceeds 1800`() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()

        val monitor = FoldStateMonitor()
        assertThat(monitor.isInnerDisplay(activity)).isTrue()
    }

    @Test
    @Config(qualifiers = "w1080dp-h2520dp")
    fun `isInnerDisplay returns false on outer cover screen 1080x2520`() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()

        val monitor = FoldStateMonitor()
        assertThat(monitor.isInnerDisplay(activity)).isFalse()
    }

    @Test
    @Config(qualifiers = "w2232dp-h2484dp")
    fun `computeFoldState returns OPENED when on inner display and folding feature is null`() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()

        val monitor = FoldStateMonitor()
        val foldState = monitor.computeFoldState(
            foldingFeature = null,
            activity = activity
        )

        assertThat(foldState).isEqualTo(FoldState.OPENED)
        assertThat(foldState.screenType).isEqualTo(ScreenType.INNER)
    }

    @Test
    @Config(qualifiers = "w1080dp-h2520dp")
    fun `computeFoldState returns CLOSED on outer cover display even if previousFoldState was OPENED`() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()

        val monitor = FoldStateMonitor()
        val foldState = monitor.computeFoldState(
            foldingFeature = null,
            previousFoldState = FoldState.OPENED,
            activity = activity
        )

        assertThat(foldState).isEqualTo(FoldState.CLOSED)
        assertThat(foldState.screenType).isEqualTo(ScreenType.OUTER)
    }

    @Test
    fun `determineScreenType returns OUTER when folding feature is null even if previousScreenType was INNER`() {
        val monitor = FoldStateMonitor()

        val screenType = monitor.determineScreenType(
            foldingFeature = null,
            previousScreenType = ScreenType.INNER
        )

        assertThat(screenType).isEqualTo(ScreenType.OUTER)
    }

    @Test
    @Config(qualifiers = "w1080dp-h2520dp")
    fun `computeFoldState with activity and null foldingFeature returns CLOSED on outer display`() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()

        val monitor = FoldStateMonitor()
        val foldState = monitor.computeFoldState(activity, null)

        assertThat(foldState).isEqualTo(FoldState.CLOSED)
        assertThat(foldState.screenType).isEqualTo(ScreenType.OUTER)
    }

    @Test
    @Config(qualifiers = "w2232dp-h2484dp")
    fun `computeFoldState with activity and null foldingFeature returns OPENED on inner display`() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()

        val monitor = FoldStateMonitor()
        val foldState = monitor.computeFoldState(activity, null)

        assertThat(foldState).isEqualTo(FoldState.OPENED)
        assertThat(foldState.screenType).isEqualTo(ScreenType.INNER)
    }

    @Test
    @Config(qualifiers = "w1080dp-h2520dp")
    fun `foldStateFlow initial value on frame 1 is CLOSED and OUTER on outer display`() = runTest {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()

        val monitor = FoldStateMonitor()
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val flow = monitor.foldStateFlow(activity, TestScope(testDispatcher))

        assertThat(flow.value).isEqualTo(FoldState.CLOSED)
        assertThat(flow.value.screenType).isEqualTo(ScreenType.OUTER)
    }

    @Test
    @Config(qualifiers = "w2232dp-h2484dp")
    fun `foldStateFlow initial value on frame 1 is OPENED and INNER on inner display`() = runTest {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()

        val monitor = FoldStateMonitor()
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val flow = monitor.foldStateFlow(activity, TestScope(testDispatcher))

        assertThat(flow.value).isEqualTo(FoldState.OPENED)
        assertThat(flow.value.screenType).isEqualTo(ScreenType.INNER)
    }
}
