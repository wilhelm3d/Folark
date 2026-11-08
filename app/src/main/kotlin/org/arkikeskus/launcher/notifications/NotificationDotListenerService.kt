package org.arkikeskus.launcher.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.BatchDelivery
import org.arkikeskus.launcher.data.BatchSchedule
import org.arkikeskus.launcher.data.MessageHeuristics
import org.arkikeskus.launcher.data.NotificationBadgeRepository
import org.arkikeskus.launcher.data.PeopleGrouping
import org.arkikeskus.launcher.data.runSuspendCatching
import org.arkikeskus.launcher.data.PersonEntry
import org.arkikeskus.launcher.data.PersonEventKind
import org.arkikeskus.launcher.data.ReplyAction
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.data.StatusNotification
import org.arkikeskus.launcher.model.LiveActivity
import org.arkikeskus.launcher.model.ScreenType
import javax.inject.Inject
import org.arkikeskus.launcher.model.catching

/**
 * Streams notification-dot counts into [NotificationBadgeRepository]. On every connect/post/remove we
 * recompute the full snapshot from [getActiveNotifications] (simple and always consistent) and group
 * the notifications by package + profile.
 *
 * Two filters, deliberately different (this is why the status-bar icons show more than the dots):
 * - **Dots** use the strict "badge-worthy" filter, mirroring AOSP Launcher3's
 *   `NotificationListener.notificationIsValidForUI`: the channel must allow badges, group summaries and
 *   content-less notifications are skipped, and ongoing notifications on the legacy default channel
 *   don't count. This keeps the home-icon dots meaningful.
 * - **Status-bar icons** use a looser "icon-worthy" filter (not a group summary + has a title or text),
 *   like the real system status bar — so low-priority/silent notifications whose channel sets
 *   `canShowBadge = false` (e.g. Google News) still show their glyph in the bar.
 * - **People tiles** take only conversation-like notifications (messages, calls, mail — see
 *   [personEntry]) and reduce each to its sender, newest text and quick actions; the repository
 *   groups them by person across apps.
 * - **Batch delivery** (opt-in) keeps the launcher's dots and status icons quiet until delivery;
 *   people tiles show "waiting". Android notifications stay active, so updates to the same key
 *   still arrive here and can bypass the batch for pinned people, missed calls and one-time codes.
 *
 * Requires the user to grant notification access (Settings → Notifications → Device & app
 * notifications); until then the system never binds this service.
 */
@AndroidEntryPoint
class NotificationDotListenerService : NotificationListenerService() {

    @Inject
    lateinit var badgeRepository: NotificationBadgeRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    /** Lives while the listener is connected; carries the batch settings + pins into [holdForBatch]. */
    private var settingsJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile private var batchEnabled = false
    @Volatile private var batchTimes: List<Int> = emptyList()
    /** Person keys that always come through: pinned people and the aliases that merge into them. */
    @Volatile private var vipKeys: Set<String> = emptySet()

    /** Packages that handle mailto: links — the mail clients. Their notifications carry the sender
     *  as the title and the subject as the text, but almost none set CATEGORY_EMAIL (Gmail doesn't),
     *  so the app being a mail client is the signal. Re-read on connect and package changes. */
    @Volatile private var mailPackages: Set<String> = emptySet()

    /** Notification channels hidden per package (the widget's mute action); read at each snapshot. */
    @Volatile private var hiddenChannels: Map<String, Set<String>> = emptyMap()

    /** Local delivery deadlines, including legacy snoozes made by earlier beta versions. */
    private val heldUntil = HashMap<String, Long>()
    private var heldLoaded = false
    private var settingsReady = false

    private fun persistHeld() {
        val snapshot = HashMap(heldUntil)
        scope.launch { catching { settingsRepository.setHeldNotifications(snapshot) } }
    }

    private val userManager by lazy { getSystemService(UserManager::class.java) }

    /** Keys that have already fired a heads-up, so a FLAG_ONLY_ALERT_ONCE re-post doesn't re-trigger.
     *  Touched only from NLS callbacks (main thread), so it needs no synchronisation. */
    private val alertedKeys = HashSet<String>()

