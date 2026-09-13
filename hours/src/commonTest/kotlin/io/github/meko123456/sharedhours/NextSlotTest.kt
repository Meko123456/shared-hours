package io.github.meko123456.sharedhours

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Searching forward, which is what anyone arranging a meeting is actually doing. */
class NextSlotTest {

    private val tbilisi = TimeZone.of("Asia/Tbilisi")
    private val london = TimeZone.of("Europe/London")
    private val dubai = TimeZone.of("Asia/Dubai")

    private val thursday = LocalDate(2026, 8, 27)
    private val friday = LocalDate(2026, 8, 28)
    private val saturday = LocalDate(2026, 8, 29)
    private val monday = LocalDate(2026, 8, 31)

    private fun minute(hhmm: String) = LocalTime.parse(hhmm).toSecondOfDay() / 60
    private fun seg(from: String, to: String) = Segment(minute(from), minute(to))

    private val pair = listOf(ZoneSchedule(tbilisi), ZoneSchedule(london))

    private val gulf = listOf(
        ZoneSchedule(tbilisi),
        ZoneSchedule(dubai, WorkingHours(LocalTime(9, 0), LocalTime(18, 0), WorkingHours.SUNDAY_TO_THURSDAY)),
    )

    @Test
    fun `today counts when today works`() {
        assertEquals(
            DatedSegment(thursday, seg("12:00", "13:00")),
            OverlapFinder.nextSlot(thursday, tbilisi, pair, lengthMinutes = 60),
        )
    }

    @Test
    fun `a weekend is skipped to the Monday`() {
        assertEquals(
            DatedSegment(monday, seg("12:00", "13:00")),
            OverlapFinder.nextSlot(saturday, tbilisi, pair, lengthMinutes = 60),
        )
    }

    @Test
    fun `two different weekends are both skipped`() {
        // Tbilisi is off Saturday and Sunday, Dubai is off Friday and Saturday. Between them the
        // Friday, the weekend and the Sunday are all out, so the next slot is the Monday.
        assertEquals(monday, OverlapFinder.nextSlot(friday, tbilisi, gulf, lengthMinutes = 60)?.date)
    }

    @Test
    fun `the horizon is respected`() {
        // Starting on the Saturday, the Monday is two days away.
        assertNull(OverlapFinder.nextSlot(saturday, tbilisi, pair, lengthMinutes = 60, withinDays = 2))
        assertEquals(monday, OverlapFinder.nextSlot(saturday, tbilisi, pair, lengthMinutes = 60, withinDays = 3)?.date)
    }

    @Test
    fun `a horizon of one day searches only that day`() {
        assertNull(OverlapFinder.nextSlot(saturday, tbilisi, pair, lengthMinutes = 60, withinDays = 1))
        assertEquals(thursday, OverlapFinder.nextSlot(thursday, tbilisi, pair, lengthMinutes = 60, withinDays = 1)?.date)
    }

    @Test
    fun `zones that never overlap give nothing however long the search`() {
        val impossible = listOf(ZoneSchedule(tbilisi), ZoneSchedule(TimeZone.of("Pacific/Honolulu")))
        assertNull(OverlapFinder.nextSlot(thursday, tbilisi, impossible, lengthMinutes = 15, withinDays = 60))
    }

    @Test
    fun `a longer meeting can push the answer to a later day`() {
        // Tbilisi and Kolkata share 09:00–16:30, so seven hours fits; with Kolkata and London both
        // in, the shared stretch is too short for it on any day.
        val three = listOf(ZoneSchedule(tbilisi), ZoneSchedule(TimeZone.of("Asia/Kolkata")), ZoneSchedule(london))
        assertEquals(thursday, OverlapFinder.nextSlot(thursday, tbilisi, three, lengthMinutes = 120)?.date)
        assertNull(OverlapFinder.nextSlot(thursday, tbilisi, three, lengthMinutes = 6 * 60, withinDays = 30))
    }

    @Test
    fun `a horizon below one day is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            OverlapFinder.nextSlot(thursday, tbilisi, pair, lengthMinutes = 60, withinDays = 0)
        }
    }
}
