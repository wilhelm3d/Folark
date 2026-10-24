package org.arkikeskus.launcher.data

import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.Point
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.view.Display
import androidx.annotation.RequiresApi
import coil3.ImageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.AppPair
import org.arkikeskus.launcher.model.IconEpochs
import org.arkikeskus.launcher.model.LauncherSettings
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** ActivityOptions' bundle key for the launch windowing mode, and WindowConfiguration's freeform value. */
private const val KEY_LAUNCH_WINDOWING_MODE = "android.activity.windowingMode"
private const val WINDOWING_MODE_FREEFORM = 5

// Motorola's pop-up window, as Motorola's ActivityOptions stores it (FLAG_INHERITABLE_MOTO_GLOBAL_FREEFORM).
private const val KEY_MOTO_FLAGS = "key_moto_flags"
private const val MOTO_FLAG_GLOBAL_FREEFORM = 1048576
private const val KEY_MOTO_POPUP_DEFAULT_BOUNDS = "android.globalfreeform.use_default_bounds"

/** Pure decision behind [LauncherAppsSource.isAppInstalled]; [profile] is the profile lookup's outcome. */
internal fun <U : Any> installVerdict(profile: Result<U?>, appInfo: (U) -> Result<*>): Boolean {
    // A lookup that THREW is not a removed profile: only a successful null answer is definitive.
    if (profile.isFailure) return true
    val user = profile.getOrNull() ?: return false // the whole profile is gone → its rows are stale
    val result = appInfo(user)
    return result.isSuccess || result.exceptionOrNull() !is PackageManager.NameNotFoundException
}

/**
 * Wraps [LauncherApps]: streams the installed launchable apps (reacting to install/remove/change),
 * launches apps, and resolves their icons.
 */
private const val FORCED_ICON_EDGE = 192

/** Below this share of the square, a theme-less icon's artwork is a logo whose outline is enough. */
private const val SILHOUETTE_MAX_COVERAGE = 0.30f

/** A filled shape needs at least this much brightness difference inside it to give a glyph. */
private const val MIN_CONTRAST = 28

/** A made-up glyph covering less, or more, of the square than this is not recognisable. */
private const val MIN_GLYPH_SHARE = 0.015f
private const val MAX_GLYPH_SHARE = 0.55f

