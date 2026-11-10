package org.arkikeskus.launcher.data.search

import org.arkikeskus.launcher.model.SearchResult
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import org.arkikeskus.launcher.model.catching

/**
 * Turns what was typed into the drawer's search into things to do: "timer 10 min", "alarm 7:30",
 * "event dentist tomorrow 14:00", "remind me to call mum friday", "navigate to Helsinki station",
 * "email anna@example.com lunch", "note buy milk", or a phone number to call or text. Each becomes
 * one [SearchResult.Action] that opens the right app already filled in (see the drawer). Pure
 * Kotlin, so it is unit-testable.
 */
object SearchActions {

    fun parse(query: String, now: ZonedDateTime = ZonedDateTime.now()): List<SearchResult.Action> {
        val q = query.trim().replace(SPACES, " ")
        if (q.isEmpty()) return emptyList()
        val lower = q.lowercase(Locale.ROOT)
        val out = ArrayList<SearchResult.Action>()
        timer(lower)?.let(out::add)
        alarm(lower)?.let(out::add)
        event(q, lower, now)?.let(out::add)
        navigation(q, lower)?.let(out::add)
        email(q, lower)?.let(out::add)
        note(q, lower)?.let(out::add)
        out += phone(q, lower)
        out += toggles(lower)
        return out
    }

    // ---------------------------------------------------------------- timer

    private val TIMER_FIRST = Regex("^(?:set (?:a )?)?timer(?: for)?(?: (.+))?$")
    private val TIMER_LAST = Regex("^(.+?) timer$")
    private val DURATION_PART = Regex("(\\d+(?:[.,]\\d+)?)\\s*(hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)?\\b")

    private fun timer(lower: String): SearchResult.Action? {
        val rest = TIMER_FIRST.find(lower)?.groupValues?.get(1)
            ?: TIMER_LAST.find(lower)?.groupValues?.get(1)?.takeIf { DURATION_PART.containsMatchIn(it) }
            ?: if (lower.startsWith("timer") || lower == "set a timer") "" else return null
        val seconds = duration(rest)
        return if (seconds == null || seconds <= 0) {
            if (rest.isNotBlank()) return null
            SearchResult.Action(SearchResult.Action.TIMER, "Timer", "Open the timer", emptyMap())
        } else {
            SearchResult.Action(
                SearchResult.Action.TIMER,
                "Timer ${durationLabel(seconds)}",
                "Start a timer",
                mapOf("seconds" to seconds.toString()),
            )
        }
    }

    /** "10", "10 min", "1h 30m", "90s", "1.5 hours" → seconds; a bare number is minutes. */
    internal fun duration(text: String): Int? {
        val t = text.trim()
        if (t.isEmpty()) return null
        var total = 0.0
        var matched = false
        for (m in DURATION_PART.findAll(t)) {
            val value = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: continue
            val unit = m.groupValues[2]
            total += when {
                unit.startsWith("h") -> value * 3600
                unit.startsWith("s") -> value
                else -> value * 60
            }
            matched = true
        }
        // Words that aren't a duration ("timer for pasta") are not a timer length.
        val leftover = DURATION_PART.replace(t, "").replace("and", "").trim()
        if (!matched || leftover.isNotEmpty()) return null
        return total.toInt().takeIf { it in 1..MAX_TIMER_SECONDS }
    }

    private fun durationLabel(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return listOfNotNull(
            if (h > 0) "$h h" else null,
            if (m > 0) "$m min" else null,
            if (s > 0) "$s s" else null,
        ).joinToString(" ")
    }

    // ---------------------------------------------------------------- alarm

    private val ALARM = Regex("^(?:set (?:an )?)?alarm(?: (?:at|for))?(?: (.+))?$")
    private val WAKE = Regex("^wake me(?: up)?(?: at)?(?: (.+))?$")

    private fun alarm(lower: String): SearchResult.Action? {
        val m = ALARM.find(lower) ?: WAKE.find(lower) ?: return null
        val rest = m.groupValues[1].trim()
        if (rest.isEmpty()) {
            return SearchResult.Action(SearchResult.Action.ALARM, "Alarm", "Open your alarms", emptyMap())
        }
        val time = clockTime(rest, requireWhole = true) ?: return null
        return SearchResult.Action(
            SearchResult.Action.ALARM,
            "Alarm %d:%02d".format(time.hour, time.minute),
            "Set an alarm",
            mapOf("hour" to time.hour.toString(), "minute" to time.minute.toString()),
        )
    }

