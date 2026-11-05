package org.arkikeskus.launcher.feature.home

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Display
import android.view.WindowManager

/**
 * Where Folark's taskbar ([AppTaskbar]) lives.
 *
 * - **Overlay** (preferred): when the user allowed "Display over other apps", the bar runs in
 *   ordinary application overlay windows owned by Folark's own process. No accessibility service is
 *   needed for it, which is what app stores expect of a taskbar. While its windows show, Android
 *   keeps the process at "perceptible" priority, so no foreground service (and no notification) is
 *   needed either.
 * - **Accessibility** (fallback): without that permission, [LockAccessibilityService] hosts it in
 *   accessibility overlay windows, as before, if the user enabled the service.
 *
 * Only one host runs the bar at a time. [refresh] decides; it is called when Folark starts and each
 * time home comes back (the permission may have been given or taken in the meantime). Main thread.
 */
object TaskbarHost {

    /** The bar that runs now, whichever host owns it; null when none runs. */
    @Volatile
    internal var current: AppTaskbar? = null
        private set

    /** The overlay-hosted bar, with its window context and the callbacks registered on it. */
    private var overlayBar: AppTaskbar? = null
    private var overlayContext: Context? = null
    private var overlayCallbacks: ComponentCallbacks? = null

    /** Whether the bar runs in Folark's own overlay windows right now. */
    val overlayRunning: Boolean get() = overlayBar != null