@Singleton
class LauncherAppsSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val iconPacks: IconPackRepository,
    // Provider breaks the instantiation cycle: the ImageLoader itself is built with this source.
    private val imageLoader: Provider<ImageLoader>,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    /** Package names from LauncherApps callbacks — lets feature-layer caches (e.g. resolved pinned
     *  shortcuts) invalidate per package instead of guessing from full app-list reloads. */
    private val _packageEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val packageEvents: SharedFlow<String> = _packageEvents

    /** (package, profile serial) from onPackageRemoved — a DEFINITIVE uninstall for that profile.
     *  Updates fire onPackageChanged and storage ejection onPackagesUnavailable, so neither can
     *  reach this flow and trigger a wrongful home-row cleanup. */
    private val _packageRemovals = MutableSharedFlow<Pair<String, Long>>(extraBufferCapacity = 16)
    val packageRemovals: SharedFlow<Pair<String, Long>> = _packageRemovals

    /** Re-fetch tokens for app icons (see [IconEpochs]): a package update bumps that package, an
     *  icon-pack content change bumps the global generation. AppIcon feeds these into the Coil
     *  model/cache key, so even icons already on screen re-fetch immediately. */
    private val _iconEpochs = MutableStateFlow(IconEpochs())
    val iconEpochs: StateFlow<IconEpochs> = _iconEpochs

    /** Private Space (Android 15+), as of the last app scan; null when there is none or it is hidden. */
    private val _privateSpace = MutableStateFlow<PrivateSpaceState?>(null)
    val privateSpace: StateFlow<PrivateSpaceState?> = _privateSpace

    /** Locks or unlocks Private Space; unlocking asks for the screen lock first (Android's prompt).
     *  The default launcher may do this; the app list follows through the profile broadcasts. */
    fun setPrivateSpaceLocked(locked: Boolean): Boolean {
        val space = _privateSpace.value ?: return false
        return runCatching { userManager?.requestQuietModeEnabled(locked, space.user) == true }.getOrDefault(false)
    }

    /** Opens the app store inside Private Space, to install apps there (Android 15+). */
    fun privateSpaceInstallSender(): IntentSender? {
        val space = _privateSpace.value ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return null
        return runCatching { launcherApps.getAppMarketActivityIntent(null, space.user) }.getOrNull()
    }

    private fun isPrivateProfile(user: UserHandle): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && runCatching {
            launcherApps.getLauncherUserInfo(user)?.userType == UserManager.USER_TYPE_PROFILE_PRIVATE
        }.getOrDefault(false)

    /** Fail-safe install check for the ghost-row sweep: only a definitive "name not found" (or a
     *  removed profile) answers false — any other failure keeps the row (a locked work profile or
     *  a transient Binder error must never wipe real items). */
    fun isAppInstalled(packageName: String, userSerial: Long): Boolean {
        val um = userManager ?: return true
        return installVerdict(runCatching { um.getUserForSerialNumber(userSerial) }) { user ->
            runCatching { launcherApps.getApplicationInfo(packageName, 0, user) }
        }
    }

    /** Forces global icon epoch bump, clears icon pack and Coil memory caches. */
    fun invalidateCachesAndReload() {
        _iconEpochs.update { it.bumpGlobal() }
        iconPacks.clearCache()
        runCatching { imageLoader.get().memoryCache?.clear() }
    }

    fun appsFlow(): Flow<List<AppItem>> = callbackFlow {
        val handler = Handler(Looper.getMainLooper())

        // One serial worker fed by a conflated trigger, NOT a coroutine per event: overlapping
        // queryApps() calls could complete out of order, leaving the flow's latest value a stale
        // snapshot after a burst of package events (install storms, batch updates). Serializing
        // guarantees the newest scan is emitted last; conflation coalesces the burst into one rescan.
        val reloads = Channel<Unit>(Channel.CONFLATED)
        fun reload() { reloads.trySend(Unit) }
        launch(Dispatchers.IO) {
            for (unused in reloads) trySend(queryApps())
        }

        fun packageEvent(vararg packageNames: String, iconsMayHaveChanged: Boolean = false) {
            packageNames.forEach { _packageEvents.tryEmit(it) }
            // An updated/replaced package bumps its icon epoch: the epoch is in the Coil model/cache
            // key, so the changed app's icon re-fetches everywhere — including AsyncImages already on
            // screen (dock, open home page, drawer top), which only re-launch when the model changes.
            // A memory-cache clear alone left those painters showing the OLD icon until process death.
            if (iconsMayHaveChanged) {
                _iconEpochs.update { it.bump(packageNames.asIterable()) }
            }
            // A changed icon PACK invalidates every mapped/masked icon, not just its own package:
            // bump the global generation (re-keys all icons) and drop the now-dead cache entries.
            if (packageNames.any { iconPacks.invalidate(it) }) {
                _iconEpochs.update { it.bumpGlobal() }
                runCatching { imageLoader.get().memoryCache?.clear() }
            }
            reload()
        }

        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: UserHandle) = packageEvent(packageName)
            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                val serial = runCatching { userManager?.getSerialNumberForUser(user) }.getOrNull() ?: 0L
                _packageRemovals.tryEmit(packageName to serial)
                packageEvent(packageName)
            }
            override fun onPackageChanged(packageName: String, user: UserHandle) =
                packageEvent(packageName, iconsMayHaveChanged = true)
            override fun onPackagesAvailable(names: Array<out String>, user: UserHandle, replacing: Boolean) =
                packageEvent(*names, iconsMayHaveChanged = replacing)
            override fun onPackagesUnavailable(names: Array<out String>, user: UserHandle, replacing: Boolean) =
                packageEvent(*names)
        }

        // A profile locked, unlocked, added or removed (Private Space, a work profile): scan again.
        val profileReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = reload()
        }
        val profileFilter = IntentFilter().apply { PROFILE_ACTIONS.forEach { addAction(it) } }
        val profileRegistered = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(profileReceiver, profileFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(profileReceiver, profileFilter)
            }
        }.isSuccess

        launcherApps.registerCallback(callback, handler)
        reload()
        awaitClose {
            launcherApps.unregisterCallback(callback)
            if (profileRegistered) runCatching { context.unregisterReceiver(profileReceiver) }
        }
    }

    private fun queryApps(): List<AppItem> {
        // LauncherApps' profiles include Private Space (Android 15+, for the default launcher that
        // holds ACCESS_HIDDEN_PROFILES); UserManager's list leaves it out.
        val profiles = runCatching { launcherApps.profiles }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: runCatching { userManager?.userProfiles }.getOrNull()
            ?: listOf(Process.myUserHandle())
        var privateSpace: PrivateSpaceState? = null
        val apps = profiles.flatMap { user ->
            val isPrivate = isPrivateProfile(user)
            if (isPrivate) {
                val locked = runCatching { userManager?.isQuietModeEnabled(user) }.getOrNull() ?: true
                privateSpace = PrivateSpaceState(user, locked)
                // Locked: none of its apps are listed, not even their names.
                if (locked) return@flatMap emptyList()
            }
            val serial = runCatching { userManager?.getSerialNumberForUser(user) }.getOrNull() ?: 0L
            val list = runCatching {
                launcherApps.getActivityList(null, user)
            }.getOrElse { emptyList() }
            val userApps = list.map { info ->
                AppItem(
                    packageName = info.componentName.packageName,
                    className = info.componentName.className,
                    user = user,
                    userSerial = serial,
                    label = info.label?.toString().orEmpty(),
                    privateProfile = isPrivate,
                    isArchived = if (Build.VERSION.SDK_INT >= 35) info.applicationInfo.isArchived else false,
                )
            }.toMutableList()
            if (!isPrivate && user == Process.myUserHandle()) {
                runCatching {
                    val pInfo = context.packageManager.getPackageInfo("com.motorola.smartmode", 0)
                    if (pInfo.applicationInfo?.enabled == true) {
                        userApps.add(
                            AppItem(
                                packageName = "com.motorola.smartmode",
                                className = "com.motorola.smartmode.ui.activities.HomePageActivity",
                                user = user,
                                userSerial = serial,
                                label = "Smart Rules",
                                privateProfile = false,
                            ),
                        )
                    }
                }
            }
            userApps
        }.sortedBy { it.label.lowercase() }
        _privateSpace.value = privateSpace
        return apps
    }

    /**
     * Launches [appItem]. A launcher is the device's HOME, so a single failed launch (app removed
     * mid-tap, profile locked, activity no longer launchable) must never crash the process — the
     * error is captured in the [Result] for the caller to log or surface.
     */
    fun launch(
        appItem: AppItem,
        animation: String = LauncherSettings.APP_OPEN_SYSTEM,
        speed: Float = 1f,
    ): Result<Unit> = runCatching {
        val fromIcon = animation == LauncherSettings.APP_OPEN_ICON || animation == LauncherSettings.APP_OPEN_REVEAL
        val origin = if (fromIcon) LaunchOrigin.take() else null
        val options = if (origin != null) {
            fromIconOptions(origin.first, origin.second, reveal = animation == LauncherSettings.APP_OPEN_REVEAL)
        } else {
            openOptions(animation, speed)
        }
        runCatching {
            launcherApps.startMainActivity(appItem.componentName, appItem.user, origin?.second, options)
        }.getOrElse {
            val intent = context.packageManager.getLaunchIntentForPackage(appItem.packageName)
                ?: Intent("com.motorola.smartmode.LAUNCH_SMART_ZONE").setComponent(appItem.componentName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent, options)
        }
    }

    /** The app's window grows out of the icon at [bounds] (on screen), measured from [view]; with
     *  [reveal] it is uncovered from there instead (a clip that widens to the full window). */
    private fun fromIconOptions(view: android.view.View, bounds: Rect, reveal: Boolean = false): Bundle? = runCatching {
        val at = IntArray(2)
        view.getLocationOnScreen(at)
        val x = bounds.left - at[0]
        val y = bounds.top - at[1]
        if (reveal) {
            ActivityOptions.makeClipRevealAnimation(view, x, y, bounds.width(), bounds.height()).toBundle()
        } else {
            ActivityOptions.makeScaleUpAnimation(view, x, y, bounds.width(), bounds.height()).toBundle()
        }
    }.getOrNull()

    /**
     * Launch options for the chosen app-open animation; null leaves it to the system. View
     * animations have fixed lengths, so the speed setting picks one of three prepared sets.
     */
    private fun openOptions(animation: String, speed: Float): Bundle? {
        val tier = when {
            speed >= 1.4f -> 0
            speed <= 0.75f -> 2
            else -> 1
        }
        val enter = when (animation) {
            LauncherSettings.APP_OPEN_ZOOM ->
                intArrayOf(R.anim.folark_open_zoom_fast, R.anim.folark_open_zoom_normal, R.anim.folark_open_zoom_slow)[tier]
            LauncherSettings.APP_OPEN_SLIDE ->
                intArrayOf(R.anim.folark_open_slide_fast, R.anim.folark_open_slide_normal, R.anim.folark_open_slide_slow)[tier]
            LauncherSettings.APP_OPEN_FADE ->
                intArrayOf(R.anim.folark_open_fade_fast, R.anim.folark_open_fade_normal, R.anim.folark_open_fade_slow)[tier]
            LauncherSettings.APP_OPEN_NONE -> return ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle()
            else -> return null
        }
        val exit = intArrayOf(R.anim.folark_open_hold_fast, R.anim.folark_open_hold_normal, R.anim.folark_open_hold_slow)[tier]
        return ActivityOptions.makeCustomAnimation(context, enter, exit).toBundle()
    }

    /**
     * Opens [appItem] in a freeform ("floating" / pop-up) window, centred on the current display.
     *
     * There is no public API to ask for a windowing mode, so this passes the platform's own
     * ActivityOptions bundle key next to the public launch bounds. The system honours it only where
     * freeform windows exist (desktop/pop-up capable devices, or "Enable freeform windows" in
     * developer options); everywhere else the app simply opens full screen.
     */
    fun launchFloating(
        appItem: AppItem,
        size: String = LauncherSettings.FLOATING_SIZE_MEDIUM,
    ): Result<Unit> = runCatching {
        launcherApps.startMainActivity(appItem.componentName, appItem.user, null, floatingOptions(size))
    }

    /** Launch options for a floating window: a Motorola pop-up where possible, else Folark's own. */
    private fun floatingOptions(size: String): Bundle {
        motoPopupOptions()?.let { return it }
        return plainFloatingOptions(size)
    }

    /**
     * Motorola's pop-up window, as its own launcher asks for it: freeform mode plus Motorola's
     * global-freeform flag, placed and sized by the system. Motorola's ActivityOptions keeps its
     * flags as a plain number under "key_moto_flags" and the default-place switch under
     * "android.globalfreeform.use_default_bounds", so both go straight into the options. Null on
     * phones without Motorola's pop-up windows.
     */
    private fun motoPopupOptions(): Bundle? {
        val available = motoPopupAvailable ?: MotoPopup.isAvailable(context).also { motoPopupAvailable = it }
        if (!available) return null
        return ActivityOptions.makeBasic().toBundle().apply {
            putInt(KEY_LAUNCH_WINDOWING_MODE, WINDOWING_MODE_FREEFORM)
            putInt(KEY_MOTO_FLAGS, MOTO_FLAG_GLOBAL_FREEFORM)
            putBoolean(KEY_MOTO_POPUP_DEFAULT_BOUNDS, true)
        }
    }

    private var motoPopupAvailable: Boolean? = null

    /** Launch options for a centred freeform window of the chosen [size] on the current display. */
    private fun plainFloatingOptions(size: String): Bundle {
        val screen = currentScreenSize()
        val width = screen.x
        val height = screen.y
        // A wide (unfolded) display gets a phone-shaped window; a narrow one a shorter, near-full-width one.
        val wide = width > height * 0.85f
        val factor = when (size) {
            LauncherSettings.FLOATING_SIZE_SMALL -> 0.78f
            LauncherSettings.FLOATING_SIZE_LARGE -> 1.22f
            else -> 1f
        }
        val density = context.resources.displayMetrics.density
        val margin = (16 * density).toInt()
        val usableTop = systemBarHeight("status_bar_height") + margin
        val usableBottom = height - systemBarHeight("navigation_bar_height") - margin
        val usableHeight = (usableBottom - usableTop).coerceAtLeast(height / 2)
        val windowWidth = (width * ((if (wide) 0.46f else 0.82f) * factor).coerceAtMost(0.9f)).toInt()
        val windowHeight = (usableHeight * ((if (wide) 0.8f else 0.62f) * factor).coerceAtMost(0.9f)).toInt()
        val left = (width - windowWidth) / 2
        val top = usableTop + (usableHeight - windowHeight) / 2
        val options = ActivityOptions.makeBasic()
            .setLaunchBounds(Rect(left, top, left + windowWidth, top + windowHeight))
            .toBundle()
        options.putInt(KEY_LAUNCH_WINDOWING_MODE, WINDOWING_MODE_FREEFORM)
        return options
    }

    /** A system bar's height from the system's own dimensions, or a safe guess. */
    private fun systemBarHeight(name: String): Int {
        val res = context.resources
        @Suppress("DiscouragedApi")
        val id = res.getIdentifier(name, "dimen", "android")
        return if (id > 0) res.getDimensionPixelSize(id) else (48 * res.displayMetrics.density).toInt()
    }

    /**
     * The real size of the screen right now, in pixels. The app context's own metrics can lag a fold
     * or still describe the other screen; the display itself always has the current size.
     */
    private fun currentScreenSize(): Point {
        val size = Point()
        runCatching {
            val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
            @Suppress("DEPRECATION")
            display?.getRealSize(size)
        }
        if (size.x <= 0 || size.y <= 0) {
            val metrics = context.resources.displayMetrics
            size.set(metrics.widthPixels, metrics.heightPixels)
        }
        return size
    }

    /** Whether the one-time split-screen grant was given (see [ShellSplit]). */
    fun canSplitByCommand(): Boolean = ShellSplit.available(context)

    /**
     * Moves [packageName]'s newest task into the right half of a split screen, beside the app
     * behind it, with Android's split-screen command. Blocking (it waits briefly for the task to
     * appear): call it off the main thread. False when the task wasn't found or the command refused.
     */
    fun moveIntoSplit(packageName: String): Boolean {
        var taskId: Int? = null
        for (attempt in 0 until SPLIT_FIND_TRIES) {
            taskId = ShellSplit.taskIdOf(context, packageName)
            if (taskId != null) break
            Thread.sleep(SPLIT_RETRY_MS)
        }
        val id = taskId ?: return false
        return ShellSplit.moveToSide(context, id, right = true)
    }

    /**
     * Puts the third app of [pair] into the split screen as a real third pane (Folark's Vector
     * module in SystemUI, see [SplitThree]); false when that isn't possible here.
     */
    suspend fun launchAppPairThirdInSplit(pair: AppPair): Boolean =
        SplitThree.ready(context) &&
            SplitThree.addThird(context, component = pair.app3ComponentName, user = userForSerial(pair.app3UserSerial))

    /** Switches the split showing now to Android's layout [snap] (see [SplitThree.snap]). */
    suspend fun snapSplit(snap: Int): Boolean = SplitThree.ready(context) && SplitThree.snap(context, snap)

    /** Opens the optional third app of [pair] in a floating window over the split pair. */
    fun launchAppPairThird(pair: AppPair, size: String): Result<Unit> = runCatching {
        launcherApps.startMainActivity(
            pair.app3ComponentName, userForSerial(pair.app3UserSerial), null, floatingOptions(size),
        )
    }

    /**
     * Opens [appItem] next to the app that is in front right now, i.e. as the second half of a split
     * screen (FLAG_ACTIVITY_LAUNCH_ADJACENT — Android 12L+ enters split screen for it; older or
     * unsupported systems just open the app normally).
     */
    fun launchAdjacent(appItem: AppItem): Result<Unit> = runCatching {
        startAdjacent(appItem.componentName, appItem.user)
    }

    private fun startAdjacent(component: ComponentName, user: UserHandle) {
        if (user == Process.myUserHandle()) {
            context.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(component)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                            Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT,
                    ),
            )
        } else {
            // An app in another profile (work / Private Space) can only be started through
            // LauncherApps, which takes no intent flags — it opens, but not as a split half.
            launcherApps.startMainActivity(component, user, null, null)
        }
    }

    private fun userForSerial(serial: Long): UserHandle =
        runCatching { userManager?.getUserForSerialNumber(serial) }.getOrNull() ?: Process.myUserHandle()

    /** Starts the first app of [pair]; [launchAppPairSecond] then puts the second one beside it. */
    fun launchAppPair(pair: AppPair): Result<Unit> = runCatching {
        launcherApps.startMainActivity(pair.app1ComponentName, userForSerial(pair.app1UserSerial), null, null)
    }

    /** Opens the second app of [pair] as the other half of a split screen (see [launchAdjacent]). */
    fun launchAppPairSecond(pair: AppPair): Result<Unit> = runCatching {
        startAdjacent(pair.app2ComponentName, userForSerial(pair.app2UserSerial))
    }

    /**
     * The launcher entry the system lists first for [packageName] in [user] — the one the package's
     * launch intent resolves to when it declares several MAIN/LAUNCHER activities. Resolved through
     * [LauncherApps] so work-profile packages resolve in their own profile. Null when the package has
     * no launcher entry or the profile is unavailable.
     */
    fun launchClassName(packageName: String, user: UserHandle): String? = runCatching {
        launcherApps.getActivityList(packageName, user).firstOrNull()?.componentName?.className
    }.getOrNull()

    /**
     * Resolves [appItem]'s icon. Like [launch], this must never crash the process (the launcher is the
     * device HOME): the app can be removed mid-load or its profile locked, so any failure resolves to
     * a null icon instead of propagating.
     *
     * A selected [iconPack] takes priority: a mapped app uses the pack's drawable, an unmapped app keeps
     * its normal icon. Otherwise, when [themed] is set (and the device + app support it) the Material You
     * monochrome icon is returned, tinted for the [dark]/light theme; else the normal icon.
     */
    fun loadIcon(
        appItem: AppItem,
        themed: Boolean = false,
        dark: Boolean = false,
        iconPack: String = "",
        customIcon: String = "",
    ): Drawable? = runCatching {
        // The user's own pick for this app ("pack|drawable") wins over the pack-wide and themed
        // styles; if its pack or drawable is gone, the normal resolution below still applies.
        if (customIcon.isNotBlank()) {
            val pack = customIcon.substringBefore('|')
            val name = customIcon.substringAfter('|', "")
            if (name.isNotEmpty()) iconPacks.get(pack)?.getDrawable(name)?.let { return@runCatching it }
        }
        val info = launcherApps.getActivityList(appItem.packageName, appItem.user)
            .firstOrNull { it.componentName.className == appItem.className }
            ?: return@runCatching null
        // A selected icon pack overrides themed icons: use the pack's drawable if it maps this app,
        // otherwise fall through to the normal icon below.
        if (iconPack.isNotBlank()) {
            iconPacks.get(iconPack)?.getIcon(appItem.componentName)?.let { return@runCatching it }
        } else if (themed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            themedIcon(info, dark)?.let { return@runCatching it }
            // Settings ▸ Theme all icons: an app without a themed icon gets one made from its own.
            if (forceThemedIcons) forcedThemedIcon(info, dark)?.let { return@runCatching it }
        }
        info.getBadgedIcon(0)
    }.getOrNull()

    /** Mirrors Settings ▸ Theme all icons (kept current by AppRepository). */
    @Volatile
    var forceThemedIcons: Boolean = false

    /** Mirrors Settings ▸ Themed icon style, one of LauncherSettings.THEMED_STYLE_* (kept current by AppRepository). */
    @Volatile
    var themedIconStyle: String = LauncherSettings.THEMED_STYLE_CLASSIC

    /**
     * Builds a themed icon from [info]'s monochrome layer, or null if it has none (most non-Google
     * apps). The monochrome drawable is the adaptive-icon foreground glyph; it is tinted and laid
     * over the style's background as a fresh AdaptiveIconDrawable, so the platform applies the icon
     * mask and foreground scaling for us.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun themedIcon(info: LauncherActivityInfo, dark: Boolean): Drawable? {
        val adaptive = info.getIcon(0) as? AdaptiveIconDrawable ?: return null
        val mono = (adaptive.monochrome ?: return null).mutate()
        mono.setTint(styleForeground(dark))
        return AdaptiveIconDrawable(styleBackground(dark), mono)
    }

    /** The glyph colour of the chosen themed style. */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun styleForeground(dark: Boolean): Int {
        val (bg, fg) = themedColors(dark)
        return when (themedIconStyle) {
            LauncherSettings.THEMED_STYLE_INVERTED -> bg
            LauncherSettings.THEMED_STYLE_GLASS -> Color.argb(242, 255, 255, 255)
            else -> fg
        }
    }

    /** The plate behind the glyph: solid for most styles, a frosted see-through gradient for Glass. */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun styleBackground(dark: Boolean): Drawable {
        val (bg, fg) = themedColors(dark)
        return when (themedIconStyle) {
            LauncherSettings.THEMED_STYLE_INVERTED -> ColorDrawable(fg)
            LauncherSettings.THEMED_STYLE_GLASS -> {
                // Lighter at the top, like light catching a pane of glass, with a hint of the accent.
                val accent = context.getColor(android.R.color.system_accent1_300)
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(
                        Color.argb(110, 255, 255, 255),
                        Color.argb(70, Color.red(accent), Color.green(accent), Color.blue(accent)),
                        Color.argb(40, 255, 255, 255),
                    ),
                )
            }
            else -> ColorDrawable(bg)
        }
    }

    /**
     * A themed icon for an app that ships none, made from its own artwork. The artwork is reduced to
     * one colour: a small logo on nothing becomes its outline; a filled shape or a full picture keeps
     * what stands out from its main colour (the "M" on a red disc, the text on a logo plate), so it
     * no longer turns into a plain blob. In the Duotone style the whole picture is instead mapped onto
     * two theme colours by brightness, keeping every detail. When the result would still be
     * unrecognisable (almost empty, or almost entirely filled) this gives up and returns null, and
     * the app keeps its normal icon.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun forcedThemedIcon(info: LauncherActivityInfo, dark: Boolean): Drawable? {
        val icon = info.getIcon(0) ?: return null
        val adaptive = icon as? AdaptiveIconDrawable
        val edge = FORCED_ICON_EDGE
        val (themeBg, themeFg) = themedColors(dark)
        fun luminance(c: Int): Int = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000

        if (themedIconStyle == LauncherSettings.THEMED_STYLE_DUOTONE) {
            // The full picture (background layer included), each pixel placed between the two colours.
            val bitmap = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            if (adaptive != null) {
                adaptive.background?.mutate()?.let { it.setBounds(0, 0, edge, edge); it.draw(canvas) }
                adaptive.foreground?.mutate()?.let { it.setBounds(0, 0, edge, edge); it.draw(canvas) }
            } else {
                icon.mutate().setBounds(0, 0, edge, edge)
                icon.draw(canvas)
            }
            val pixels = IntArray(edge * edge)
            bitmap.getPixels(pixels, 0, edge, 0, 0, edge, edge)
            var low = 255
            var high = 0
            for (c in pixels) if (Color.alpha(c) > 128) { val l = luminance(c); low = minOf(low, l); high = maxOf(high, l) }
            val span = (high - low).coerceAtLeast(1)
            for (i in pixels.indices) {
                val c = pixels[i]
                val t = ((luminance(c) - low).toFloat() / span).coerceIn(0f, 1f)
                val r = (Color.red(themeBg) + (Color.red(themeFg) - Color.red(themeBg)) * t).toInt()
                val g = (Color.green(themeBg) + (Color.green(themeFg) - Color.green(themeBg)) * t).toInt()
                val b = (Color.blue(themeBg) + (Color.blue(themeFg) - Color.blue(themeBg)) * t).toInt()
                pixels[i] = Color.argb(Color.alpha(c), r, g, b)
            }
            bitmap.setPixels(pixels, 0, edge, 0, 0, edge, edge)
            val picture: Drawable = BitmapDrawable(context.resources, bitmap)
            return AdaptiveIconDrawable(ColorDrawable(themeBg), if (adaptive != null) picture else InsetDrawable(picture, 0.12f))
        }

        val artwork = (adaptive?.foreground ?: icon).mutate()
        val bitmap = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
        artwork.setBounds(0, 0, edge, edge)
        artwork.draw(Canvas(bitmap))
        val pixels = IntArray(edge * edge)
        bitmap.getPixels(pixels, 0, edge, 0, 0, edge, edge)

        // How much of the square the artwork covers, and its average brightness there.
        var solid = 0
        var solidLuminance = 0L
        for (c in pixels) if (Color.alpha(c) > 200) { solid++; solidLuminance += luminance(c) }
        val coverage = solid.toFloat() / pixels.size
        val tint = styleForeground(dark) and 0x00FFFFFF
        val tintAlpha = Color.alpha(styleForeground(dark))

        if (coverage < SILHOUETTE_MAX_COVERAGE) {
            // A small logo on nothing: its own shape is the glyph.
            for (i in pixels.indices) pixels[i] = ((Color.alpha(pixels[i]) * tintAlpha / 255) shl 24) or tint
        } else {
            // A filled shape or a full picture: keep what differs from its main colour.
            val base = (solidLuminance / solid.coerceAtLeast(1)).toInt()
            var strongest = 0
            for (c in pixels) if (Color.alpha(c) > 200) strongest = maxOf(strongest, kotlin.math.abs(luminance(c) - base))
            if (strongest < MIN_CONTRAST) return null // one flat colour: nothing to draw
            for (i in pixels.indices) {
                val c = pixels[i]
                val stand = (kotlin.math.abs(luminance(c) - base) * 255 / strongest).coerceIn(0, 255)
                // Soft edges of faint differences are dropped so noise doesn't smear the glyph.
                val keep = if (stand < 40) 0 else stand
                pixels[i] = ((Color.alpha(c) * keep / 255 * tintAlpha / 255).coerceIn(0, 255) shl 24) or tint
            }
        }

        // Is the result still a recognisable mark? Almost nothing drawn, or nearly everything
        // drawn, means no: the app keeps its normal icon.
        var drawn = 0
        for (c in pixels) if (Color.alpha(c) > 128) drawn++
        val drawnShare = drawn.toFloat() / pixels.size
        if (drawnShare < MIN_GLYPH_SHARE || drawnShare > MAX_GLYPH_SHARE) return null

        bitmap.setPixels(pixels, 0, edge, 0, 0, edge, edge)
        val glyph: Drawable = BitmapDrawable(context.resources, bitmap)
        // An adaptive foreground already carries the platform's margins; a plain icon fills its
        // square, so it is inset to sit inside the mask like the others.
        val layer = if (adaptive != null) glyph else InsetDrawable(glyph, 0.26f)
        return AdaptiveIconDrawable(styleBackground(dark), layer)
    }

    /** Background/foreground colours for themed icons, from the system dynamic (Monet) palette. */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun themedColors(dark: Boolean): Pair<Int, Int> = if (dark) {
        context.getColor(android.R.color.system_neutral1_800) to
            context.getColor(android.R.color.system_accent1_100)
    } else {
        context.getColor(android.R.color.system_accent1_100) to
            context.getColor(android.R.color.system_neutral2_700)
    }
}

private const val SPLIT_FIND_TRIES = 10
private const val SPLIT_RETRY_MS = 100L

/** Profile changes that change the app list (Private Space locked or unlocked among them). */
private val PROFILE_ACTIONS = listOf(
    "android.intent.action.PROFILE_AVAILABLE",
    "android.intent.action.PROFILE_UNAVAILABLE",
    "android.intent.action.PROFILE_ADDED",
    "android.intent.action.PROFILE_REMOVED",
    Intent.ACTION_MANAGED_PROFILE_AVAILABLE,
    Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE,
)
