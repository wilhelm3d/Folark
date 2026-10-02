package org.arkikeskus.launcher.feature.settings

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.arkikeskus.launcher.data.AppRepository
import org.arkikeskus.launcher.data.AppUsageRepository
import org.arkikeskus.launcher.data.HomeLayoutRepository
import org.arkikeskus.launcher.data.IconPackRepository
import org.arkikeskus.launcher.data.LauncherAppsSource
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.data.local.LauncherDatabase
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import javax.inject.Provider
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var db: LauncherDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) {
            db.close()
        }
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SettingsViewModel {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dataStoreFile = File(context.filesDir, "test_settings_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = testScope) { dataStoreFile }
        val settingsRepo = SettingsRepository(dataStore)

        db = Room.inMemoryDatabaseBuilder(context, LauncherDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.homeItemDao()
        val homeLayoutRepo = HomeLayoutRepository(db, dao)
        val iconPackRepo = IconPackRepository(context)

        val appUsageRepo = AppUsageRepository(dataStore)
        val appsSource = LauncherAppsSource(context, iconPackRepo,
            Provider { ImageLoader.Builder(context).build() })
        val appRepo = AppRepository(appsSource, settingsRepo, appUsageRepo, testScope)

        return SettingsViewModel(
            repository = settingsRepo,
            homeLayoutRepository = homeLayoutRepo,
            iconPackRepository = iconPackRepo,
            appRepository = appRepo,
        )
    }

    @Test
    fun `initScreenType updates screen type for active physical screen`() = runTest {
        val viewModel = createViewModel()

        // Initial screen type defaults to OUTER before initialization
        assertThat(viewModel.currentScreenType.value).isEqualTo(ScreenType.OUTER)

        // initScreenType sets it to INNER
        viewModel.initScreenType(ScreenType.INNER)
        assertThat(viewModel.currentScreenType.value).isEqualTo(ScreenType.INNER)

        // initScreenType sets it back to OUTER
        viewModel.initScreenType(ScreenType.OUTER)
        assertThat(viewModel.currentScreenType.value).isEqualTo(ScreenType.OUTER)
    }

    @Test
    fun `dpi scaling setters update settings for selected screen type`() = runTest {
        val viewModel = createViewModel()
        viewModel.initScreenType(ScreenType.OUTER)
        backgroundScope.launch { viewModel.settings.collect {} }

        viewModel.setDpiWorkspace(0.9f)
        viewModel.setDpiAppDrawer(1.1f)
        viewModel.setDpiSettings(1.2f)

        testScheduler.advanceUntilIdle()

        val settingsOuter = viewModel.settings.value
        assertThat(settingsOuter.dpiWorkspace).isEqualTo(0.9f)
        assertThat(settingsOuter.dpiAppDrawer).isEqualTo(1.1f)
        assertThat(settingsOuter.dpiSettings).isEqualTo(1.2f)

        viewModel.initScreenType(ScreenType.INNER)
        viewModel.setDpiWorkspace(1.3f)

        testScheduler.advanceUntilIdle()

        val settingsInner = viewModel.settings.value
        assertThat(settingsInner.dpiWorkspace).isEqualTo(1.3f)
    }

    @Test
    fun `inner tablet feature setters update settings for selected screen type`() = runTest {
        val viewModel = createViewModel()
        viewModel.initScreenType(ScreenType.INNER)
        backgroundScope.launch { viewModel.settings.collect {} }

        viewModel.setInnerDualPageWorkspace(true)
        viewModel.setInnerSidebarAppDrawer(true)
        viewModel.setInnerDockAlignment(LauncherSettings.DOCK_ALIGNMENT_FLOATING)

        testScheduler.advanceUntilIdle()

        val settingsInner = viewModel.settings.value
        assertThat(settingsInner.innerDualPageWorkspace).isTrue()
        assertThat(settingsInner.innerSidebarAppDrawer).isTrue()
        assertThat(settingsInner.innerDockAlignment).isEqualTo(LauncherSettings.DOCK_ALIGNMENT_FLOATING)
    }

    @Test
    fun `drawer dimensions and alignment setters update settings for selected screen type`() = runTest {
        val viewModel = createViewModel()
        viewModel.initScreenType(ScreenType.OUTER)
        backgroundScope.launch { viewModel.settings.collect {} }

        viewModel.setDrawerHeightFraction(0.85f)
        viewModel.setDrawerWidthFraction(0.75f)
        viewModel.setDrawerAlignment(LauncherSettings.DRAWER_ALIGNMENT_LEFT)

        testScheduler.advanceUntilIdle()

        val outer = viewModel.settings.value
        assertThat(outer.drawerHeightFraction).isEqualTo(0.85f)
        assertThat(outer.drawerWidthFraction).isEqualTo(0.75f)
        assertThat(outer.drawerAlignment).isEqualTo(LauncherSettings.DRAWER_ALIGNMENT_LEFT)

        viewModel.initScreenType(ScreenType.INNER)
        testScheduler.advanceUntilIdle()

        val defaultInner = viewModel.settings.value
        assertThat(defaultInner.drawerHeightFraction).isEqualTo(1.0f)
        assertThat(defaultInner.drawerWidthFraction).isEqualTo(1.0f)
        assertThat(defaultInner.drawerAlignment).isEqualTo(LauncherSettings.DRAWER_ALIGNMENT_CENTER)

        viewModel.setDrawerHeightFraction(0.65f)
        viewModel.setDrawerWidthFraction(0.55f)
        viewModel.setDrawerAlignment(LauncherSettings.DRAWER_ALIGNMENT_RIGHT)

        testScheduler.advanceUntilIdle()

        val inner = viewModel.settings.value
        assertThat(inner.drawerHeightFraction).isEqualTo(0.65f)
        assertThat(inner.drawerWidthFraction).isEqualTo(0.55f)
        assertThat(inner.drawerAlignment).isEqualTo(LauncherSettings.DRAWER_ALIGNMENT_RIGHT)
    }

    @Test
    fun `glass blur radius and dark tint setters update settings for selected screen type`() = runTest {
        val viewModel = createViewModel()
        viewModel.initScreenType(ScreenType.OUTER)
        backgroundScope.launch { viewModel.settings.collect {} }

        viewModel.setGlassBlurRadius(30f)
        viewModel.setGlassDarkTint(0.6f)

        testScheduler.advanceUntilIdle()

        val outer = viewModel.settings.value
        assertThat(outer.glassBlurRadius).isEqualTo(30f)
        assertThat(outer.glassDarkTint).isEqualTo(0.6f)

        viewModel.initScreenType(ScreenType.INNER)
        testScheduler.advanceUntilIdle()

        val defaultInner = viewModel.settings.value
        assertThat(defaultInner.glassBlurRadius).isEqualTo(25f)
        assertThat(defaultInner.glassDarkTint).isEqualTo(0.45f)

        viewModel.setGlassBlurRadius(40f)
        viewModel.setGlassDarkTint(0.7f)

        testScheduler.advanceUntilIdle()

        val inner = viewModel.settings.value
        assertThat(inner.glassBlurRadius).isEqualTo(40f)
        assertThat(inner.glassDarkTint).isEqualTo(0.7f)
    }

    @Test
    fun `azIndexPosition setter updates settings for selected screen type`() = runTest {
        val viewModel = createViewModel()
        viewModel.initScreenType(ScreenType.OUTER)
        backgroundScope.launch { viewModel.settings.collect {} }

        viewModel.setAzIndexPosition(LauncherSettings.AZ_POSITION_HORIZONTAL_BOTTOM)
        testScheduler.advanceUntilIdle()

        val outer = viewModel.settings.value
        assertThat(outer.azIndexPosition).isEqualTo(LauncherSettings.AZ_POSITION_HORIZONTAL_BOTTOM)

        viewModel.initScreenType(ScreenType.INNER)
        testScheduler.advanceUntilIdle()

        val defaultInner = viewModel.settings.value
        assertThat(defaultInner.azIndexPosition).isEqualTo(LauncherSettings.AZ_POSITION_VERTICAL_RIGHT)

        viewModel.setAzIndexPosition(LauncherSettings.AZ_POSITION_VERTICAL_LEFT)
        testScheduler.advanceUntilIdle()

        val inner = viewModel.settings.value
        assertThat(inner.azIndexPosition).isEqualTo(LauncherSettings.AZ_POSITION_VERTICAL_LEFT)
    }

    @Test
    fun `settingsForScreen returns settings for requested screen type independent of selected screen type`() = runTest {
        val viewModel = createViewModel()
        viewModel.initScreenType(ScreenType.INNER)
        backgroundScope.launch { viewModel.settings.collect {} }

        // Set DPI for OUTER screen
        viewModel.initScreenType(ScreenType.OUTER)
        viewModel.setDpiSettings(1.1f)
        testScheduler.advanceUntilIdle()

        // Set DPI for INNER screen
        viewModel.initScreenType(ScreenType.INNER)
        viewModel.setDpiSettings(1.4f)
        testScheduler.advanceUntilIdle()

        // When selected screen type is INNER, settingsForScreen(OUTER) still returns OUTER settings
        val outerSettingsDeferred = async { viewModel.settingsForScreen(ScreenType.OUTER).first() }
        val innerSettingsDeferred = async { viewModel.settingsForScreen(ScreenType.INNER).first() }
        testScheduler.advanceUntilIdle()

        assertThat(outerSettingsDeferred.await().dpiSettings).isEqualTo(1.1f)
        assertThat(innerSettingsDeferred.await().dpiSettings).isEqualTo(1.4f)
    }

    @Test
    fun `animations and fluid motion setters update settings for selected screen type`() = runTest {
        val viewModel = createViewModel()
        viewModel.initScreenType(ScreenType.OUTER)
        backgroundScope.launch { viewModel.settings.collect {} }

        viewModel.setAquamorphicTouchEnabled(false)
        viewModel.setFoldTransitionStyle(LauncherSettings.TRANSITION_BOOK_UNFOLD_SWEEP)
        viewModel.setAppLaunchZoomEnabled(false)
        viewModel.setPageBounceEnabled(false)

        testScheduler.advanceUntilIdle()

        val outer = viewModel.settings.value
        assertThat(outer.aquamorphicTouchEnabled).isFalse()
        assertThat(outer.foldTransitionStyle).isEqualTo(LauncherSettings.TRANSITION_BOOK_UNFOLD_SWEEP)
        assertThat(outer.appLaunchZoomEnabled).isFalse()
        assertThat(outer.pageBounceEnabled).isFalse()

        viewModel.initScreenType(ScreenType.INNER)
        testScheduler.advanceUntilIdle()

        val defaultInner = viewModel.settings.value
        assertThat(defaultInner.aquamorphicTouchEnabled).isTrue()
        assertThat(defaultInner.foldTransitionStyle).isEqualTo(LauncherSettings.TRANSITION_BOOK_UNFOLD_SWEEP)
        assertThat(defaultInner.appLaunchZoomEnabled).isTrue()
        assertThat(defaultInner.pageBounceEnabled).isTrue()

        viewModel.setAquamorphicTouchEnabled(true)
        viewModel.setFoldTransitionStyle(LauncherSettings.TRANSITION_AQUAMORPHIC_RIPPLE)
        viewModel.setAppLaunchZoomEnabled(true)
        viewModel.setPageBounceEnabled(true)

        testScheduler.advanceUntilIdle()

        val inner = viewModel.settings.value
        assertThat(inner.aquamorphicTouchEnabled).isTrue()
        assertThat(inner.foldTransitionStyle).isEqualTo(LauncherSettings.TRANSITION_AQUAMORPHIC_RIPPLE)
        assertThat(inner.appLaunchZoomEnabled).isTrue()
        assertThat(inner.pageBounceEnabled).isTrue()
    }

    @Test
    fun `changing settings on INNER screen updates ONLY inner settings and does not alter OUTER screen settings`() = runTest {
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.settings.collect {} }

        // Capture initial outer settings defaults
        val initialOuter = viewModel.settingsForScreen(ScreenType.OUTER).first()

        // Switch active screen to INNER
        viewModel.setScreenType(ScreenType.INNER)
        testScheduler.advanceUntilIdle()

        // Modify settings on INNER screen
        viewModel.setHomeColumns(5)
        viewModel.setHomeRows(7)
        viewModel.setDockColumns(6)
        viewModel.setDockEnabled(false)
        viewModel.setDockBackgroundOpacity(0.8f)
        viewModel.setShowDockLabels(true)
        viewModel.setDrawerColumns(7)
        viewModel.setDrawerRows(5)
        viewModel.setDrawerStyle(LauncherSettings.DRAWER_STYLE_ONE_UI)
        viewModel.setDrawerThemeTint(LauncherSettings.DRAWER_THEME_ACCENT_TINT)
        viewModel.setDrawerAccentColor(0xFFFF0055.toInt())
        viewModel.setDrawerLayoutMode(LauncherSettings.DRAWER_LAYOUT_LIST)
        viewModel.setDrawerBackgroundBlur(18f)
        viewModel.setDrawerScrimOpacity(0.8f)
        viewModel.setGlassBlurRadius(35f)
        viewModel.setGlassDarkTint(0.65f)
        viewModel.setDpiWorkspace(1.1f)
        viewModel.setDpiAppDrawer(1.2f)
        viewModel.setDpiSettings(1.3f)
        viewModel.setAmoledDark(true)
        viewModel.setSwipeDownAction(LauncherSettings.GESTURE_SEARCH)
        viewModel.setDoubleTapAction(LauncherSettings.GESTURE_LOCK)
        viewModel.setSwipeUpAction(LauncherSettings.GESTURE_NONE)

        testScheduler.advanceUntilIdle()

        // Verify INNER settings were updated
        val innerSettings = viewModel.settingsForScreen(ScreenType.INNER).first()
        assertThat(innerSettings.homeColumns).isEqualTo(5)
        assertThat(innerSettings.homeRows).isEqualTo(7)
        assertThat(innerSettings.dockColumns).isEqualTo(6)
        assertThat(innerSettings.dockEnabled).isFalse()
        assertThat(innerSettings.dockBackgroundOpacity).isEqualTo(0.8f)
        assertThat(innerSettings.showDockLabels).isTrue()
        assertThat(innerSettings.drawerColumns).isEqualTo(7)
        assertThat(innerSettings.drawerRows).isEqualTo(5)
        assertThat(innerSettings.drawerStyle).isEqualTo(LauncherSettings.DRAWER_STYLE_ONE_UI)
        assertThat(innerSettings.drawerThemeTint).isEqualTo(LauncherSettings.DRAWER_THEME_ACCENT_TINT)
        assertThat(innerSettings.drawerAccentColor).isEqualTo(0xFFFF0055.toInt())
        assertThat(innerSettings.drawerLayoutMode).isEqualTo(LauncherSettings.DRAWER_LAYOUT_LIST)
        assertThat(innerSettings.drawerBackgroundBlur).isEqualTo(18f)
        assertThat(innerSettings.drawerScrimOpacity).isEqualTo(0.8f)
        assertThat(innerSettings.glassBlurRadius).isEqualTo(35f)
        assertThat(innerSettings.glassDarkTint).isEqualTo(0.65f)
        assertThat(innerSettings.dpiWorkspace).isEqualTo(1.1f)
        assertThat(innerSettings.dpiAppDrawer).isEqualTo(1.2f)
        assertThat(innerSettings.dpiSettings).isEqualTo(1.3f)
        assertThat(innerSettings.amoledDark).isTrue()
        assertThat(innerSettings.swipeDownAction).isEqualTo(LauncherSettings.GESTURE_SEARCH)
        assertThat(innerSettings.doubleTapAction).isEqualTo(LauncherSettings.GESTURE_LOCK)
        assertThat(innerSettings.swipeUpAction).isEqualTo(LauncherSettings.GESTURE_NONE)

        // Verify OUTER settings were NOT altered
        val outerSettings = viewModel.settingsForScreen(ScreenType.OUTER).first()
        assertThat(outerSettings.homeColumns).isEqualTo(initialOuter.homeColumns)
        assertThat(outerSettings.homeRows).isEqualTo(initialOuter.homeRows)
        assertThat(outerSettings.dockColumns).isEqualTo(initialOuter.dockColumns)
        assertThat(outerSettings.dockEnabled).isEqualTo(initialOuter.dockEnabled)
        assertThat(outerSettings.dockBackgroundOpacity).isEqualTo(initialOuter.dockBackgroundOpacity)
        assertThat(outerSettings.showDockLabels).isEqualTo(initialOuter.showDockLabels)
        assertThat(outerSettings.drawerColumns).isEqualTo(initialOuter.drawerColumns)
        assertThat(outerSettings.drawerRows).isEqualTo(initialOuter.drawerRows)
        assertThat(outerSettings.drawerStyle).isEqualTo(initialOuter.drawerStyle)
        assertThat(outerSettings.drawerThemeTint).isEqualTo(initialOuter.drawerThemeTint)
        assertThat(outerSettings.drawerAccentColor).isEqualTo(initialOuter.drawerAccentColor)
        assertThat(outerSettings.drawerLayoutMode).isEqualTo(initialOuter.drawerLayoutMode)
        assertThat(outerSettings.drawerBackgroundBlur).isEqualTo(initialOuter.drawerBackgroundBlur)
        assertThat(outerSettings.drawerScrimOpacity).isEqualTo(initialOuter.drawerScrimOpacity)
        assertThat(outerSettings.glassBlurRadius).isEqualTo(initialOuter.glassBlurRadius)
        assertThat(outerSettings.glassDarkTint).isEqualTo(initialOuter.glassDarkTint)
        assertThat(outerSettings.dpiWorkspace).isEqualTo(initialOuter.dpiWorkspace)
        assertThat(outerSettings.dpiAppDrawer).isEqualTo(initialOuter.dpiAppDrawer)
        assertThat(outerSettings.dpiSettings).isEqualTo(initialOuter.dpiSettings)
        assertThat(outerSettings.amoledDark).isEqualTo(initialOuter.amoledDark)
        assertThat(outerSettings.swipeDownAction).isEqualTo(initialOuter.swipeDownAction)
        assertThat(outerSettings.doubleTapAction).isEqualTo(initialOuter.doubleTapAction)
        assertThat(outerSettings.swipeUpAction).isEqualTo(initialOuter.swipeUpAction)
    }

    @Test
    fun `passing explicit ScreenType INNER to setters modifies ONLY inner DataStore keys and leaves OUTER settings untouched`() = runTest {
        val viewModel = createViewModel()
        viewModel.setScreenType(ScreenType.OUTER)
        backgroundScope.launch { viewModel.settings.collect {} }

        val initialOuter = viewModel.settingsForScreen(ScreenType.OUTER).first()

        // Call setters with explicit ScreenType.INNER while active screen is OUTER
        viewModel.setDrawerColumns(7, ScreenType.INNER)
        viewModel.setDrawerRows(5, ScreenType.INNER)
        viewModel.setDrawerLayoutMode(LauncherSettings.DRAWER_LAYOUT_LIST, ScreenType.INNER)
        viewModel.setDrawerHeightFraction(0.7f, ScreenType.INNER)
        viewModel.setDrawerWidthFraction(0.8f, ScreenType.INNER)
        viewModel.setDrawerAlignment(LauncherSettings.DRAWER_ALIGNMENT_LEFT, ScreenType.INNER)
        viewModel.setDrawerStyle(LauncherSettings.DRAWER_STYLE_ONE_UI, ScreenType.INNER)
        viewModel.setDrawerThemeTint(LauncherSettings.DRAWER_THEME_ACCENT_TINT, ScreenType.INNER)
        viewModel.setGlassBlurRadius(35f, ScreenType.INNER)
        viewModel.setGlassDarkTint(0.65f, ScreenType.INNER)
        viewModel.setDrawerAccentColor(0xFFFF0055.toInt(), ScreenType.INNER)
        viewModel.setAzIndexPosition(LauncherSettings.AZ_POSITION_VERTICAL_LEFT, ScreenType.INNER)
        viewModel.setHomeColumns(5, ScreenType.INNER)
        viewModel.setHomeRows(7, ScreenType.INNER)
        viewModel.setDockColumns(6, ScreenType.INNER)
        viewModel.setDockEnabled(false, ScreenType.INNER)
        viewModel.setDockBackgroundOpacity(0.8f, ScreenType.INNER)
        viewModel.setDpiWorkspace(1.1f, ScreenType.INNER)
        viewModel.setDpiAppDrawer(1.2f, ScreenType.INNER)
        viewModel.setDpiSettings(1.3f, ScreenType.INNER)
        viewModel.setAmoledDark(true, ScreenType.INNER)
        viewModel.setParallaxWallpaper(true, ScreenType.INNER)
        viewModel.setSwipeUpAction(LauncherSettings.GESTURE_NONE, ScreenType.INNER)
        viewModel.setSwipeDownAction(LauncherSettings.GESTURE_SEARCH, ScreenType.INNER)
        viewModel.setLeftSwipeAppKey("test.app/LeftActivity", ScreenType.INNER)
        viewModel.setRightSwipeAppKey("test.app/RightActivity", ScreenType.INNER)
        viewModel.setAquamorphicTouchEnabled(false, ScreenType.INNER)
        viewModel.setFoldTransitionStyle(LauncherSettings.TRANSITION_AQUAMORPHIC_RIPPLE, ScreenType.INNER)
        viewModel.setAppLaunchZoomEnabled(false, ScreenType.INNER)
        viewModel.setPageBounceEnabled(false, ScreenType.INNER)

        testScheduler.advanceUntilIdle()

        // Verify INNER settings were updated
        val inner = viewModel.settingsForScreen(ScreenType.INNER).first()
        assertThat(inner.drawerColumns).isEqualTo(7)
        assertThat(inner.drawerRows).isEqualTo(5)
        assertThat(inner.drawerLayoutMode).isEqualTo(LauncherSettings.DRAWER_LAYOUT_LIST)
        assertThat(inner.drawerHeightFraction).isEqualTo(0.7f)
        assertThat(inner.drawerWidthFraction).isEqualTo(0.8f)
        assertThat(inner.drawerAlignment).isEqualTo(LauncherSettings.DRAWER_ALIGNMENT_LEFT)
        assertThat(inner.drawerStyle).isEqualTo(LauncherSettings.DRAWER_STYLE_ONE_UI)
        assertThat(inner.drawerThemeTint).isEqualTo(LauncherSettings.DRAWER_THEME_ACCENT_TINT)
        assertThat(inner.glassBlurRadius).isEqualTo(35f)
        assertThat(inner.glassDarkTint).isEqualTo(0.65f)
        assertThat(inner.drawerAccentColor).isEqualTo(0xFFFF0055.toInt())
        assertThat(inner.azIndexPosition).isEqualTo(LauncherSettings.AZ_POSITION_VERTICAL_LEFT)
        assertThat(inner.homeColumns).isEqualTo(5)
        assertThat(inner.homeRows).isEqualTo(7)
        assertThat(inner.dockColumns).isEqualTo(6)
        assertThat(inner.dockEnabled).isFalse()
        assertThat(inner.dockBackgroundOpacity).isEqualTo(0.8f)
        assertThat(inner.dpiWorkspace).isEqualTo(1.1f)
        assertThat(inner.dpiAppDrawer).isEqualTo(1.2f)
        assertThat(inner.dpiSettings).isEqualTo(1.3f)
        assertThat(inner.amoledDark).isTrue()
        assertThat(inner.parallaxWallpaper).isTrue()
        assertThat(inner.swipeUpAction).isEqualTo(LauncherSettings.GESTURE_NONE)
        assertThat(inner.swipeDownAction).isEqualTo(LauncherSettings.GESTURE_SEARCH)
        assertThat(inner.leftSwipeAppKey).isEqualTo("test.app/LeftActivity")
        assertThat(inner.rightSwipeAppKey).isEqualTo("test.app/RightActivity")
        assertThat(inner.aquamorphicTouchEnabled).isFalse()
        assertThat(inner.foldTransitionStyle).isEqualTo(LauncherSettings.TRANSITION_AQUAMORPHIC_RIPPLE)
        assertThat(inner.appLaunchZoomEnabled).isFalse()
        assertThat(inner.pageBounceEnabled).isFalse()

        // Verify OUTER settings remain unchanged
        val outer = viewModel.settingsForScreen(ScreenType.OUTER).first()
        assertThat(outer.drawerColumns).isEqualTo(initialOuter.drawerColumns)
        assertThat(outer.drawerRows).isEqualTo(initialOuter.drawerRows)
        assertThat(outer.drawerLayoutMode).isEqualTo(initialOuter.drawerLayoutMode)
        assertThat(outer.drawerHeightFraction).isEqualTo(initialOuter.drawerHeightFraction)
        assertThat(outer.drawerWidthFraction).isEqualTo(initialOuter.drawerWidthFraction)
        assertThat(outer.drawerAlignment).isEqualTo(initialOuter.drawerAlignment)
        assertThat(outer.drawerStyle).isEqualTo(initialOuter.drawerStyle)
        assertThat(outer.drawerThemeTint).isEqualTo(initialOuter.drawerThemeTint)
        assertThat(outer.glassBlurRadius).isEqualTo(initialOuter.glassBlurRadius)
        assertThat(outer.glassDarkTint).isEqualTo(initialOuter.glassDarkTint)
        assertThat(outer.drawerAccentColor).isEqualTo(initialOuter.drawerAccentColor)
        assertThat(outer.azIndexPosition).isEqualTo(initialOuter.azIndexPosition)
        assertThat(outer.homeColumns).isEqualTo(initialOuter.homeColumns)
        assertThat(outer.homeRows).isEqualTo(initialOuter.homeRows)
        assertThat(outer.dockColumns).isEqualTo(initialOuter.dockColumns)
        assertThat(outer.dockEnabled).isEqualTo(initialOuter.dockEnabled)
        assertThat(outer.dockBackgroundOpacity).isEqualTo(initialOuter.dockBackgroundOpacity)
        assertThat(outer.dpiWorkspace).isEqualTo(initialOuter.dpiWorkspace)
        assertThat(outer.dpiAppDrawer).isEqualTo(initialOuter.dpiAppDrawer)
        assertThat(outer.dpiSettings).isEqualTo(initialOuter.dpiSettings)
        assertThat(outer.amoledDark).isEqualTo(initialOuter.amoledDark)
        assertThat(outer.parallaxWallpaper).isEqualTo(initialOuter.parallaxWallpaper)
        assertThat(outer.swipeUpAction).isEqualTo(initialOuter.swipeUpAction)
        assertThat(outer.swipeDownAction).isEqualTo(initialOuter.swipeDownAction)
        assertThat(outer.leftSwipeAppKey).isEqualTo(initialOuter.leftSwipeAppKey)
        assertThat(outer.rightSwipeAppKey).isEqualTo(initialOuter.rightSwipeAppKey)
        assertThat(outer.aquamorphicTouchEnabled).isEqualTo(initialOuter.aquamorphicTouchEnabled)
        assertThat(outer.foldTransitionStyle).isEqualTo(initialOuter.foldTransitionStyle)
        assertThat(outer.appLaunchZoomEnabled).isEqualTo(initialOuter.appLaunchZoomEnabled)
        assertThat(outer.pageBounceEnabled).isEqualTo(initialOuter.pageBounceEnabled)
    }

    @Test
    fun `applyAllSettings executes callback and flushes settings`() = runTest {
        val viewModel = createViewModel()
        var completed = false

        viewModel.applyAllSettings {
            completed = true
        }

        testScheduler.advanceUntilIdle()
        assertThat(completed).isTrue()
    }
}