    private val CLOCK = Regex("\\b(\\d{1,2})(?:[:.](\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)?(?=\\s|$)")

    /** "7", "7:30", "19.45", "7pm", "7:30 am" → a time of day. */
    internal fun clockTime(text: String, requireWhole: Boolean): LocalTime? {
        val m = CLOCK.find(text) ?: return null
        if (requireWhole && text.replace(m.value, "").isNotBlank()) return null
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
        val half = m.groupValues[3]
        if (half.startsWith("p") && hour in 1..11) hour += 12
        if (half.startsWith("a") && hour == 12) hour = 0
        if (hour > 23 || minute > 59) return null
        return LocalTime.of(hour, minute)
    }

    // ---------------------------------------------------------------- calendar event

    private val EVENT = Regex("^(?:(?:new|add|create) )?(event|meeting|appointment)(?: (.+))?$")
    private val REMIND = Regex("^remind me(?: to)?(?: (.+))?$")
    private val AT_TIME = Regex("(?:^|\\s)(?:at )?(\\d{1,2}(?:[:.]\\d{2})?\\s*(?:am|pm)?)(?=\\s|$)")
    /** Each weekday's pattern, built once (E10). */
    private val WEEKDAYS: List<Pair<DayOfWeek, Regex>> = DayOfWeek.entries.map { dow ->
        val full = dow.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase(Locale.ROOT)
        dow to Regex("(?:^|\\s)(?:on |next )?(?:$full|${full.take(3)})(?=\\s|$)")
    }
    private val TODAY = Regex("(?:^|\\s)(?:on )?today(?=\\s|$)")
    private val TOMORROW = Regex("(?:^|\\s)(?:on )?tomorrow(?=\\s|$)")
    private val DAY_MONTH = Regex("(?:^|\\s)(?:on )?(\\d{1,2})[./](\\d{1,2})\\.?(?=\\s|$)")

    private fun event(original: String, lower: String, now: ZonedDateTime): SearchResult.Action? {
        val eventMatch = EVENT.find(lower)
        val remindMatch = if (eventMatch == null) REMIND.find(lower) ?: return null else null
        val restGroup = if (eventMatch != null) eventMatch.groups[2] else remindMatch?.groups?.get(1)
        // "meeting with Tom" keeps its word; "event dentist" is just "Dentist".
        val noun = eventMatch?.groupValues?.get(1)?.takeIf { it != "event" }
        val start = restGroup?.range?.first ?: original.length
        var rest = original.substring(start).trim()
        var date: LocalDate? = null
        var time: LocalTime? = null

        // The day: today, tomorrow, a weekday, or day.month.
        fun take(word: Regex, day: LocalDate) {
            val hit = word.find(rest.lowercase(Locale.ROOT)) ?: return
            date = day
            rest = rest.removeRange(hit.range).trim()
        }
        take(TODAY, now.toLocalDate())
        if (date == null) take(TOMORROW, now.toLocalDate().plusDays(1))
        if (date == null) {
            for ((dow, word) in WEEKDAYS) {
                take(word, nextDay(now.toLocalDate(), dow))
                if (date != null) break
            }
        }
        if (date == null) {
            DAY_MONTH.find(rest)?.let { hit ->
                val day = hit.groupValues[1].toInt()
                val month = hit.groupValues[2].toInt()
                catching { LocalDate.of(now.year, month, day) }.getOrNull()?.let { d ->
                    date = if (d.isBefore(now.toLocalDate())) d.plusYears(1) else d
                    rest = rest.removeRange(hit.range).trim()
                }
            }
        }
        // The time: "at 14", "14:00", "2pm".
        AT_TIME.findAll(rest.lowercase(Locale.ROOT)).lastOrNull()?.let { hit ->
            val t = clockTime(hit.groupValues[1].trim(), requireWhole = true)
            // A bare number without "at" (like "event 3 apples") is only a time with a day or a colon.
            val explicit = hit.value.contains("at ") || hit.groupValues[1].contains(':') ||
                hit.groupValues[1].contains('.') || hit.groupValues[1].contains('m') || date != null
            if (t != null && explicit) {
                time = t
                rest = rest.removeRange(hit.range).trim()
            }
        }
        val title = listOfNotNull(noun, rest.trim().trim(',', '-').trim().ifEmpty { null }).joinToString(" ")
        val day = date ?: if (time != null) {
            // A time already gone today means tomorrow.
            if (time.isBefore(now.toLocalTime())) now.toLocalDate().plusDays(1) else now.toLocalDate()
        } else null
        val args = HashMap<String, String>()
        if (title.isNotEmpty()) args["title"] = title.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        val detail: String
        if (day != null) {
            if (time != null) {
                val begin = day.atTime(time).atZone(now.zone)
                args["begin"] = begin.toInstant().toEpochMilli().toString()
                detail = "${dayLabel(day, now.toLocalDate())} %d:%02d".format(time.hour, time.minute)
            } else {
                args["allDay"] = day.atStartOfDay(now.zone).toInstant().toEpochMilli().toString()
                detail = "${dayLabel(day, now.toLocalDate())}, all day"
            }
        } else {
            detail = "Pick a day and time"
        }
        val label = if (title.isEmpty()) "New event" else "New event: ${args["title"]}"
        return SearchResult.Action(SearchResult.Action.EVENT, label, detail, args)
    }