    /** Whether Folark may display over other apps (the overlay host can run). */
    fun canUseOverlay(context: Context): Boolean = runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)

    /** Starts or stops the overlay host to match the permission; hands the bar over between hosts. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        if (canUseOverlay(app)) {
            if (overlayBar == null) {
                // The accessibility service gives its bar up first: two bars would double every window.
                LockAccessibilityService.releaseTaskbar()
                startOverlay(app)
            }
        } else if (overlayBar != null) {
            stopOverlay()
            LockAccessibilityService.hostTaskbarIfNeeded()
        }
    }

    private fun startOverlay(app: Context) {
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val windowContext = runCatching {
            val display = app.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
                ?: return@runCatching null
            app.createDisplayContext(display).createWindowContext(type, null)
        }.getOrNull() ?: return
        val bar = AppTaskbar(windowContext, type)
        val callbacks = object : ComponentCallbacks {
            // A fold or unfold: the bar only belongs on the large display.
            override fun onConfigurationChanged(newConfig: Configuration) {
                overlayBar?.onConfigurationChanged()
                // Unfolded again: the app watch paused on the cover screen picks up at once.
                checkNow()
            }

            @Deprecated("Required by ComponentCallbacks")
            override fun onLowMemory() = Unit
        }
        runCatching { windowContext.registerComponentCallbacks(callbacks) }
        overlayContext = windowContext
        overlayCallbacks = callbacks
        overlayBar = bar
        current = bar
        bar.start()
    }

    private fun stopOverlay() {
        val bar = overlayBar ?: return
        stopWatching()
        bar.stop()
        overlayCallbacks?.let { cb -> runCatching { overlayContext?.unregisterComponentCallbacks(cb) } }
        overlayBar = null
        overlayContext = null
        overlayCallbacks = null
        if (current === bar) current = null
    }

    /** The accessibility service took the bar (no overlay permission) or gave it back. */
    internal fun attachAccessibilityBar(bar: AppTaskbar?) {
        if (overlayBar == null) current = bar
    }

    // ---------------------------------------------------------------- the app in front

    /**
     * Per-app taskbar rules need to know which app is in front. The accessibility service hears it
     * directly when it is on; without it, the overlay host reads Android's usage events (Usage
     * access) about once a second after a switch, easing off to every 3 s while nothing changes, and only
     * while there are rules and the screen is on.
     */
    fun watchApps(context: Context, on: Boolean) {
        val main = Looper.getMainLooper()
        if (Looper.myLooper() != main) {
            Handler(main).post { watchApps(context, on) }
            return
        }
        wantWatch = on
        if (on && overlayBar != null && !LockAccessibilityService.isRunning) startWatching(context.applicationContext)
        else stopWatching()
    }

    /** The accessibility service saw [packageName] come to the front. */
    internal fun onForegroundApp(packageName: String) {
        current?.onForegroundApp(packageName)
    }

    private var wantWatch = false
    private var watcher: HandlerThread? = null
    private var watchHandler: Handler? = null
    private var lastEventAt = 0L
    private var lastForeground: String? = null
    private var watchTick: Runnable? = null
    @Volatile private var watchDelay = WATCH_FAST_MS

    /**
     * Looks right away instead of at the next tick, and restarts the watch after the screen was off
     * (it stops while the screen is off rather than waking every second for nothing). Called when the
     * screen comes on and when the bar is opened from a handle.
     */
    fun checkNow() {
        val handler = watchHandler ?: return
        val tick = watchTick ?: return
        handler.removeCallbacks(tick)
        watchDelay = WATCH_FAST_MS
        handler.post(tick)
    }

    /**
     * The app most recently brought to the front, read once from usage events (Usage access), for
     * when no watch is running yet. Not on the main thread. Null if unknown.
     */
    fun foregroundNow(context: Context): String? = runCatching {
        val usage = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val events = usage.queryEvents(now - FOREGROUND_LOOK_BACK_MS, now) ?: return null
        val event = UsageEvents.Event()
        var newest: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED &&
                event.packageName != "com.android.systemui" && event.packageName != context.packageName
            ) {
                newest = event.packageName
            }
        }
        newest
    }.getOrNull()

    private fun startWatching(app: Context) {
        if (watcher != null) return
        val thread = HandlerThread("folark-taskbar-apps").apply { start() }
        val handler = Handler(thread.looper)
        watcher = thread
        watchHandler = handler
        lastEventAt = System.currentTimeMillis() - FIRST_LOOK_BACK_MS
        watchDelay = WATCH_FAST_MS
        val usage = app.getSystemService(UsageStatsManager::class.java)
        val power = app.getSystemService(PowerManager::class.java)
        val main = Handler(Looper.getMainLooper())
        val tick = object : Runnable {
            override fun run() {
                if (watchHandler !== handler) return
                // Screen off: stop until checkNow() (screen on) starts it again.
                if (power?.isInteractive == false) return
                // Folded with no bar on the cover screen: nothing to follow; checkNow() (unfold)
                // starts it again. It asked the usage history every 1-3 s on the cover screen.
                if (current?.barPossibleHere() == false) return
                var changed = false
                if (usage != null) {
                    val now = System.currentTimeMillis()
                    var newest: String? = null
                    runCatching {
                        val events = usage.queryEvents(lastEventAt, now)
                        val event = UsageEvents.Event()
                        while (events.hasNextEvent()) {
                            events.getNextEvent(event)
                            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                                newest = event.packageName
                                lastEventAt = maxOf(lastEventAt, event.timeStamp)
                            }
                        }
                    }
                    // Never ask for more than a short window: after a quiet hour the query would
                    // otherwise span the whole hour on every tick. The overlap catches late writes.
                    lastEventAt = maxOf(lastEventAt, now - EVENT_OVERLAP_MS)
                    val pkg = newest
                    if (pkg != null && pkg != lastForeground && pkg != "com.android.systemui") {
                        lastForeground = pkg
                        changed = true
                        main.post { current?.onForegroundApp(pkg) }
                    }
                }
                // Back off while nothing changes (1 s, 1.5 s, 2.25 s … up to 3 s); a switch or
                // checkNow() brings it back to the fast pace.
                watchDelay = if (changed) WATCH_FAST_MS
                else minOf(WATCH_SLOW_MS, watchDelay * 3 / 2)
                handler.postDelayed(this, watchDelay)
            }
        }
        watchTick = tick
        handler.post(tick)
    }

    private fun stopWatching() {
        watchHandler?.removeCallbacksAndMessages(null)
        watchHandler = null
        watchTick = null
        watcher?.quitSafely()
        watcher = null
        lastForeground = null
    }

    /** The accessibility service started or stopped: it hears the app in front itself, or no longer. */
    internal fun onAccessibilityChanged(context: Context) {
        watchApps(context, wantWatch)
    }

    private const val WATCH_FAST_MS = 1_000L
    private const val WATCH_SLOW_MS = 3_000L
    private const val EVENT_OVERLAP_MS = 5_000L
    private const val FIRST_LOOK_BACK_MS = 10_000L
    private const val FOREGROUND_LOOK_BACK_MS = 30 * 60_000L
}
