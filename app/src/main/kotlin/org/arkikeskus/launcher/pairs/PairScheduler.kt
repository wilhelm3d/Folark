package org.arkikeskus.launcher.pairs

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.AppRepository
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.data.ScheduledPair
import org.arkikeskus.launcher.data.nextFireAfter
import org.arkikeskus.launcher.model.catching

/**
 * Fires scheduled app-pair scenes (PairSchedules.kt): one exact alarm, armed for the soonest
 * scheduled pair; when it goes off, that pair launches straight away if Folark is what the user is
 * looking at, and otherwise comes up as a heads-up notification ([PairLaunchActivity]) — Android
 * does not let a background app start activities, and a notification the user taps is the honest
 * way around that.
 *
 * Alarms are re-laid after boot, after any schedule/pair change (the application watches the
 * schedules flow), and after time or timezone changes; each fire re-lays the next occurrence.
 */
@AndroidEntryPoint
class PairScheduleReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var appRepository: AppRepository

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                // Done inside goAsync(): the next alarm is laid before the receiver reports back, so
                // Android cannot stop the process in between and leave no alarm at all.
                when (intent.action) {
                    ACTION_FIRE -> fire(context, intent.getStringExtra(EXTRA_PAIR_ID))
                    else -> relay(context, System.currentTimeMillis())
                }
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * The alarm went off: lay the next occurrence, then launch or notify every pair due at this
     * moment — the one the alarm named, and any other pair scheduled for the same minute (one alarm
     * covers them all, so pairs sharing a minute arrive together).
     */
    private suspend fun fire(context: Context, pairId: String?) {
        val firedAt = System.currentTimeMillis()
        val due = dueNow(pairId, firedAt)
        // Strictly in the future, so this fire cannot arm itself again.
        relay(context, after = firedAt)
        if (due.isEmpty()) return
        if (folarkInFront(context)) {
            due.forEach { catching { appRepository.launchAppPair(it.pair) } }
        } else {
            due.forEach { notifyPair(context, it) }
        }
    }

    /**
     * The pair [pairId] (which this alarm was laid for, so a delivery delayed by Doze still comes
     * up), plus every pair whose schedule falls within a second of [firedAt].
     */
    private suspend fun dueNow(pairId: String?, firedAt: Long): List<ScheduledPair> =
        withContext(Dispatchers.IO) {
            val schedules = settingsRepository.scheduledPairs.first()
            val pairs = settingsRepository.appPairs.first()
            schedules.mapNotNull { scheduled ->
                val schedule = scheduled.schedule ?: return@mapNotNull null
                val due = scheduled.pair.id == pairId ||
                    schedule.nextFireAfter(firedAt - DUE_SLACK_MS) <= firedAt + 1_000L
                if (!due) return@mapNotNull null
                pairs.firstOrNull { it.id == scheduled.pair.id }?.let { ScheduledPair(it, schedule) }
            }
        }

    /**
     * A pair may only jump to the front while the user is actually on Folark, awake and unlocked.
     * "On Folark" is one of Folark's own screens being resumed right now ([FolarkOnScreen]): asking
     * Android for the top task does not work for this (an app only ever sees its own tasks, and
     * the home task is always Folark's, so it said "in front" while any other app was open).
     */
    private fun folarkInFront(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager ?: return false
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager ?: return false
        if (!power.isInteractive || keyguard.isKeyguardLocked) return false
        return FolarkOnScreen.resumed
    }

    private fun notifyPair(context: Context, pair: ScheduledPair) {
        catching {
            val manager = context.getSystemService(android.app.NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                android.app.NotificationChannel(
                    CHANNEL_ID, context.getString(org.arkikeskus.launcher.feature.settings.R.string.pair_schedule_channel),
                    android.app.NotificationManager.IMPORTANCE_HIGH,
                ),
            )
            val pending = PendingIntent.getActivity(
                context, pair.pair.id.hashCode(),
                Intent(context, PairLaunchActivity::class.java).putExtra(EXTRA_PAIR_ID, pair.pair.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            manager.notify(
                pair.pair.id.hashCode(),
                android.app.Notification.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_menu_myplaces)
                    .setContentTitle(pair.pair.name)
                    .setContentText(context.getString(org.arkikeskus.launcher.feature.settings.R.string.pair_schedule_due))
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }

    companion object {
        const val ACTION_FIRE = "org.arkikeskus.launcher.pairs.FIRE"
        const val ACTION_RESCHEDULE = "org.arkikeskus.launcher.pairs.RESCHEDULE"
        const val EXTRA_PAIR_ID = "pair_id"
        const val CHANNEL_ID = "pair_schedules"

        /** Single persistent scope shared across all rescheduleAll calls (lives for the app lifetime). */
        private val rescheduleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val relayLock = Mutex()

        /** The one alarm's request code: it is re-armed for whichever pair is next. */
        private const val SINGLE_ALARM_REQUEST = 0x50414952

        /** How far before a fire a pair still counts as due with it (two schedules, one minute). */
        private const val DUE_SLACK_MS = 2_000L

        /**
         * (Re)lays the single alarm, armed for the soonest pair scheduled strictly after [after];
         * cancels it when nothing is scheduled.
         *
         * One alarm, not one per pair: every `RTC_WAKEUP` alarm is a separate exit from Doze, and
         * the receiver re-lays the next occurrence on each fire anyway. Earlier versions armed one
         * per pair; their pending intents are cancelled here so an upgrade cannot leave them behind.
         */
        fun rescheduleAll(context: Context, after: Long = System.currentTimeMillis()) {
            // One persistent scope (not a new one per call), for callers outside a receiver.
            rescheduleScope.launch { relay(context, after) }
        }

        /**
         * [rescheduleAll]'s work, awaited: the receiver calls this directly so the alarm is laid
         * before it finishes. One at a time, so two quick changes cannot interleave their writes.
         */
        suspend fun relay(context: Context, after: Long) = relayLock.withLock {
            val manager = context.getSystemService(AlarmManager::class.java) ?: return@withLock
            val hilt = catching {
                dagger.hilt.android.EntryPointAccessors.fromApplication(
                    context.applicationContext, PairSchedulerEntryPoint::class.java,
                )
            }.getOrNull() ?: return@withLock
            run {
                val wanted = withContext(Dispatchers.IO) {
                    hilt.settingsRepository().scheduledPairs.first()
                }
                val prefs = context.getSharedPreferences("pair_schedules", Context.MODE_PRIVATE)
                // Per-pair alarms from earlier versions (AlarmManager has no listing of its own).
                prefs.getStringSet("scheduled", emptySet()).orEmpty().forEach { id ->
                    manager.cancel(fireIntent(context, id))
                }
                val next = wanted
                    .mapNotNull { sp -> sp.schedule?.nextFireAfter(after)?.let { at -> sp.pair.id to at } }
                    .minByOrNull { it.second }
                if (next == null) {
                    manager.cancel(singleFireIntent(context, ""))
                    prefs.edit().putStringSet("scheduled", emptySet()).apply()
                    return@withLock
                }
                val pi = singleFireIntent(context, next.first)
                // Exact when Android allows it (user grant on 12+); else a ±10 min window —
                // the pair then arrives a little late rather than not at all.
                if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
                    manager.setWindow(AlarmManager.RTC_WAKEUP, next.second, 10 * 60_000L, pi)
                } else {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.second, pi)
                }
                prefs.edit().putStringSet("scheduled", setOf(next.first)).apply()
            }
        }

        /** The single alarm, naming the pair it was armed for (extras are ignored when cancelling). */
        private fun singleFireIntent(context: Context, pairId: String): PendingIntent {
            val intent = Intent(context, PairScheduleReceiver::class.java)
                .setAction(ACTION_FIRE)
                .putExtra(EXTRA_PAIR_ID, pairId)
            return PendingIntent.getBroadcast(
                context, SINGLE_ALARM_REQUEST, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /** A per-pair alarm from an earlier version, cancelled so an upgrade leaves none behind. */
        private fun fireIntent(context: Context, pairId: String): PendingIntent {
            val intent = Intent(context, PairScheduleReceiver::class.java)
                .setAction(ACTION_FIRE)
                .putExtra(EXTRA_PAIR_ID, pairId)
            return PendingIntent.getBroadcast(
                context, pairId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface PairSchedulerEntryPoint {
        fun settingsRepository(): SettingsRepository
    }
}
