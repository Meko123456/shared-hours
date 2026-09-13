package io.github.meko123456.sharedhours

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Where a meeting of a given length actually fits. */
class SlotsTest {

    private val tbilisi = TimeZone.of("Asia/Tbilisi")
    private val london = TimeZone.of("Europe/London")
    private val losAngeles = TimeZone.of("America/Los_Angeles")
    private val thursday = LocalDate(2026, 8, 27)

    private fun minute(hhmm: String) = LocalTime.parse(hhmm).toSecondOfDay() / 60
    private fun seg(from: String, to: String) = Segment(minute(from), minute(to))

    /** Home and London share 12:00–18:00 on the Thursday. */
    private val pair = listOf(ZoneSchedule(tbilisi), ZoneSchedule(london))

    @Test
    fun `an hour long meeting fits six times in a six hour window`() {
        val slots = OverlapFinder.slots(thursday, tbilisi, pair, lengthMinutes = 60)
        assertEquals(6, slots.size)
        assertEquals(seg("12:00", "13:00"), slots.first())
        assertEquals(seg("17:00", "18:00"), slots.last())
    }

    @Test
    fun `the step defaults to the length so slots do not overlap`() {
        val slots = OverlapFinder.slots(thursday, tbilisi, pair, lengthMinutes = 90)
        assertEquals(listOf(seg("12:00", "13:30"), seg("13:30", "15:00"), seg("15:00", "16:30"), seg("16:30", "18:00")), slots)
    }

    @Test
    fun `a smaller step gives overlapping slots`() {
        val slots = OverlapFinder.slots(thursday, tbilisi, pair, lengthMinutes = 60, stepMinutes = 30)
        assertEquals(11, slots.size)
        assertEquals(seg("12:00", "13:00"), slots[0])
        assertEquals(seg("12:30", "13:30"), slots[1])
        assertEquals(seg("17:00", "18:00"), slots.last())
    }

    @Test
    fun `starts are aligned to the step rather than to the window`() {
        // Kolkata pulls the shared window's end to 16:30; the start stays on the hour.
        val kolkata = listOf(ZoneSchedule(tbilisi), ZoneSchedule(TimeZone.of("Asia/Kolkata")))
        val slots = OverlapFinder.slots(thursday, tbilisi, kolkata, lengthMinutes = 60)
        assertEquals(seg("09:00", "10:00"), slots.first())
        // 16:00–17:00 would run past the window, so the last full hour starts at 15:00.
        assertEquals(seg("15:00", "16:00"), slots.last())
    }

    @Test
    fun `a meeting longer than the window does not fit at all`() {
        assertTrue(OverlapFinder.slots(thursday, tbilisi, pair, lengthMinutes = 7 * 60).isEmpty())
    }

    @Test
    fun `a slot never straddles the gap between two windows`() {
        // Tokyo and Los Angeles share only 04:00–05:00 seen from Tbilisi.
        val far = listOf(ZoneSchedule(TimeZone.of("Asia/Tokyo")), ZoneSchedule(losAngeles))
        assertEquals(listOf(seg("04:00", "05:00")), OverlapFinder.slots(thursday, tbilisi, far, lengthMinutes = 60))
        assertTrue(OverlapFinder.slots(thursday, tbilisi, far, lengthMinutes = 120).isEmpty())
    }

    @Test
    fun `slots come from every window when there is more than one`() {
        // A night shift in the home zone gives two windows at either end of the day.
        val night = ZoneSchedule(tbilisi, WorkingHours(LocalTime(22, 0), LocalTime(6, 0), WorkingHours.EVERY_DAY))
        val slots = OverlapFinder.slots(thursday, tbilisi, listOf(night), lengthMinutes = 120)
        // Three two-hour slots in the 00:00–06:00 window, one in the 22:00–24:00 one.
        assertEquals(
            listOf(Segment(0, 120), Segment(120, 240), Segment(240, 360), Segment(1320, 1440)),
            slots,
        )
    }

    @Test
    fun `nothing shared means no slots`() {
        val honolulu = listOf(ZoneSchedule(tbilisi), ZoneSchedule(TimeZone.of("Pacific/Honolulu")))
        assertTrue(OverlapFinder.slots(thursday, tbilisi, honolulu, lengthMinutes = 15).isEmpty())
    }

    @Test
    fun `the longest window is the best that can be done`() {
        assertEquals(seg("12:00", "18:00"), OverlapFinder.longestWindow(thursday, tbilisi, pair))
        val night = ZoneSchedule(tbilisi, WorkingHours(LocalTime(22, 0), LocalTime(6, 0), WorkingHours.EVERY_DAY))
        // 00:00–06:00 is six hours against 22:00–24:00's two.
        assertEquals(Segment(0, 360), OverlapFinder.longestWindow(thursday, tbilisi, listOf(night)))
    }

    @Test
    fun `there is no longest window when nothing is shared`() {
        val honolulu = listOf(ZoneSchedule(tbilisi), ZoneSchedule(TimeZone.of("Pacific/Honolulu")))
        assertNull(OverlapFinder.longestWindow(thursday, tbilisi, honolulu))
    }

    @Test
    fun `a meaningless length or step is rejected`() {
        assertFailsWith<IllegalArgumentException> { OverlapFinder.slots(thursday, tbilisi, pair, lengthMinutes = 0) }
        assertFailsWith<IllegalArgumentException> { OverlapFinder.slots(thursday, tbilisi, pair, lengthMinutes = 60, stepMinutes = 0) }
    }
}