    private fun nextDay(today: LocalDate, dow: DayOfWeek): LocalDate {
        var d = today.plusDays(1)
        while (d.dayOfWeek != dow) d = d.plusDays(1)
        return d
    }

    private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> if (day.isBefore(today.plusDays(7))) {
            day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        } else {
            "${day.dayOfMonth} ${day.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
        }
    }

    // ---------------------------------------------------------------- maps

    private val NAVIGATE = Regex("^(?:navigate|directions|drive|route|take me)(?: to)? (.+)$")
    private val MAP = Regex("^(?:map|maps|where is|find on map) (.+)$")

    private fun navigation(original: String, lower: String): SearchResult.Action? {
        NAVIGATE.find(lower)?.let { m ->
            val place = original.substring(m.groups[1]?.range?.first ?: return null).trim()
            return SearchResult.Action(SearchResult.Action.NAVIGATE, "Directions to $place", "Start navigation", mapOf("place" to place))
        }
        MAP.find(lower)?.let { m ->
            val place = original.substring(m.groups[1]?.range?.first ?: return null).trim()
            return SearchResult.Action(SearchResult.Action.MAP, "Show $place on the map", "Open maps", mapOf("place" to place))
        }
        return null
    }

    // ---------------------------------------------------------------- email and notes

    private val EMAIL = Regex("^(?:e-?mail|mail|write to) (.+)$")
    private val ADDRESS = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    private fun email(original: String, lower: String): SearchResult.Action? {
        val m = EMAIL.find(lower) ?: return null
        val rest = original.substring(m.groups[1]?.range?.first ?: return null).trim()
        val first = rest.substringBefore(' ')
        return if (ADDRESS.matches(first)) {
            val subject = rest.substringAfter(' ', "").trim()
            SearchResult.Action(
                SearchResult.Action.EMAIL,
                "Email $first",
                subject.ifEmpty { "New email" },
                mapOf("to" to first, "subject" to subject),
            )
        } else {
            SearchResult.Action(SearchResult.Action.EMAIL, "New email: $rest", "Subject filled in", mapOf("subject" to rest))
        }
    }

    private val NOTE = Regex("^(?:note|notes|new note|write down|jot down|jot) (.+)$")

    private fun note(original: String, lower: String): SearchResult.Action? {
        val m = NOTE.find(lower) ?: return null
        val text = original.substring(m.groups[1]?.range?.first ?: return null).trim()
        return SearchResult.Action(SearchResult.Action.NOTE, "Note: $text", "Save as a note", mapOf("text" to text))
    }

    // ---------------------------------------------------------------- phone numbers

    private val NUMBER = Regex("^(?:(?:call|dial|text|sms|message) )?(\\+?[\\d][\\d ()\\-/]{2,})$")

    private fun phone(original: String, lower: String): List<SearchResult.Action> {
        val m = NUMBER.find(lower) ?: return emptyList()
        val raw = original.substring(m.groups[1]?.range?.first ?: return emptyList()).trim()
        val digits = raw.count { it.isDigit() }
        // Not a calculation ("12-3") and long enough to be a number someone dials.
        if (digits < 3 || CALC_LIKE.matches(raw)) return emptyList()
        val number = raw.filter { it.isDigit() || it == '+' }
        val textFirst = lower.startsWith("text") || lower.startsWith("sms") || lower.startsWith("message")
        val call = SearchResult.Action(SearchResult.Action.CALL, "Call $raw", "Open the dialer", mapOf("number" to number))
        val sms = SearchResult.Action(SearchResult.Action.MESSAGE, "Message $raw", "New text message", mapOf("number" to number))
        return if (textFirst) listOf(sms, call) else listOf(call, sms)
    }

    // ---------------------------------------------------------------- system switches

    /**
     * "turn on the flashlight", "turn the flashlight off", "flashlight off", "switch off wifi",
     * "disable bluetooth": the thing switched ([2]) and the wanted state, wherever the words
     * stand ([1] before it, [3] after it). Either may be missing (just "flashlight": toggle).
     */
    private val SWITCH = Regex("^(?:(?:turn|switch|put|set)\\s+)?(?:(on|off)\\s+)?(?:the\\s+)?(.+?)(?:\\s+(on|off))?$")
    private val ENABLE = Regex("^(enable|disable)\\s+(?:the\\s+)?(.+)$")
    private val FLASHLIGHT = Regex("^(?:flashlight|flash light|torch)$")
    private val ROTATION = Regex("^(?:auto[- ]?rotate|auto[- ]?rotation|rotation)$")
    private val ROTATION_LOCK = Regex("^(?:rotation lock|screen lock rotation|lock rotation)$")
    private val DND = Regex("^(?:do not disturb|dnd|silence|silent mode)$")
    private val WIFI = Regex("^(?:wi-?fi|wlan)$")
    private val BLUETOOTH = Regex("^bluetooth$")
    private val AIRPLANE = Regex("^(?:airplane|aeroplane|flight|plane) mode$")
    private val BRIGHTNESS = Regex("^(?:set )?(?:the )?brightness(?: to)?(?: (\\d{1,3}))?(?: ?%)?$")

    /** What a switch query asks for: the thing (lower case) and "on", "off" or "" (not said). */
    internal fun switchOf(lower: String): Pair<String, String>? {
        ENABLE.find(lower)?.let { m ->
            return m.groupValues[2].trim() to (if (m.groupValues[1] == "enable") "on" else "off")
        }
        val m = SWITCH.find(lower) ?: return null
        val before = m.groupValues[1]
        val after = m.groupValues[3]
        // "turn on X off" says two things: no switch at all.
        if (before.isNotEmpty() && after.isNotEmpty() && before != after) return null
        return m.groupValues[2].trim() to before.ifEmpty { after }
    }

    /**
     * System switches typed into search: "flashlight", "turn off the flashlight", "wifi off",
     * "do not disturb on", "disable bluetooth", "auto-rotate off", "brightness 40", "airplane mode".
     * Each becomes a TOGGLE action with "on", "off" or "toggle"; the drawer decides what the phone
     * allows (a panel, a settings page, or the switch itself).
     */
    private fun toggles(lower: String): List<SearchResult.Action> {
        val out = ArrayList<SearchResult.Action>()
        fun add(label: String, setting: String, state: String) {
            out += SearchResult.Action(
                SearchResult.Action.TOGGLE, label, "Quick action",
                mapOf("setting" to setting, "state" to state),
            )
        }
        BRIGHTNESS.find(lower)?.let { m ->
            val percent = m.groupValues[1]
            add(if (percent.isEmpty()) "Brightness" else "Brightness $percent%", "brightness", percent)
            return out
        }
        val (thing, said) = switchOf(lower) ?: return out
        val state = said.ifEmpty { "toggle" }
        fun label(name: String) = when (said) {
            "on" -> "Turn $name on"
            "off" -> "Turn $name off"
            else -> name
        }
        when {
            FLASHLIGHT.matches(thing) -> add(label("the flashlight").replaceFirstChar { it.uppercase() }, "flashlight", state)
            ROTATION_LOCK.matches(thing) ->
                // Rotation lock on = auto-rotate off, and the other way round.
                add(label("rotation lock").replaceFirstChar { it.uppercase() }, "rotation", when (said) {
                    "on" -> "off"
                    "off" -> "on"
                    else -> "toggle"
                })
            ROTATION.matches(thing) -> add(label("auto-rotate").replaceFirstChar { it.uppercase() }, "rotation", state)
            DND.matches(thing) -> add(label("Do Not Disturb"), "dnd", state)
            WIFI.matches(thing) -> add(label("Wi-Fi"), "wifi", state)
            BLUETOOTH.matches(thing) -> add(label("Bluetooth"), "bluetooth", state)
            AIRPLANE.matches(thing) -> add(label("airplane mode").replaceFirstChar { it.uppercase() }, "airplane", state)
        }
        return out
    }

    private val CALC_LIKE = Regex("^\\d+\\s*[-/]\\s*\\d+$")
    private val SPACES = Regex("\\s+")
    private const val MAX_TIMER_SECONDS = 24 * 3600 - 1
}
