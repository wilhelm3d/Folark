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
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.AppRepository
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.data.ScheduledPair
import org.arkikeskus.launcher.data.nextFireAfter

/**
 * Fires scheduled app-pair scenes (PairSchedules.kt): one exact alarm per scheduled pair; when it
 * goes off, the pair launches straight away if Folark is what the user is looking at, and otherwise
 * comes up as a heads-up notification ([PairLaunchActivity]) — Android does not let a background
 * app start activities, and a notification the user taps is the honest way around that.
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
                when (intent.action) {
                    ACTION_FIRE -> fire(context, intent.getStringExtra(EXTRA_PAIR_ID))
                    else -> rescheduleAll(context)
                }
            } finally {
                pending.finish()
            }
        }
    }

    /** The alarm for [pairId] went off: lay the next occurrence, then launch or notify. */
    private suspend fun fire(context: Context, pairId: String?) {
        pairId ?: return
        val pair = scheduledPair(pairId) ?: return
        rescheduleAll(context)
        if (folarkInFront(context)) {
            runCatching { appRepository.launchAppPair(pair.pair) }
        } else {
            notifyPair(context, pair)
        }
    }

    private suspend fun scheduledPair(pairId: String): ScheduledPair? =
        withContext(Dispatchers.IO) {
            val schedule = settingsRepository.pairSchedule(pairId) ?: return@withContext null
            val pair = settingsRepository.appPairs.first().firstOrNull { it.id == pairId }
                ?: return@withContext null
            ScheduledPair(pair, schedule)
        }

    /** A pair may only jump to the front while the user is actually on Folark, awake and unlocked. */
    private fun folarkInFront(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager ?: return false
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager ?: return false
        if (!power.isInteractive || keyguard.isKeyguardLocked) return false
        val top = runCatching {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)
                ?.getRunningTasks(1)?.firstOrNull()?.topActivity?.packageName
        }.getOrNull()
        return top == context.packageName
    }

    private fun notifyPair(context: Context, pair: ScheduledPair) {
        runCatching {
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

        /** (Re)lays every scheduled pair's alarm; cancels the ones no longer wanted. */
        fun rescheduleAll(context: Context) {
            val manager = context.getSystemService(AlarmManager::class.java) ?: return
            val hilt = runCatching {
                dagger.hilt.android.EntryPointAccessors.fromApplication(
                    context.applicationContext, PairSchedulerEntryPoint::class.java,
                )
            }.getOrNull() ?: return
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val wanted = withContext(Dispatchers.IO) {
                    hilt.settingsRepository().scheduledPairs.first()
                }
                val prefs = context.getSharedPreferences("pair_schedules", Context.MODE_PRIVATE)
                // Cancel alarms of schedules removed since last time (AlarmManager has no listing).
                val scheduled = prefs.getStringSet("scheduled", emptySet()).orEmpty()
                (scheduled - wanted.map { it.pair.id }.toSet()).forEach { id ->
                    manager.cancel(fireIntent(context, id))
                }
                val now = System.currentTimeMillis()
                val ids = wanted.mapNotNull { sp ->
                    val next = sp.schedule?.nextFireAfter(now) ?: return@mapNotNull null
                    val pi = fireIntent(context, sp.pair.id)
                    // Exact when Android allows it (user grant on 12+); else a ±10 min window —
                    // the pair then arrives a little late rather than not at all.
                    if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
                        manager.setWindow(AlarmManager.RTC_WAKEUP, next, 10 * 60_000L, pi)
                    } else {
                        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
                    }
                    sp.pair.id
                }
                prefs.edit().putStringSet("scheduled", ids.toSet()).apply()
            }
        }

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
