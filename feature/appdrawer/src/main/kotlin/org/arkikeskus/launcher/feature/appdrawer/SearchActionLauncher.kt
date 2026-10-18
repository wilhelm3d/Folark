package org.arkikeskus.launcher.feature.appdrawer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.widget.Toast
import org.arkikeskus.launcher.model.SearchResult

/**
 * Opens the app for a search [SearchResult.Action], already filled in: the clock app's timer or
 * alarm, the calendar's new-event screen, maps, a new email or text, a note, the dialer. Nothing
 * is done without the user seeing it: each app still shows its own screen to confirm. Returns
 * false (after a short message) when no app on the phone can do it.
 */
internal fun startSearchAction(context: Context, action: SearchResult.Action): Boolean {
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