    private val handler = Handler(Looper.getMainLooper())
    private var snapshotRetriesLeft = 0
    private val retryRefresh = Runnable { refresh() }
    private val deliverBatch = Runnable { refreshWithRetries() }
    private var connected = false
    private var packageRefreshJob: Job? = null
    private var receiverRegistered = false
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            appLabels.clear()
            // An uninstalled app's held notifications would sit in the map (and the settings
            // store) forever: forget them.
            if (intent?.action == Intent.ACTION_PACKAGE_REMOVED) {
                intent.data?.schemeSpecificPart?.let { pruneHeldFor(it) }
            }
            refreshMailPackages()
        }
    }
    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (heldUntil.isNotEmpty()) refreshWithRetries()
        }
    }

    private companion object {
        /** Notification categories that are about something going on now. */
        val LIVE_CATEGORIES = setOf(
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_NAVIGATION,
            Notification.CATEGORY_STOPWATCH,
            Notification.CATEGORY_WORKOUT,
            Notification.CATEGORY_LOCATION_SHARING,
        )

        const val TAG = "NotifDots"

        /** Cap on the held-notification map (and the settings store it persists to). */
        const val HELD_MAX = 300
        const val MAX_SNAPSHOT_RETRIES = 2
        const val SNAPSHOT_RETRY_DELAY_MS = 500L

        /** How long posts and removals are gathered into one rebuild (well under a frame's notice). */
        const val COALESCE_MS = 40L
        /** Updates of an ongoing notification already shown: at most one rebuild a second. */
        const val QUIET_UPDATE_MS = 1_000L

        /** Floor between full snapshot rebuilds: a sustained stream of posts (a mail sync, a group
         *  chat) is not an ongoing update, so it would otherwise rebuild up to 25 times a second. */
        const val MIN_FULL_REBUILD_MS = 250L

        /** Grace period for legacy snoozes to reappear before deleting stale bookkeeping. */
        const val RELEASE_SLACK_MS = 60_000L
    }

    /** Refresh now, allowing a bounded number of delayed retries if the snapshot read fails —
     *  without one, a single transient Binder failure would leave a removed notification visible
     *  until the next callback happens to fire. */
    private fun refreshWithRetries() {
        snapshotRetriesLeft = MAX_SNAPSHOT_RETRIES
        handler.removeCallbacks(retryRefresh)
        handler.removeCallbacks(coalescedRefresh)
        refreshQueued = false
        refresh()
    }

    /**
     * For posts and removals: one rebuild for a burst. A messaging app waking from Doze can post a
     * dozen notifications within a few ms; each rebuild reads Android's whole list and redraws the
     * dots, the people tiles and the notifications widget, so they are gathered for [COALESCE_MS]
     * and done once. A steady stream still refreshes every [COALESCE_MS], never later.
     */
    private fun scheduleRefresh(delayMs: Long = COALESCE_MS) {
        // A full rebuild supersedes any pending live-only update.
        if (liveRefreshQueued) {
            handler.removeCallbacks(liveRefreshRunnable)
            liveRefreshQueued = false
        }
        // A full rebuild is deferred to the floor, never dropped: nothing is lost, it is only
        // gathered (the snapshot read copies every active notification over Binder).
        val sinceLast = android.os.SystemClock.uptimeMillis() - lastFullRefreshAt
        val wait = maxOf(delayMs, MIN_FULL_REBUILD_MS - sinceLast)
        // Already coming at least as soon: nothing to do. A slow one waiting is brought forward.
        if (refreshQueued && queuedDelay <= wait) return
        handler.removeCallbacks(coalescedRefresh)
        refreshQueued = true
        queuedDelay = wait
        handler.postDelayed(coalescedRefresh, wait)
    }

    /**
     * Ongoing notifications seen before (a download, a timer, music, navigation): their updates
     * come several times a second and change only a progress or a time, so they rebuild at most
     * every [QUIET_UPDATE_MS] instead of after [COALESCE_MS]. Every rebuild reads Android's whole
     * list and redraws dots, people tiles, widgets and the live pill.
     */
    private val ongoingKeys = HashSet<String>()

    private fun isQuietUpdate(sbn: StatusBarNotification): Boolean {
        val flags = sbn.notification?.flags ?: return false
        val ongoing = (flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE)) != 0
        if (!ongoing) return false
        return !ongoingKeys.add(sbn.key)
    }

    private var queuedDelay = 0L
    private var refreshQueued = false

    /** When the last full snapshot began (uptime millis): see [MIN_FULL_REBUILD_MS]. */
    private var lastFullRefreshAt = 0L
    private val coalescedRefresh = Runnable {
        refreshQueued = false
        refreshWithRetries()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "listener connected")
        connected = true
        settingsReady = false
        badgeRepository.registerCanceller { key -> catching { cancelNotification(key) } }
        badgeRepository.registerAllCanceller { excludeKeys -> 
            catching {
                val activeKeys = activeNotifications.map { it.key }
                val toCancel = activeKeys.filter { it !in excludeKeys }
                toCancel.forEach { cancelNotification(it) }
            }
        }
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            ContextCompat.registerReceiver(this, timeReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_SCREEN_ON)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
        mailPackages = queryMailPackages()
        settingsJob?.cancel()
        // Hidden notification channels (the widget's mute action): kept current for the snapshot filter.
        scope.launch {
            settingsRepository.hiddenChannels.collect { hiddenChannels = it }
        }
        settingsJob = scope.launch {
            if (!heldLoaded) {
                val saved = runSuspendCatching { settingsRepository.heldNotifications.first() }.getOrDefault(emptyMap())
                for ((k, v) in saved) heldUntil.putIfAbsent(k, v)
                heldLoaded = true
            }
            combine(
                settingsRepository.settings(ScreenType.OUTER), settingsRepository.pinnedPeople, settingsRepository.peopleAliases,
            ) { s, pinned, aliases ->
                val pins = pinned.map { it.key }.toSet()
                Triple(s.widgets.peopleBatchEnabled, BatchSchedule.parse(s.widgets.peopleBatchTimes), pins + aliases.filterValues { it in pins }.keys)
            }.distinctUntilChanged().onEach { (enabled, times, vips) ->
                batchEnabled = enabled
                batchTimes = times
                vipKeys = vips
                settingsReady = true
                refreshWithRetries()
            }.collect {}
        }
        refreshWithRetries()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        connected = false
        handler.removeCallbacks(retryRefresh)
        handler.removeCallbacks(deliverBatch)
        handler.removeCallbacks(coalescedRefresh)
        handler.removeCallbacks(liveRefreshRunnable)
        refreshQueued = false
        liveRefreshQueued = false
        stopPackageRefresh()
        settingsJob?.cancel()
        settingsJob = null
        badgeRepository.clearCanceller()
        badgeRepository.clearAllCanceller()
        badgeRepository.setBadges(emptyMap())
        badgeRepository.setIcons(emptyList())
        badgeRepository.setAllNotifications(emptyList())
        badgeRepository.setPeople(emptyList())
        alertedKeys.clear()
        nudgedKeys.clear()
        // Aggressive OEM battery managers (Samsung, Xiaomi, …) can unbind the listener; ask the system
        // to rebind so dots + status-bar icons come back on their own instead of the user having to
        // re-toggle notification access. No-op if the system declines.
        catching { requestRebind(ComponentName(this, NotificationDotListenerService::class.java)) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // A heads-up post makes the system transiently show ITS status bar over our content; tell the
        // repo so the home screen can blank the themed bar for that window (the reveal isn't dispatched
        // as an inset, so this is the only app-observable signal — see the audit note in StatusBar).
        if (sbn != null && isHeadsUpWorthy(sbn) && shouldAlert(sbn)) badgeRepository.notifyHeadsUp()
        if (sbn != null && settingsReady) holdForBatch(sbn)
        if (sbn != null) catching { maybeNudge(sbn) }
        // FIX #3: for quiet updates (ongoing notifications whose progress/time changed — timers,
        // navigation, music, downloads) only the live pill needs to change. Avoid a full snapshot
        // rebuild (Binder IPC + badge/people/icon re-sort) up to 60 times a minute by updating
        // only the live list. A full rebuild is still issued when a genuinely new or removed
        // notification arrives (COALESCE_MS path).
        if (sbn != null && isQuietUpdate(sbn)) {
            scheduleLiveRefresh()
        } else {
            scheduleRefresh()
        }
    }

    /** Handler runnable for the lightweight live-only update. */
    private val liveRefreshRunnable = Runnable { refreshLiveOnly() }
    private var liveRefreshQueued = false

    /**
     * Schedules a lightweight live-pill-only refresh, coalesced like the full refresh.
     * Cancelled and superseded whenever a full [scheduleRefresh] is queued (a full rebuild
     * already updates the live list too).
     */
    private fun scheduleLiveRefresh() {
        if (refreshQueued || liveRefreshQueued) return
        liveRefreshQueued = true
        handler.postDelayed(liveRefreshRunnable, QUIET_UPDATE_MS)
    }

    /**
     * Lightweight refresh that updates only [badgeRepository.setLive]: iterates active
     * notifications to recompute live-activity entries (progress values, elapsed times) without
     * touching badge counts, status-bar icons, or people tiles. Used for ongoing-notification
     * quiet updates (timers, downloads, navigation) where only the live pill changes.
     */
    private fun refreshLiveOnly() {
        liveRefreshQueued = false
        if (!connected || !heldLoaded) return
        val active = catching { activeNotifications }.getOrNull() ?: return
        val ranking = catching { currentRanking }.getOrNull()
        val tmp = Ranking()
        val live = ArrayList<LiveActivity>()
        for (sbn in active) {
            if (sbn == null) continue
            val serial = catching { userManager?.getSerialNumberForUser(sbn.user) }.getOrNull() ?: 0L
            catching { liveEntry(sbn, serial) }.getOrNull()?.let(live::add)
        }
        badgeRepository.setLive(live.sortedWith(compareByDescending<LiveActivity> { livePriority(it) }.thenByDescending { it.postTime }))
    }

    /** Notifications that already nudged their app's icon (a silent update must not nudge again). */
    private val nudgedKeys = HashSet<String>()

    /**
     * A new notification someone would want to notice: its app's icon nudges on home and on the
     * taskbar. Not for ongoing ones, silent ones, ones held for the batch, or quiet updates.
     *
     * FIX #2: the icon-colour bitmap decode (createBitmap + draw + getPixels) used to run on the
     * NLS main thread. All gate checks still run on main for thread-safety (nudgedKeys, heldUntil);
     * only the bitmap work is dispatched to Default when the notification itself carries no colour.
     */
    private fun maybeNudge(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        if ((n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE)) != 0) return
        if (sbn.key in heldUntil) return
        val first = nudgedKeys.add(sbn.key)
        if (!first && (n.flags and Notification.FLAG_ONLY_ALERT_ONCE) != 0) return
        val ranking = catching { currentRanking }.getOrNull()
        val tmp = Ranking()
        if (!isBadgeWorthy(sbn, ranking, tmp)) return
        if (ranking?.getRanking(sbn.key, tmp) == true && tmp.importance < NotificationManager.IMPORTANCE_DEFAULT) return
        val serial = catching { userManager?.getSerialNumberForUser(sbn.user) }.getOrNull() ?: 0L
        val notifColor = n.color.takeIf { it != Notification.COLOR_DEFAULT && it != 0 }
        val pkg = sbn.packageName
        val badgeKey = "${pkg}/$serial"
        if (notifColor != null) {
            // Fast path: the notification itself supplies a colour; no bitmap work needed.
            badgeRepository.nudge(badgeKey, notifColor)
        } else {
            // Slow path: decode the app icon to find its brand colour off the main thread.
            scope.launch(Dispatchers.Default) {
                badgeRepository.nudge(badgeKey, iconColorOffMain(pkg))
            }
        }
    }

    /** Icon colours by "package:lastUpdateTime", so an app update (a new icon) is coloured afresh. */
    private val iconColors = HashMap<String, Int?>()

    /**
     * The most vivid colour of an app's icon (its brand colour, usually), or null for a grey one.
     * Safe to call from any thread; synchronised so the cache HashMap is not mutated concurrently
     * when multiple notifications arrive back-to-back for different packages.
     */
    @Synchronized
    private fun iconColorOffMain(pkg: String): Int? {
        val updated = catching { packageManager.getPackageInfo(pkg, 0).lastUpdateTime }.getOrDefault(0L)
        val key = "$pkg:$updated"
        if (iconColors.containsKey(key)) return iconColors[key]
        // An older entry of the same app is stale now.
        iconColors.keys.removeAll { it.startsWith("$pkg:") }
        return computeIconColor(pkg).also { iconColors[key] = it }
    }

    /** Kept for any remaining internal call sites; delegates to the synchronised version. */
    private fun iconColor(pkg: String): Int? = iconColorOffMain(pkg)

    private fun computeIconColor(pkg: String): Int? =
        catching {
            val drawable = packageManager.getApplicationIcon(pkg)
            val size = 24
            val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(android.graphics.Canvas(bitmap))
            val hsv = FloatArray(3)
            var weight = 0f
            var r = 0f
            var g = 0f
            var b = 0f
            // One call for all pixels instead of one per pixel.
            val pixels = IntArray(size * size)
            bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
            for (c in pixels) {
                if (android.graphics.Color.alpha(c) < 200) continue
                android.graphics.Color.colorToHSV(c, hsv)
                // Vivid, not too dark or washed out, counts most.
                val w = hsv[1] * hsv[1] * hsv[2]
                if (w < 0.05f) continue
                weight += w
                r += android.graphics.Color.red(c) * w
                g += android.graphics.Color.green(c) * w
                b += android.graphics.Color.blue(c) * w
            }
            bitmap.recycle()
            if (weight < 1f) null else android.graphics.Color.rgb((r / weight).toInt(), (g / weight).toInt(), (b / weight).toInt())
        }.getOrNull()

    /** Keep the notification active in Android so every new message can be checked for exceptions. */
    private fun holdForBatch(sbn: StatusBarNotification) {
        val now = System.currentTimeMillis()
        val previous = heldUntil[sbn.key]
        val entry = catching { personEntry(sbn, 0L, currentRanking, Ranking()) }.getOrNull()
        val until = BatchDelivery.holdUntil(
            entry, previous, now, batchEnabled, vipKeys, BatchSchedule.nextDelivery(now, batchTimes),
        )
        if (until == null) heldUntil.remove(sbn.key) else heldUntil[sbn.key] = until
        // A cap keeps a pathological app (thousands of notifications held at once) from growing
        // the map and the settings store without end: the soonest-expiring entries go first.
        while (heldUntil.size > HELD_MAX) {
            heldUntil.entries.minByOrNull { it.value }?.let { heldUntil.remove(it.key) } ?: break
        }
        if (until != previous) persistHeld()
    }

    /** Notification keys are "user|package|id|tag": forget the held ones for an uninstalled [pkg]. */
    private fun pruneHeldFor(pkg: String) {
        val gone = heldUntil.keys.filter { it.substringAfter('|').substringBefore('|') == pkg }
        if (gone.isEmpty()) return
        gone.forEach(heldUntil::remove)
        persistHeld()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        if (sbn != null) alertedKeys.remove(sbn.key)
        if (sbn != null) nudgedKeys.remove(sbn.key)
        if (sbn != null) ongoingKeys.remove(sbn.key)
        // A user snoozing an already-held notification leaves its deadline intact. Only a real
        // removal (dismissed or cancelled by the app) forgets it.
        if (sbn != null && reason != REASON_SNOOZED && heldUntil.remove(sbn.key) != null) persistHeld()
        scheduleRefresh()
    }

    /** A FLAG_ONLY_ALERT_ONCE notification heads-ups only on its FIRST post; a re-post (same key) must
     *  not re-trigger (mirrors SystemUI's alertAgain / shouldHunAgain) — otherwise a frequently-updating
     *  ongoing HIGH-importance notification would keep the themed bar blanked forever. */
    private fun shouldAlert(sbn: StatusBarNotification): Boolean {
        val firstTime = alertedKeys.add(sbn.key)
        val onlyOnce = ((sbn.notification?.flags ?: 0) and Notification.FLAG_ONLY_ALERT_ONCE) != 0
        return firstTime || !onlyOnce
    }

    private fun refresh() {
        if (!connected || !heldLoaded) return
        lastFullRefreshAt = android.os.SystemClock.uptimeMillis()
        val active = catching { activeNotifications }.getOrNull()
        if (active == null) {
            // A transient Binder failure: keep the last good snapshot (a stale removal self-heals on
            // the next post/remove callback) but leave a trail so a persistent failure is visible.
            Log.w(TAG, "activeNotifications unavailable; keeping the previous snapshot")
            if (snapshotRetriesLeft > 0) {
                snapshotRetriesLeft--
                handler.postDelayed(retryRefresh, SNAPSHOT_RETRY_DELAY_MS)
            }
            return
        }
        val ranking = catching { currentRanking }.getOrNull()
        val tmp = Ranking()
        val counts = HashMap<String, Int>()
        val iconCounts = HashMap<String, Int>()
        val visual = LinkedHashMap<String, StatusNotification>()
        val openable = HashMap<String, StatusNotification>()
        val allNotifs = ArrayList<StatusNotification>()
        val people = ArrayList<PersonEntry>()
        val live = ArrayList<LiveActivity>()
        val now = System.currentTimeMillis()
        val activeKeys = active.filterNotNull().map { it.key }.toSet()
        val released = heldUntil.filter { (key, until) ->
            settingsReady && key in activeKeys && (!batchEnabled || now >= until)
        }
        if (released.isNotEmpty()) {
            released.keys.forEach(heldUntil::remove)
            persistHeld()
        }
        for (sbn in active) {
            if (sbn == null) continue
            val serial = catching { userManager?.getSerialNumberForUser(sbn.user) }.getOrNull() ?: 0L
            val key = "${sbn.packageName}/$serial"
            // A channel hidden for this app (the notification widget's mute action): none of Folark's
            // surfaces show it — dots, status icons, widgets, people tiles. Android's shade is untouched.
            if (sbn.notification?.channelId in (hiddenChannels[sbn.packageName] ?: emptySet())) continue
            // People tiles: conversation-like notifications reduced to sender + text + actions; any
            // other real (icon-worthy, not ongoing) notification becomes an app-grouped entry.
            val person = catching { personEntry(sbn, serial, ranking, tmp) }.getOrNull()
            when {
                person != null -> people.add(person.copy(heldUntil = heldUntil[sbn.key] ?: 0L))
                isIconWorthy(sbn) && (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) == 0 ->
                    catching { appEntry(sbn, serial) }.getOrNull()?.let(people::add)
            }
            // Live pill: what is going on right now (never held: it is about this moment).
            catching { liveEntry(sbn, serial) }.getOrNull()?.let(live::add)
            // Only launcher surfaces wait. Android's shade, sound and heads-up remain unchanged.
            if (sbn.key in heldUntil) continue
            // Dots: strict badge-worthy filter (meaningful home-icon badges).
            if (isBadgeWorthy(sbn, ranking, tmp)) {
                counts[key] = (counts[key] ?: 0) + 1
            }
            // Status-bar icons + the notifications widget: looser filter so silent/low-priority
            // notifs (Google News etc.) show too. One entry per app — the most recent — carrying
            // the app's icon-worthy total so the widget's count always matches what it lists.
            if (isIconWorthy(sbn)) {
                val smallIcon = sbn.notification?.smallIcon ?: continue
                iconCounts[key] = (iconCounts[key] ?: 0) + 1
                val flags = sbn.notification?.flags ?: 0
                val extras = sbn.notification?.extras
                val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                val color = if (sbn.notification?.color != Notification.COLOR_DEFAULT) sbn.notification?.color else null
                val actions = sbn.notification?.actions?.toList() ?: emptyList()
                val entry = StatusNotification(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    icon = smallIcon,
                    postTime = sbn.postTime,
                    userSerial = serial,
                    contentIntent = sbn.notification?.contentIntent,
                    autoCancel = (flags and Notification.FLAG_AUTO_CANCEL) != 0,
                    preferSmallIcon = extras?.getBoolean(Notification.EXTRA_PREFER_SMALL_ICON) == true,
                    title = title,
                    text = text,
                    color = color,
                    actions = actions,
                    channelId = sbn.notification?.channelId,
                )
                
                val isSilent = if (ranking != null && ranking.getRanking(sbn.key, tmp)) {
                    tmp.importance < NotificationManager.IMPORTANCE_DEFAULT
                } else false
                val isOngoing = sbn.isOngoing

                if (!isSilent && !isOngoing) {
                    allNotifs.add(entry)
                }

                val curVisual = visual[key]
                if (curVisual == null || sbn.postTime > curVisual.postTime) visual[key] = entry
                if (entry.contentIntent != null) {
                    val curOpen = openable[key]
                    if (curOpen == null || sbn.postTime > curOpen.postTime) openable[key] = entry
                }
            }
        }
        Log.d(TAG, "badge snapshot: ${counts.size} app(s) badged")
        badgeRepository.setBadges(counts)
        badgeRepository.setIcons(
            visual.map { (k, v) ->
                // Visual icon = newest notification; tap action = newest OPENABLE notification, so a
                // newer intentless post never buries an older tappable one. count = app's total.
                val open = openable[k]
                v.copy(
                    count = iconCounts[k] ?: 1,
                    key = open?.key ?: v.key,
                    contentIntent = open?.contentIntent,
                    autoCancel = open?.autoCancel ?: false,
                )
            }.sortedByDescending { it.postTime },
        )
        badgeRepository.setAllNotifications(allNotifs.sortedByDescending { it.postTime })
        badgeRepository.setLive(live.sortedWith(compareByDescending<LiveActivity> { livePriority(it) }.thenByDescending { it.postTime }))
        // Migration: earlier betas snoozed in Android. Those existing snoozes cannot be released
        // through the public listener API; keep showing them until Android delivers them.
        if (heldUntil.isNotEmpty()) {
            val snoozed = catching { snoozedNotifications }.getOrNull().orEmpty().filterNotNull()
            val snoozedKeys = snoozed.map { it.key }.toSet()
            for (sbn in snoozed) {
                if (sbn.key in activeKeys) continue
                val until = heldUntil[sbn.key] ?: continue
                val serial = catching { userManager?.getSerialNumberForUser(sbn.user) }.getOrNull() ?: 0L
                val entry = catching { personEntry(sbn, serial, ranking, tmp) }.getOrNull() ?: continue
                people.add(entry.copy(heldUntil = until))
            }
            // Forget entries whose notification is gone for good (delivered and then dismissed, or
            // cancelled by its app while snoozed), so the persisted map doesn't grow forever.
            val stale = heldUntil.filter { (k, until) -> k !in activeKeys && k !in snoozedKeys && now > until + RELEASE_SLACK_MS }
            if (stale.isNotEmpty()) {
                stale.keys.forEach { heldUntil.remove(it) }
                persistHeld()
            }
        }
        badgeRepository.setPeople(PeopleGrouping.group(people))
        handler.removeCallbacks(deliverBatch)
        if (settingsReady) {
            heldUntil.filterKeys { it in activeKeys }.values.minOrNull()?.let { until ->
                handler.postDelayed(deliverBatch, (until - now).coerceAtLeast(1L))
            }
        }
    }

    /**
     * Reduces a notification to a [PersonEntry] when it is about a person, else null. Recognized, in
     * order: a MessagingStyle (any chat app; newest message + its sender), a call / missed call, a
     * system-flagged conversation or CATEGORY_MESSAGE (sender = title), and mail — CATEGORY_EMAIL
     * or any notification from a mail client (sender = title, subject = text). Group summaries and
     * ongoing notifications (an active call, "now playing") are skipped.
     */
    private fun personEntry(
        sbn: StatusBarNotification, serial: Long, ranking: RankingMap?, tmp: Ranking,
    ): PersonEntry? {
        val n = sbn.notification ?: return null
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return null
        if ((n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return null
        val extras = n.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        var subject: String? = null
        val ranked = ranking?.getRanking(sbn.key, tmp) == true
        // Ranking's conversation APIs are public from Android 12. Android 11 still uses the
        // MessagingStyle, category and title fallbacks below to identify people and batch messages.
        val conversation = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ranked && tmp.isConversation
        val shortcutLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ranked) {
            tmp.conversationShortcutInfo?.shortLabel?.toString()
        } else null

        var name: String
        var preview: String? = text
        var count = 1
        var icon: android.graphics.drawable.Icon? = null
        var uri: String? = null
        var sender: String? = null
        // A chat app's large icon is the contact's or group's picture; a dialer's, the caller's.
        val largeIcon = catching { n.getLargeIcon() }.getOrNull()
        val kind: PersonEventKind
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        when {
            style != null -> {
                // The style also carries the user's own replies: they must not count, name the tile
                // or be its preview.
                val messages = style.messages.filter { !it.text.isNullOrBlank() && !isOwnMessage(it.person, style.user) }
                if (messages.isEmpty()) return null
                val last = messages.last()
                val groupTitle = style.conversationTitle?.toString()?.trim()
                    ?.takeIf { style.isGroupConversation && it.isNotEmpty() }
                val lastSender = last.person?.name?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                name = groupTitle ?: lastSender.orEmpty().ifEmpty { shortcutLabel ?: title }
                preview = last.text?.toString()?.trim()
                count = messages.size
                // The tile is the group when there is one, so its picture beats the sender's.
                val personIcon = last.person?.icon?.toIcon(this)
                icon = if (groupTitle != null) largeIcon ?: personIcon else personIcon ?: largeIcon
                if (groupTitle != null) sender = lastSender
                uri = last.person?.uri
                kind = PersonEventKind.MESSAGE
            }
            n.category == Notification.CATEGORY_MISSED_CALL || n.category == Notification.CATEGORY_CALL -> {
                // Dialers disagree on which field carries the caller: Google's "Missed call" / "Mikko"
                // vs. Samsung's "Mikko" / "Missed call". The people list settles it when present;
                // otherwise the field that reads like the generic label is the label.
                val person = catching {
                    @Suppress("DEPRECATION")
                    extras.getParcelableArrayList<android.app.Person>(Notification.EXTRA_PEOPLE_LIST)?.firstOrNull()
                }.getOrNull()
                val swap = MessageHeuristics.looksLikeCallLabel(title) && !text.isNullOrEmpty() &&
                    !MessageHeuristics.looksLikeCallLabel(text)
                name = person?.name?.toString()?.trim().orEmpty().ifEmpty { if (swap) text.orEmpty() else title }
                preview = if (swap) title else text
                icon = person?.icon ?: largeIcon
                uri = person?.uri
                kind = PersonEventKind.MISSED_CALL
            }
            conversation || n.category == Notification.CATEGORY_MESSAGE -> {
                name = shortcutLabel ?: title
                icon = largeIcon
                kind = PersonEventKind.MESSAGE
            }
            n.category == Notification.CATEGORY_EMAIL || (sbn.packageName in mailPackages && n.category == null) -> {
                // A mail client's other notices ("Syncing…", "Sending…") carry no sender/subject pair
                // or are ongoing, and its calendar reminders declare their own category; a real mail
                // has a sender and a subject and, Gmail included, usually no category at all.
                if (title.isEmpty() || text == null) return null
                name = title
                // Mail clients put the subject in the text and the body's first lines in the
                // expanded (big) text; show the subject as a title line and the body under it.
                subject = text
                preview = bigText?.removePrefix(text)?.trim()?.takeIf { it.isNotEmpty() } ?: text
                kind = PersonEventKind.EMAIL
            }
            else -> return null
        }
        if (name.isBlank()) return null

        val reply = replyAction(n, conversationOnly = true)
        val callBack = n.actions
            ?.firstOrNull { it.semanticAction == Notification.Action.SEMANTIC_ACTION_CALL }
            ?.actionIntent
        return PersonEntry(
            key = sbn.key,
            name = name,
            title = subject,
            text = preview,
            postTime = sbn.postTime,
            packageName = sbn.packageName,
            userSerial = serial,
            kind = kind,
            count = count,
            contentIntent = n.contentIntent,
            autoCancel = (n.flags and Notification.FLAG_AUTO_CANCEL) != 0,
            reply = reply,
            callBack = callBack,
            personIcon = icon,
            sender = sender,
            personUri = uri,
        )
    }

    /** Any other notification as an entry under its app: label as the name, title + text shown. */
    private fun appEntry(sbn: StatusBarNotification, serial: Long): PersonEntry? {
        val n = sbn.notification ?: return null
        // An empty incoming MessagingStyle is deliberately absent, not an ordinary app notice.
        if (NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n) != null) return null
        val extras = n.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val body = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        // Some apps repeat the title at the start of the text; the tile already shows the title.
        val text = if (title != null && body != null) body.removePrefix(title).trim().ifEmpty { body } else body
        if (title == null && text == null) return null
        val label = appLabels.getOrPut(sbn.packageName) {
            catching {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
            }.getOrDefault(sbn.packageName)
        }
        val reply = replyAction(n, conversationOnly = false)
        return PersonEntry(
            key = sbn.key,
            name = label,
            groupKey = "app:${sbn.packageName}/$serial",
            title = title,
            text = text,
            postTime = sbn.postTime,
            packageName = sbn.packageName,
            userSerial = serial,
            kind = PersonEventKind.APP,
            contentIntent = n.contentIntent,
            autoCancel = (n.flags and Notification.FLAG_AUTO_CANCEL) != 0,
            reply = reply,
        )
    }

    /** App labels by package, filled lazily; a label rarely changes while the listener lives. */
    private val appLabels = HashMap<String, String>()

    private fun refreshMailPackages() {
        packageRefreshJob?.cancel()
        packageRefreshJob = scope.launch {
            val packages = withContext(Dispatchers.IO) { queryMailPackages() }
            if (!connected) return@launch
            mailPackages = packages
            refreshWithRetries()
        }
    }

    private fun stopPackageRefresh() {
        packageRefreshJob?.cancel()
        packageRefreshJob = null
        if (receiverRegistered) {
            unregisterReceiver(packageReceiver)
            unregisterReceiver(timeReceiver)
            receiverRegistered = false
        }
    }

    override fun onDestroy() {
        connected = false
        handler.removeCallbacksAndMessages(null)
        liveRefreshQueued = false
        stopPackageRefresh()
        scope.cancel()
        super.onDestroy()
    }

    /** The installed apps that offer to compose mail (handle mailto:), i.e. the mail clients. */
    private fun queryMailPackages(): Set<String> = catching {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:someone@example.com"))
        packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .filter { it != packageName }
            .toSet()
    }.getOrDefault(emptySet())

    /**
     * The first free-text RemoteInput action, as a [ReplyAction]. For a person's notification only
     * a reply (or an unlabelled) semantic counts — a chat app's "mark as read" with a text field
     * must not become the tile's reply; an app tile takes any text input it offers.
     */
    private fun replyAction(n: Notification, conversationOnly: Boolean): ReplyAction? =
        n.actions?.firstNotNullOfOrNull { action ->
            val input = action.remoteInputs?.firstOrNull { it.allowFreeFormInput } ?: return@firstNotNullOfOrNull null
            val semantic = action.semanticAction
            if (conversationOnly && semantic != Notification.Action.SEMANTIC_ACTION_REPLY &&
                semantic != Notification.Action.SEMANTIC_ACTION_NONE
            ) return@firstNotNullOfOrNull null
            action.actionIntent?.let { ReplyAction(it, input) }
        }

    /** A message without a sender (legacy style) or from the style's own user is the user's. The
     *  persons come from separate bundles, so they are matched by key, else uri, else name. */
    private fun isOwnMessage(person: Person?, user: Person): Boolean {
        if (person == null) return true
        person.key?.let { return it == user.key }
        person.uri?.let { return it == user.uri }
        return person.name?.toString() == user.name?.toString()
    }


    /**
     * Approximates SystemUI's heads-up decision (NotificationInterruptStateProvider.shouldHeadsUp): a
     * high-importance (or full-screen-intent) notification that isn't peek-suppressed. On a match the
     * system transiently reveals its own status bar over our content. A false positive only briefly
     * blanks the themed bar, so the check is deliberately lenient.
     */
    private fun isHeadsUpWorthy(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification ?: return false
        val r = Ranking()
        val ranked = catching { currentRanking?.getRanking(sbn.key, r) == true }.getOrDefault(false)
        val importance = if (ranked) r.importance else NotificationManager.IMPORTANCE_DEFAULT
        val peekSuppressed = ranked &&
            (r.suppressedVisualEffects and NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK) != 0
        return !peekSuppressed &&
            (importance >= NotificationManager.IMPORTANCE_HIGH || n.fullScreenIntent != null)
    }

    private fun isBadgeWorthy(sbn: StatusBarNotification, ranking: RankingMap?, tmp: Ranking): Boolean {
        val n = sbn.notification ?: return false
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return false
        if (ranking?.getRanking(sbn.key, tmp) == true) {
            if (!tmp.canShowBadge()) return false
            if (tmp.channel?.id == NotificationChannel.DEFAULT_CHANNEL_ID &&
                (n.flags and Notification.FLAG_ONGOING_EVENT) != 0
            ) {
                return false
            }
        }
        val title = n.extras?.getCharSequence(Notification.EXTRA_TITLE)
        val text = n.extras?.getCharSequence(Notification.EXTRA_TEXT)
        return !title.isNullOrEmpty() || !text.isNullOrEmpty()
    }

    /**
     * Looser filter for the status-bar icons: like the system status bar, it shows a glyph for any real
     * notification — including silent / low-importance ones whose channel disables badges (Google News,
     * "now playing", etc.) and custom-view/MediaStyle ones that carry a small icon but no title/text.
     * We only drop group summaries (they duplicate their children's icons). Notably it does NOT consult
     * `canShowBadge()`, which is what the strict [isBadgeWorthy] dot filter uses.
     */
    /**
     * An ongoing notification about something happening right now, as a [LiveActivity]: Android 16
     * live updates (promoted ongoing), anything with a running clock (timers, stopwatches, calls),
     * navigation, a workout, location sharing, or a task with a progress bar (downloads, uploads).
     * Media has its own widget and taskbar icon, so players are left out; so is Folark itself.
     */
    private fun liveEntry(sbn: StatusBarNotification, serial: Long): LiveActivity? {
        val n = sbn.notification ?: return null
        if (sbn.packageName == packageName) return null
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return null
        val ongoing = (n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE)) != 0
        val promoted = Build.VERSION.SDK_INT >= 36 && (n.flags and Notification.FLAG_PROMOTED_ONGOING) != 0
        if (!ongoing && !promoted) return null
        val extras = n.extras
        if (extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true) return null
        val chronometer = extras?.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) == true && n.`when` > 0L
        val max = extras?.getInt(Notification.EXTRA_PROGRESS_MAX) ?: 0
        val indeterminate = extras?.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE) == true
        val category = n.category
        val live = promoted || chronometer || max > 0 || indeterminate || category in LIVE_CATEGORIES
        if (!live) return null
        val critical = if (Build.VERSION.SDK_INT >= 36) catching { n.shortCriticalText }.getOrNull() else null
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }
            ?: appLabel(sbn.packageName)
        return LiveActivity(
            key = sbn.key,
            packageName = sbn.packageName,
            userSerial = serial,
            icon = n.smallIcon,
            color = n.color.takeIf { it != Notification.COLOR_DEFAULT },
            title = title,
            text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            critical = critical,
            chronometerBase = if (chronometer) n.`when` else 0L,
            countDown = extras?.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN) == true,
            progress = if (max > 0) ((extras?.getInt(Notification.EXTRA_PROGRESS) ?: 0).toFloat() / max).coerceIn(0f, 1f) else null,
            indeterminate = indeterminate,
            category = category,
            postTime = sbn.postTime,
            contentIntent = n.contentIntent,
            appLabel = appLabel(sbn.packageName),
            subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.takeIf { it.isNotBlank() },
            bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() },
            actions = liveActions(n),
            // Android draws its own chip for these: a promoted Live update, or an ongoing call.
            systemChip = promoted || (category == Notification.CATEGORY_CALL && ongoing),
        )
    }

    /** Up to three of [n]'s buttons that just do something (no reply boxes), for the opened-up pill. */
    private fun liveActions(n: Notification): List<LiveActivity.Action> =
        n.actions.orEmpty()
            .filter { it.actionIntent != null && it.remoteInputs.isNullOrEmpty() && !it.title.isNullOrBlank() }
            .take(3)
            .map { LiveActivity.Action(it.title.toString(), it.actionIntent) }

    /** Live updates and calls first, then navigation and clocks, then progress bars. */
    private fun livePriority(a: LiveActivity): Int = when {
        a.critical != null -> 4
        a.category == Notification.CATEGORY_CALL -> 4
        a.category == Notification.CATEGORY_NAVIGATION -> 3
        a.chronometerBase > 0L -> 2
        else -> 1
    }

    private val labels = HashMap<String, String>()

    private fun appLabel(pkg: String): String = labels.getOrPut(pkg) {
        catching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }
            .getOrDefault(pkg)
    }

    private fun isIconWorthy(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification ?: return false
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return false
        // A custom-RemoteViews / MediaStyle notification can carry a user-visible glyph with neither
        // EXTRA_TITLE nor EXTRA_TEXT — the system bar shows it, so we do too. Require a small icon.
        val title = n.extras?.getCharSequence(Notification.EXTRA_TITLE)
        val text = n.extras?.getCharSequence(Notification.EXTRA_TEXT)
        return !title.isNullOrEmpty() || !text.isNullOrEmpty() || n.smallIcon != null
    }
}
