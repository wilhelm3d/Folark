package org.arkikeskus.launcher.data

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

/** [PairSchedule] and [nextFireAfter]: weekdays, "strictly after", and daylight-saving days. */
class PairSchedulesTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private lateinit var savedZone: TimeZone

    // nextFireAfter works in the phone's own time zone, as the user set the schedule.
    @Before fun zone() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(berlin))
    }

    @After fun back() = TimeZone.setDefault(savedZone)

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, berlin).toInstant().toEpochMilli()
    private fun local(ms: Long): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(berlin)

    @Test fun `every day fires later today or tomorrow`() {
        val s = PairSchedule(8, 0, emptySet())
        assertThat(local(s.nextFireAfter(at(2026, 11, 10, 7, 0)))).isEqualTo(ZonedDateTime.of(2026, 11, 10, 8, 0, 0, 0, berlin))
        assertThat(local(s.nextFireAfter(at(2026, 11, 10, 9, 0)))).isEqualTo(ZonedDateTime.of(2026, 11, 11, 8, 0, 0, 0, berlin))
    }

    @Test fun `exactly at the time it is the next occurrence, so a fire cannot arm itself again`() {
        val s = PairSchedule(8, 0, emptySet())
        val now = at(2026, 11, 10, 8, 0)
        assertThat(local(s.nextFireAfter(now)).dayOfMonth).isEqualTo(11)
    }

    @Test fun `chosen weekdays only, Monday is 1 and Sunday 7`() {
        val mondays = PairSchedule(9, 30, setOf(1))
        // Tuesday 10 November 2026 -> Monday 16 November.
        val next = local(mondays.nextFireAfter(at(2026, 11, 10, 12, 0)))
        assertThat(next.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
        assertThat(next).isEqualTo(ZonedDateTime.of(2026, 11, 16, 9, 30, 0, 0, berlin))
        val weekend = PairSchedule(10, 0, setOf(6, 7))
        assertThat(local(weekend.nextFireAfter(at(2026, 11, 10, 12, 0))).dayOfWeek).isEqualTo(DayOfWeek.SATURDAY)
        assertThat(local(weekend.nextFireAfter(at(2026, 11, 14, 11, 0))).dayOfWeek).isEqualTo(DayOfWeek.SUNDAY)
    }

    @Test fun `clocks going forward - a time that does not exist still fires that day`() {
        // 29 March 2026: 02:00 jumps to 03:00 in Berlin; 02:30 lands at 03:30, not a day later.
        val s = PairSchedule(2, 30, emptySet())
        val next = local(s.nextFireAfter(at(2026, 3, 29, 0, 0)))
        assertThat(next.toLocalDate().dayOfMonth).isEqualTo(29)
        assertThat(next.hour).isEqualTo(3)
        assertThat(next.minute).isEqualTo(30)
    }

    @Test fun `clocks going back - the next morning is still 8 o'clock local`() {
        // 25 October 2026: 03:00 goes back to 02:00 (a 25-hour day).
        val s = PairSchedule(8, 0, emptySet())
        val next = local(s.nextFireAfter(at(2026, 10, 24, 9, 0)))
        assertThat(next).isEqualTo(ZonedDateTime.of(2026, 10, 25, 8, 0, 0, 0, berlin))
        val after = local(s.nextFireAfter(at(2026, 10, 25, 9, 0)))
        assertThat(after).isEqualTo(ZonedDateTime.of(2026, 10, 26, 8, 0, 0, 0, berlin))
    }

    @Test fun `stored text round-trips and rubbish is refused`() {
        val s = PairSchedule(7, 5, setOf(5, 1, 3))
        assertThat(s.serialize()).isEqualTo("07:05|135")
        assertThat(PairSchedule.parse(s.serialize())).isEqualTo(s)
        assertThat(PairSchedule.parse("21:00|")).isEqualTo(PairSchedule(21, 0, emptySet()))
        assertThat(PairSchedule.parse("25:00|1")).isNull()
        assertThat(PairSchedule.parse("nonsense")).isNull()
        assertThat(PairSchedule.parse(null)).isNull()
    }
}
