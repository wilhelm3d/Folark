package org.arkikeskus.launcher.feature.appdrawer

import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.Toast
import org.arkikeskus.launcher.model.SearchResult
import org.arkikeskus.launcher.model.catching

/**
 * Opens the app for a search [SearchResult.Action], already filled in: the clock app's timer or
 * alarm, the calendar's new-event screen, maps, a new email or text, a note, the dialer. Nothing
 * is done without the user seeing it: each app still shows its own screen to confirm. Returns
 * false (after a short message) when no app on the phone can do it.
 */
internal fun startSearchAction(context: Context, action: SearchResult.Action): Boolean {
    // A system switch: done here, without opening an app, where Android allows it.
    if (action.kind == SearchResult.Action.TOGGLE) {
        val done = runToggle(context, action.args["setting"].orEmpty(), action.args["state"].orEmpty())
        if (!done) Toast.makeText(context, R.string.search_action_unavailable, Toast.LENGTH_SHORT).show()
        return done
    }
    // A clipboard-history hit: copied again, no app opened.
    if (action.kind == SearchResult.Action.CLIPBOARD) {
        val clip = context.getSystemService(android.content.ClipboardManager::class.java) ?: return false
        catching { clip.setPrimaryClip(android.content.ClipData.newPlainText("clip", action.args["text"].orEmpty())) }
        Toast.makeText(context, R.string.search_clip_copied, Toast.LENGTH_SHORT).show()
        return true
    }
    val a = action.args
    val intents: List<Intent> = when (action.kind) {
        SearchResult.Action.TIMER -> listOf(
            a["seconds"]?.toIntOrNull()?.let { seconds ->
                Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            } ?: Intent(AlarmClock.ACTION_SHOW_TIMERS),
        )
        SearchResult.Action.ALARM -> {
            val hour = a["hour"]?.toIntOrNull()
            val minute = a["minute"]?.toIntOrNull()
            listOf(
                if (hour != null && minute != null) {
                    Intent(AlarmClock.ACTION_SET_ALARM)
                        .putExtra(AlarmClock.EXTRA_HOUR, hour)
                        .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                } else {
                    Intent(AlarmClock.ACTION_SHOW_ALARMS)
                },
            )
        }
        SearchResult.Action.EVENT -> listOf(
            Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
                a["title"]?.let { putExtra(CalendarContract.Events.TITLE, it) }
                a["begin"]?.toLongOrNull()?.let { begin ->
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + HOUR_MS)
                }
                a["allDay"]?.toLongOrNull()?.let { day ->
                    putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, day)
                    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, day + DAY_MS)
                }
            },
        )
        SearchResult.Action.NAVIGATE -> {
            val place = Uri.encode(a["place"].orEmpty())
            // Turn-by-turn where a navigation app takes it, else any maps app.
            listOf(
                Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$place")),
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$place")),
            )
        }
        SearchResult.Action.MAP -> listOf(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(a["place"].orEmpty())}")))
        SearchResult.Action.EMAIL -> listOf(
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(a["to"].orEmpty(), "@.+-_"))).apply {
                a["subject"]?.takeIf { it.isNotBlank() }?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            },
        )
        SearchResult.Action.NOTE -> {
            val text = a["text"].orEmpty()
            listOf(
                // Notes apps that take a new note directly (Google Keep and others).
                Intent(CREATE_NOTE).putExtra(Intent.EXTRA_TEXT, text).setType("text/plain"),
                Intent.createChooser(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, text).setType("text/plain"), action.label),
            )
        }
        SearchResult.Action.CALL -> listOf(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", a["number"].orEmpty(), null)))
        SearchResult.Action.MESSAGE -> listOf(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", a["number"].orEmpty(), null)))
        SearchResult.Action.FILE -> listOf(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse(a["uri"].orEmpty()), a["mime"].orEmpty())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
        else -> emptyList()
    }
    for (intent in intents) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val chooser = intent.action == Intent.ACTION_CHOOSER
        if (!chooser && intent.resolveActivity(context.packageManager) == null) continue
        try {
            context.startActivity(intent)
            return true
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }
    Toast.makeText(context, R.string.search_action_no_app, Toast.LENGTH_SHORT).show()
    return false
}

