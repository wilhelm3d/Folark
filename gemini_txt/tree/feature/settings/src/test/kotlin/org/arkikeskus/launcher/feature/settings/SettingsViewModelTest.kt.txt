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
        assertThat(settingsOuter.appearance.dpiWorkspace).isEqualTo(0.9f)
        assertThat(settingsOuter.appearance.dpiAppDrawer).isEqualTo(1.1f)
        assertThat(settingsOuter.appearance.dpiSettings).isEqualTo(1.2f)

        viewModel.initScreenType(ScreenType.INNER)
        viewModel.setDpiWorkspace(1.3f)

        testScheduler.advanceUntilIdle()

        val settingsInner = viewModel.settings.value
        assertThat(settingsInner.appearance.dpiWorkspace).isEqualTo(1.3f)
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
        assertThat(settingsInner.home.innerDualPage).isTrue()
        assertThat(settingsInner.drawer.innerSidebar).isTrue()
        assertThat(settingsInner.dock.innerAlignment).isEqualTo(LauncherSettings.DOCK_ALIGNMENT_FLOATING)
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
        assertThat(outer.drawer.heightFraction).isEqualTo(0.85f)
        assertThat(outer.drawer.widthFraction).isEqualTo(0.75f)
        assertThat(outer.drawer.alignment).isEqualTo(LauncherSettings.DRAWER_ALIGNMENT_LEFT)

        viewModel.initScreenType(ScreenType.INNER)
        testScheduler.advanceUntilIdle()

        val defaultInner = viewModel.settings.value
        assertThat(defaultInner.drawer.heightFraction).isEqualTo(1.0f)
        assertThat(defaultInner.drawer.widthFraction).isEqualTo(1.0f)
        assertThat(defaultInner.drawer.alignment).isEqualTo(LauncherSettings.DRAWER_ALIGNMENT_CENTER)

        viewModel.setDrawerHeightFraction(0.65f)
        viewModel.setDrawerWidthFraction(0.55f)
        viewModel.setDrawerAlignment(LauncherSettings.DRAWER_ALIGNMENT_RIGHT)

        testScheduler.advanceUntilIdle()

        val inner = viewModel.settings.value
        assertThat(inner.drawer.heightFraction).isEqualTo(0.65f)
        assertThat(inner.drawer.widthFraction).isEqualTo(0.55f)
        assertThat(inner.drawer.alignment).isEqualTo(LauncherSettings.DRAWER_ALIGNMENT_RIGHT)
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
        assertThat(outer.drawer.glassBlurRadius).isEqualTo(30f)
        assertThat(outer.drawer.glassDarkTint).isEqualTo(0.6f)

        viewModel.initScreenType(ScreenType.INNER)
        testScheduler.advanceUntilIdle()

        val defaultInner = viewModel.settings.value
        assertThat(defaultInner.drawer.glassBlurRadius).isEqualTo(25f)
        assertThat(defaultInner.drawer.glassDarkTint).isEqualTo(0.45f)

        viewModel.setGlassBlurRadius(40f)
        viewModel.setGlassDarkTint(0.7f)

        testScheduler.advanceUntilIdle()

        val inner = viewModel.settings.value
        assertThat(inner.drawer.glassBlurRadius).isEqualTo(40f)
        assertThat(inner.drawer.glassDarkTint).isEqualTo(0.7f)
    }

    @Test
    fun `azIndexPosition setter updates settings for selected screen type`() = runTest {
        val viewModel = createViewModel()
        viewModel.initScreenType(ScreenType.OUTER)
        backgroundScope.launch { viewModel.settings.collect {} }

        viewModel.setAzIndexPosition(LauncherSettings.AZ_POSITION_HORIZONTAL_BOTTOM)
        testScheduler.advanceUntilIdle()

        val outer = viewModel.settings.value
        assertThat(outer.drawer.azIndexPosition).isEqualTo(LauncherSettings.AZ_POSITION_HORIZONTAL_BOTTOM)

        viewModel.initScreenType(ScreenType.INNER)
        testScheduler.advanceUntilIdle()

        val defaultInner = viewModel.settings.value
        assertThat(defaultInner.drawer.azIndexPosition).isEqualTo(LauncherSettings.AZ_POSITION_VERTICAL_RIGHT)

        viewModel.setAzIndexPosition(LauncherSettings.AZ_POSITION_VERTICAL_LEFT)
        testScheduler.advanceUntilIdle()

        val inner = viewModel.settings.value
        assertThat(inner.drawer.azIndexPosition).isEqualTo(LauncherSettings.AZ_POSITION_VERTICAL_LEFT)
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

        assertThat(outerSettingsDeferred.await().appearance.dpiSettings).isEqualTo(1.1f)
        assertThat(innerSettingsDeferred.await().appearance.dpiSettings).isEqualTo(1.4f)
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
        assertThat(outer.motion.aquamorphicTouchEnabled).isFalse()
        assertThat(outer.fold.transitionStyle).isEqualTo(LauncherSettings.TRANSITION_BOOK_UNFOLD_SWEEP)
        assertThat(outer.motion.appLaunchZoomEnabled).isFalse()
        assertThat(outer.home.pageBounceEnabled).isFalse()

        viewModel.initScreenType(ScreenType.INNER)
        testScheduler.advanceUntilIdle()

        val defaultInner = viewModel.settings.value
        assertThat(defaultInner.motion.aquamorphicTouchEnabled).isTrue()
        assertThat(defaultInner.fold.transitionStyle).isEqualTo(LauncherSettings.TRANSITION_BOOK_UNFOLD_SWEEP)
        assertThat(defaultInner.motion.appLaunchZoomEnabled).isTrue()
        assertThat(defaultInner.home.pageBounceEnabled).isTrue()

        viewModel.setAquamorphicTouchEnabled(true)
        viewModel.setFoldTransitionStyle(LauncherSettings.TRANSITION_AQUAMORPHIC_RIPPLE)
        viewModel.setAppLaunchZoomEnabled(true)
        viewModel.setPageBounceEnabled(true)

        testScheduler.advanceUntilIdle()

        val inner = viewModel.settings.value
        assertThat(inner.motion.aquamorphicTouchEnabled).isTrue()
        assertThat(inner.fold.transitionStyle).isEqualTo(LauncherSettings.TRANSITION_AQUAMORPHIC_RIPPLE)
        assertThat(inner.motion.appLaunchZoomEnabled).isTrue()
        assertThat(inner.home.pageBounceEnabled).isTrue()
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
        assertThat(innerSettings.home.columns).isEqualTo(5)
        assertThat(innerSettings.home.rows).isEqualTo(7)
        assertThat(innerSettings.dock.columns).isEqualTo(6)
        assertThat(innerSettings.dock.enabled).isFalse()
        assertThat(innerSettings.dock.backgroundOpacity).isEqualTo(0.8f)
        assertThat(innerSettings.dock.showLabels).isTrue()
        assertThat(innerSettings.drawer.columns).isEqualTo(7)
        assertThat(innerSettings.drawer.rows).isEqualTo(5)
        assertThat(innerSettings.drawer.style).isEqualTo(LauncherSettings.DRAWER_STYLE_ONE_UI)
        assertThat(innerSettings.drawer.themeTint).isEqualTo(LauncherSettings.DRAWER_THEME_ACCENT_TINT)
        assertThat(innerSettings.drawer.accentColor).isEqualTo(0xFFFF0055.toInt())
        assertThat(innerSettings.drawer.layoutMode).isEqualTo(LauncherSettings.DRAWER_LAYOUT_LIST)
        assertThat(innerSettings.drawer.backgroundBlur).isEqualTo(18f)
        assertThat(innerSettings.drawer.scrimOpacity).isEqualTo(0.8f)
        assertThat(innerSettings.drawer.glassBlurRadius).isEqualTo(35f)
        assertThat(innerSettings.drawer.glassDarkTint).isEqualTo(0.65f)
        assertThat(innerSettings.appearance.dpiWorkspace).isEqualTo(1.1f)
        assertThat(innerSettings.appearance.dpiAppDrawer).isEqualTo(1.2f)
        assertThat(innerSettings.appearance.dpiSettings).isEqualTo(1.3f)
        assertThat(innerSettings.appearance.amoledDark).isTrue()
        assertThat(innerSettings.home.swipeDownAction).isEqualTo(LauncherSettings.GESTURE_SEARCH)
        assertThat(innerSettings.home.doubleTapAction).isEqualTo(LauncherSettings.GESTURE_LOCK)
        assertThat(innerSettings.home.swipeUpAction).isEqualTo(LauncherSettings.GESTURE_NONE)

        // Verify OUTER settings were NOT altered
        val outerSettings = viewModel.settingsForScreen(ScreenType.OUTER).first()
        assertThat(outerSettings.home.columns).isEqualTo(initialOuter.home.columns)
        assertThat(outerSettings.home.rows).isEqualTo(initialOuter.home.rows)
        assertThat(outerSettings.dock.columns).isEqualTo(initialOuter.dock.columns)
        assertThat(outerSettings.dock.enabled).isEqualTo(initialOuter.dock.enabled)
        assertThat(outerSettings.dock.backgroundOpacity).isEqualTo(initialOuter.dock.backgroundOpacity)
        assertThat(outerSettings.dock.showLabels).isEqualTo(initialOuter.dock.showLabels)
        assertThat(outerSettings.drawer.columns).isEqualTo(initialOuter.drawer.columns)
        assertThat(outerSettings.drawer.rows).isEqualTo(initialOuter.drawer.rows)
        assertThat(outerSettings.drawer.style).isEqualTo(initialOuter.drawer.style)
        assertThat(outerSettings.drawer.themeTint).isEqualTo(initialOuter.drawer.themeTint)
        assertThat(outerSettings.drawer.accentColor).isEqualTo(initialOuter.drawer.accentColor)
        assertThat(outerSettings.drawer.layoutMode).isEqualTo(initialOuter.drawer.layoutMode)
        assertThat(outerSettings.drawer.backgroundBlur).isEqualTo(initialOuter.drawer.backgroundBlur)
        assertThat(outerSettings.drawer.scrimOpacity).isEqualTo(initialOuter.drawer.scrimOpacity)
        assertThat(outerSettings.drawer.glassBlurRadius).isEqualTo(initialOuter.drawer.glassBlurRadius)
        assertThat(outerSettings.drawer.glassDarkTint).isEqualTo(initialOuter.drawer.glassDarkTint)
        assertThat(outerSettings.appearance.dpiWorkspace).isEqualTo(initialOuter.appearance.dpiWorkspace)
        assertThat(outerSettings.appearance.dpiAppDrawer).isEqualTo(initialOuter.appearance.dpiAppDrawer)
        assertThat(outerSettings.appearance.dpiSettings).isEqualTo(initialOuter.appearance.dpiSettings)
        assertThat(outerSettings.appearance.amoledDark).isEqualTo(initialOuter.appearance.amoledDark)
        assertThat(outerSettings.home.swipeDownAction).isEqualTo(initialOuter.home.swipeDownAction)
        assertThat(outerSettings.home.doubleTapAction).isEqualTo(initialOuter.home.doubleTapAction)
        assertThat(outerSettings.home.swipeUpAction).isEqualTo(initialOuter.home.swipeUpAction)
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
        assertThat(inner.drawer.columns).isEqualTo(7)
        assertThat(inner.drawer.rows).isEqualTo(5)
        assertThat(inner.drawer.layoutMode).isEqualTo(LauncherSettings.DRAWER_LAYOUT_LIST)
        assertThat(inner.drawer.heightFraction).isEqualTo(0.7f)
        assertThat(inner.drawer.widthFraction).isEqualTo(0.8f)
        assertThat(inner.drawer.alignment).isEqualTo(LauncherSettings.DRAWER_ALIGNMENT_LEFT)
        assertThat(inner.drawer.style).isEqualTo(LauncherSettings.DRAWER_STYLE_ONE_UI)
        assertThat(inner.drawer.themeTint).isEqualTo(LauncherSettings.DRAWER_THEME_ACCENT_TINT)
        assertThat(inner.drawer.glassBlurRadius).isEqualTo(35f)
        assertThat(inner.drawer.glassDarkTint).isEqualTo(0.65f)
        assertThat(inner.drawer.accentColor).isEqualTo(0xFFFF0055.toInt())
        assertThat(inner.drawer.azIndexPosition).isEqualTo(LauncherSettings.AZ_POSITION_VERTICAL_LEFT)
        assertThat(inner.home.columns).isEqualTo(5)
        assertThat(inner.home.rows).isEqualTo(7)
        assertThat(inner.dock.columns).isEqualTo(6)
        assertThat(inner.dock.enabled).isFalse()
        assertThat(inner.dock.backgroundOpacity).isEqualTo(0.8f)
        assertThat(inner.appearance.dpiWorkspace).isEqualTo(1.1f)
        assertThat(inner.appearance.dpiAppDrawer).isEqualTo(1.2f)
        assertThat(inner.appearance.dpiSettings).isEqualTo(1.3f)
        assertThat(inner.appearance.amoledDark).isTrue()
        assertThat(inner.home.parallaxWallpaper).isTrue()
        assertThat(inner.home.swipeUpAction).isEqualTo(LauncherSettings.GESTURE_NONE)
        assertThat(inner.home.swipeDownAction).isEqualTo(LauncherSettings.GESTURE_SEARCH)
        assertThat(inner.home.leftSwipeAppKey).isEqualTo("test.app/LeftActivity")
        assertThat(inner.home.rightSwipeAppKey).isEqualTo("test.app/RightActivity")
        assertThat(inner.motion.aquamorphicTouchEnabled).isFalse()
        assertThat(inner.fold.transitionStyle).isEqualTo(LauncherSettings.TRANSITION_AQUAMORPHIC_RIPPLE)
        assertThat(inner.motion.appLaunchZoomEnabled).isFalse()
        assertThat(inner.home.pageBounceEnabled).isFalse()

        // Verify OUTER settings remain unchanged
        val outer = viewModel.settingsForScreen(ScreenType.OUTER).first()
        assertThat(outer.drawer.columns).isEqualTo(initialOuter.drawer.columns)
        assertThat(outer.drawer.rows).isEqualTo(initialOuter.drawer.rows)
        assertThat(outer.drawer.layoutMode).isEqualTo(initialOuter.drawer.layoutMode)
        assertThat(outer.drawer.heightFraction).isEqualTo(initialOuter.drawer.heightFraction)
        assertThat(outer.drawer.widthFraction).isEqualTo(initialOuter.drawer.widthFraction)
        assertThat(outer.drawer.alignment).isEqualTo(initialOuter.drawer.alignment)
        assertThat(outer.drawer.style).isEqualTo(initialOuter.drawer.style)
        assertThat(outer.drawer.themeTint).isEqualTo(initialOuter.drawer.themeTint)
        assertThat(outer.drawer.glassBlurRadius).isEqualTo(initialOuter.drawer.glassBlurRadius)
        assertThat(outer.drawer.glassDarkTint).isEqualTo(initialOuter.drawer.glassDarkTint)
        assertThat(outer.drawer.accentColor).isEqualTo(initialOuter.drawer.accentColor)
        assertThat(outer.drawer.azIndexPosition).isEqualTo(initialOuter.drawer.azIndexPosition)
        assertThat(outer.home.columns).isEqualTo(initialOuter.home.columns)
        assertThat(outer.home.rows).isEqualTo(initialOuter.home.rows)
        assertThat(outer.dock.columns).isEqualTo(initialOuter.dock.columns)
        assertThat(outer.dock.enabled).isEqualTo(initialOuter.dock.enabled)
        assertThat(outer.dock.backgroundOpacity).isEqualTo(initialOuter.dock.backgroundOpacity)
        assertThat(outer.appearance.dpiWorkspace).isEqualTo(initialOuter.appearance.dpiWorkspace)
        assertThat(outer.appearance.dpiAppDrawer).isEqualTo(initialOuter.appearance.dpiAppDrawer)
        assertThat(outer.appearance.dpiSettings).isEqualTo(initialOuter.appearance.dpiSettings)
        assertThat(outer.appearance.amoledDark).isEqualTo(initialOuter.appearance.amoledDark)
        assertThat(outer.home.parallaxWallpaper).isEqualTo(initialOuter.home.parallaxWallpaper)
        assertThat(outer.home.swipeUpAction).isEqualTo(initialOuter.home.swipeUpAction)
        assertThat(outer.home.swipeDownAction).isEqualTo(initialOuter.home.swipeDownAction)
        assertThat(outer.home.leftSwipeAppKey).isEqualTo(initialOuter.home.leftSwipeAppKey)
        assertThat(outer.home.rightSwipeAppKey).isEqualTo(initialOuter.home.rightSwipeAppKey)
        assertThat(outer.motion.aquamorphicTouchEnabled).isEqualTo(initialOuter.motion.aquamorphicTouchEnabled)
        assertThat(outer.fold.transitionStyle).isEqualTo(initialOuter.fold.transitionStyle)
        assertThat(outer.motion.appLaunchZoomEnabled).isEqualTo(initialOuter.motion.appLaunchZoomEnabled)
        assertThat(outer.home.pageBounceEnabled).isEqualTo(initialOuter.home.pageBounceEnabled)
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
