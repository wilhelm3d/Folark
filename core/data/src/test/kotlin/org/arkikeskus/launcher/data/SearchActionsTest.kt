package org.arkikeskus.launcher.data

import com.google.common.truth.Truth.assertThat
import org.arkikeskus.launcher.data.search.SearchActions
import org.arkikeskus.launcher.model.SearchResult
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class SearchActionsTest {
    private val now = ZonedDateTime.of(2026, 10, 18, 10, 0, 0, 0, ZoneId.of("Europe/Helsinki"))
    private fun one(q: String) = SearchActions.parse(q, now).single()

    @Test fun `timer lengths`() {
        assertThat(one("timer 10").args["seconds"]).isEqualTo("600")
        assertThat(one("1h 30m timer").args["seconds"]).isEqualTo("5400")
        assertThat(one("set a timer for 90s").args["seconds"]).isEqualTo("90")
        assertThat(one("timer").args).isEmpty()
        assertThat(SearchActions.parse("timer for pasta", now)).isEmpty()
    }

    @Test fun `alarm times`() {
        assertThat(one("alarm 7:30").args).containsExactly("hour", "7", "minute", "30")
        assertThat(one("alarm 7pm").args).containsExactly("hour", "19", "minute", "0")
        assertThat(one("wake me at 6").args["hour"]).isEqualTo("6")
        assertThat(SearchActions.parse("alarm clock app", now)).isEmpty()
    }

    @Test fun `events with days and times`() {
        val dentist = one("event dentist tomorrow 14:00")
        assertThat(dentist.kind).isEqualTo(SearchResult.Action.EVENT)
        assertThat(dentist.args["title"]).isEqualTo("Dentist")
        assertThat(dentist.args["begin"]).isEqualTo(now.plusDays(1).withHour(14).toInstant().toEpochMilli().toString())
        assertThat(one("meeting with Tom at 3pm").args["title"]).isEqualTo("Meeting with Tom")
        assertThat(one("remind me to call mum friday").args).containsKey("allDay")
    }

    @Test fun `maps mail notes and numbers`() {
        assertThat(one("navigate to Helsinki station").args["place"]).isEqualTo("Helsinki station")
        assertThat(one("email anna@example.com lunch").args).containsExactly("to", "anna@example.com", "subject", "lunch")
        assertThat(one("note buy milk").args["text"]).isEqualTo("buy milk")
        assertThat(SearchActions.parse("0401234567", now).map { it.kind })
            .containsExactly(SearchResult.Action.CALL, SearchResult.Action.MESSAGE).inOrder()
        assertThat(SearchActions.parse("12-3", now)).isEmpty()
        assertThat(SearchActions.parse("spotify", now)).isEmpty()
    }

    private fun switch(q: String): Pair<String, String>? =
        SearchActions.parse(q, now).singleOrNull { it.kind == SearchResult.Action.TOGGLE }
            ?.let { it.args["setting"].orEmpty() to it.args["state"].orEmpty() }

    @Test fun `switches read on and off wherever they stand`() {
        // The words before the thing count as much as the words after it.
        assertThat(switch("turn off the flashlight")).isEqualTo("flashlight" to "off")
        assertThat(switch("turn on the flashlight")).isEqualTo("flashlight" to "on")
        assertThat(switch("turn the flashlight off")).isEqualTo("flashlight" to "off")
        assertThat(switch("torch on")).isEqualTo("flashlight" to "on")
        assertThat(switch("flashlight")).isEqualTo("flashlight" to "toggle")
        assertThat(switch("turn off do not disturb")).isEqualTo("dnd" to "off")
        assertThat(switch("turn on dnd")).isEqualTo("dnd" to "on")
        assertThat(switch("do not disturb")).isEqualTo("dnd" to "toggle")
        assertThat(switch("switch off wifi")).isEqualTo("wifi" to "off")
        assertThat(switch("disable bluetooth")).isEqualTo("bluetooth" to "off")
        assertThat(switch("enable wi-fi")).isEqualTo("wifi" to "on")
        assertThat(switch("auto-rotate off")).isEqualTo("rotation" to "off")
        assertThat(switch("airplane mode")).isEqualTo("airplane" to "toggle")
    }

    @Test fun `rotation lock is auto-rotate the other way round`() {
        assertThat(switch("rotation lock on")).isEqualTo("rotation" to "off")
        assertThat(switch("turn off rotation lock")).isEqualTo("rotation" to "on")
    }

    @Test fun `brightness takes a number`() {
        assertThat(switch("brightness 40")).isEqualTo("brightness" to "40")
        assertThat(switch("set brightness to 70%")).isEqualTo("brightness" to "70")
        assertThat(switch("brightness")).isEqualTo("brightness" to "")
    }

    @Test fun `ordinary searches are no switches`() {
        assertThat(switch("spotify")).isNull()
        assertThat(switch("the office")).isNull()
        assertThat(switch("turn on x off")).isNull()
        assertThat(switch("flashlight app")).isNull()
    }
}
