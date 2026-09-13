package io.github.meko123456.sharedhours

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Who is holding the window in — the half of the answer a team can act on. */
class ConstraintsTest {

    private val tbilisi = TimeZone.of("Asia/Tbilisi")
    private val london = TimeZone.of("Europe/London")
    private val thursday = LocalDate(2026, 8, 27)

    private fun minute(hhmm: String) = LocalTime.parse(hhmm).toSecondOfDay() / 60
    private fun seg(from: String, to: String) = Segment(minute(from), minute(to))

    private val home = ZoneSchedule(tbilisi)
    private val uk = ZoneSchedule(london)

    @Test
    fun `each edge is attributed to whoever caused it`() {
        val constraint = OverlapFinder.constraints(thursday, tbilisi, listOf(home, uk)).single()
        assertEquals(seg("12:00", "18:00"), constraint.window)
        // London's 09:00 is 12:00 here, so London opens the window.
        assertEquals(listOf(uk), constraint.opensWith)
        // Tbilisi finishes at 18:00 while London runs to 21:00, so home closes it.
        assertEquals(listOf(home), constraint.closesWith)
        assertFalse(constraint.boundedByTheDay)
    }

    @Test
    fun `two people finishing together are both named`() {
        val dubai = ZoneSchedule(TimeZone.of("Asia/Dubai"))
        val constraint = OverlapFinder.constraints(thursday, tbilisi, listOf(home, dubai)).single()
        // Same offset and same hours, so both bound both edges.
        assertEquals(listOf(home, dubai), constraint.opensWith)
        assertEquals(listOf(home, dubai), constraint.closesWith)
    }

    @Test
    fun `an edge that belongs to the day itself belongs to nobody`() {
        val night = ZoneSchedule(tbilisi, WorkingHours(LocalTime(22, 0), LocalTime(6, 0), WorkingHours.EVERY_DAY))
        val constraints = OverlapFinder.constraints(thursday, tbilisi, listOf(night))
        assertEquals(2, constraints.size)

        // 00:00–06:00 opens because the day does, not because anybody arrives.
        val morning = constraints.first()
        assertEquals(Segment(0, 360), morning.window)
        assertTrue(morning.opensWith.isEmpty())
        assertEquals(listOf(night), morning.closesWith)
        assertTrue(morning.boundedByTheDay)

        // 22:00–24:00 closes because the day does.
        val evening = constraints.last()
        assertEquals(listOf(night), evening.opensWith)
        assertTrue(evening.closesWith.isEmpty())
        assertTrue(evening.boundedByTheDay)
    }

    @Test
    fun `nothing shared means nothing to explain`() {
        val honolulu = ZoneSchedule(TimeZone.of("Pacific/Honolulu"))
        assertTrue(OverlapFinder.constraints(thursday, tbilisi, listOf(home, honolulu)).isEmpty())
    }

    @Test
    fun `a person well inside the window constrains neither edge`() {
        // Kolkata runs 07:30–16:30 here, so it closes the window; London opens it at 12:00 and
        // Tbilisi's own 09:00–18:00 touches neither edge.
        val kolkata = ZoneSchedule(TimeZone.of("Asia/Kolkata"))
        val constraint = OverlapFinder.constraints(thursday, tbilisi, listOf(home, uk, kolkata)).single()
        assertEquals(seg("12:00", "16:30"), constraint.window)
        assertEquals(listOf(uk), constraint.opensWith)
        assertEquals(listOf(kolkata), constraint.closesWith)
    }
}