private const val CREATE_NOTE = "com.google.android.gms.actions.CREATE_NOTE"
private const val HOUR_MS = 3_600_000L
private const val DAY_MS = 86_400_000L

/**
 * The torch's real state, followed through Android's torch callback (registered on the first
 * use), so "flashlight" toggles correctly even when it was switched on from Quick Settings.
 */
private object Torch {
    @Volatile var on = false
        private set
    private var watching = false

    fun watch(manager: CameraManager, cameraId: String) {
        if (watching) return
        watching = true
        catching {
            manager.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(id: String, enabled: Boolean) {
                    if (id == cameraId) on = enabled
                }

                override fun onTorchModeUnavailable(id: String) {
                    if (id == cameraId) on = false
                }
            }, android.os.Handler(android.os.Looper.getMainLooper()))
        }
    }

    fun set(value: Boolean) {
        on = value
    }
}

/** Runs one system switch. False when nothing could be done (no torch, no camera, no permission route). */
private fun runToggle(context: Context, setting: String, state: String): Boolean = when (setting) {
    "flashlight" -> toggleTorch(context, state)
    "rotation" -> setRotation(context, state)
    "dnd" -> setDnd(context, state)
    "brightness" -> setBrightness(context, state)
    "wifi" -> openPanel(context, Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.ACTION_WIFI_SETTINGS)
    "bluetooth" -> openSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS)
    "airplane" -> openSettings(context, Settings.ACTION_AIRPLANE_MODE_SETTINGS)
    else -> false
}

/** The first camera with a flash gets its torch switched. */
private fun toggleTorch(context: Context, state: String): Boolean {
    val manager = context.getSystemService(CameraManager::class.java) ?: return false
    val cameraId = catching {
        manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull() ?: return false
    // The callback reports the current state at once when registered (here, the first time).
    Torch.watch(manager, cameraId)
    val on = when (state) {
        "on" -> true
        "off" -> false
        else -> !Torch.on
    }
    return catching {
        manager.setTorchMode(cameraId, on)
        Torch.set(on)
    }.isSuccess
}

/** Auto-rotate on/off. Android only lets a written setting through with "Modify system settings". */
private fun setRotation(context: Context, state: String): Boolean {
    if (!Settings.System.canWrite(context)) {
        openWriteSettings(context)
        return true
    }
    val resolver = context.contentResolver
    val on = when (state) {
        "on" -> true
        "off" -> false
        else -> catching { Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION) }.getOrDefault(1) == 0
    }
    return catching {
        Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0)
    }.isSuccess
}

/** Do Not Disturb, through the notification policy the user granted Folark. */
private fun setDnd(context: Context, state: String): Boolean {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return false
    if (!manager.isNotificationPolicyAccessGranted) {
        openSettings(context, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        return true
    }
    val on = when (state) {
        "on" -> true
        "off" -> false
        // Toggle: on unless it is already on (anything but "all notifications" counts as on).
        else -> manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
    }
    return catching {
        manager.setInterruptionFilter(
            if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL,
        )
    }.isSuccess
}

/** Screen brightness as a percentage, in manual mode. */
private fun setBrightness(context: Context, state: String): Boolean {
    if (!Settings.System.canWrite(context)) {
        openWriteSettings(context)
        return true
    }
    val percent = state.toIntOrNull()?.coerceIn(1, 100) ?: 50
    return catching {
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            (percent * 255 / 100).coerceIn(1, 255),
        )
    }.isSuccess
}

/** Android's own quick-settings panel for a radio, falling back to its settings page. */
private fun openPanel(context: Context, panel: String, fallback: String): Boolean {
    val shown = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        catching { context.startActivity(Intent(panel).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    } else {
        false
    }
    return shown || openSettings(context, fallback)
}

private fun openSettings(context: Context, action: String): Boolean =
    catching {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

/** Android's page for granting "Modify system settings", then the user repeats the action. */
private fun openWriteSettings(context: Context) {
    catching {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
