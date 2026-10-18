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
}
