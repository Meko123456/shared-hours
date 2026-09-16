package io.github.meko123456.sharedhours

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Holidays and leave — the part the weekly pattern cannot express. */
class WorkCalendarTest {

    private val tbilisi = TimeZone.of("Asia/Tbilisi")
    private val london = TimeZone.of("Europe/London")
    private val losAngeles = TimeZone.of("America/Los_Angeles")

    private val thursday = LocalDate(2026, 8, 27)
    private val friday = LocalDate(2026, 8, 28)
    private val monday = LocalDate(2026, 8, 31)

    private fun minute(hhmm: String) = LocalTime.parse(hhmm).toSecondOfDay() / 60
    private fun seg(from: String, to: String) = Segment(minute(from), minute(to))

    @Test
    fun `the default is no exceptions at all`() {
        assertEquals(
            listOf(seg("09:00", "18:00")),
            OverlapFinder.project(thursday, tbilisi, ZoneSchedule(tbilisi)),
        )
    }

    @Test
    fun `a date on the calendar removes that person from the day`() {
        val onLeave = ZoneSchedule(tbilisi, calendar = WorkCalendar.excluding(setOf(thursday)))
        assertTrue(OverlapFinder.project(thursday, tbilisi, onLeave).isEmpty())
        // ...and only that day.
        assertEquals(listOf(seg("09:00", "18:00")), OverlapFinder.project(friday, tbilisi, onLeave))
    }

    @Test
    fun `one person's holiday empties the shared window`() {
        val holiday = ZoneSchedule(london, calendar = WorkCalendar.excluding(setOf(thursday)))
        val team = listOf(ZoneSchedule(tbilisi), holiday)
        assertTrue(OverlapFinder.sharedWindows(thursday, tbilisi, team).isEmpty())
        assertEquals(listOf(seg("12:00", "18:00")), OverlapFinder.sharedWindows(friday, tbilisi, team))
    }

    @Test
    fun `a holiday somewhere else does not stop anyone else working`() {
        val londonOff = ZoneSchedule(london, calendar = WorkCalendar.excluding(setOf(thursday)))
        assertTrue(OverlapFinder.project(thursday, tbilisi, londonOff).isEmpty())
        assertEquals(listOf(seg("09:00", "18:00")), OverlapFinder.project(thursday, tbilisi, ZoneSchedule(tbilisi)))
    }

    @Test
    fun `the calendar is read in the schedule's own zone`() {
        // On a Tbilisi Thursday, Los Angeles contributes two pieces: its Wednesday evening and its
        // Thursday. Marking the Los Angeles *Wednesday* off removes only the first of them.
        val wednesday = LocalDate(2026, 8, 26)
        val laOffWednesday = ZoneSchedule(losAngeles, calendar = WorkCalendar.excluding(setOf(wednesday)))
        assertEquals(2, OverlapFinder.project(thursday, tbilisi, ZoneSchedule(losAngeles)).size)
        assertEquals(listOf(Segment(1200, 1440)), OverlapFinder.project(thursday, tbilisi, laOffWednesday))
    }

    @Test
    fun `an arbitrary rule works just as well as a list`() {
        // Works on even days of the month only, expressed as a rule rather than enumerated.
        val evenDaysOnly = ZoneSchedule(tbilisi, calendar = { it.day % 2 == 0 })
        assertTrue(OverlapFinder.project(thursday, tbilisi, evenDaysOnly).isEmpty(), "the 27th is odd")
        assertTrue(OverlapFinder.project(friday, tbilisi, evenDaysOnly).isNotEmpty(), "the 28th is even")
    }

    @Test
    fun `a search walks over a holiday to the next working day`() {
        val team = listOf(
            ZoneSchedule(tbilisi),
            ZoneSchedule(london, calendar = WorkCalendar.excluding(setOf(thursday, friday, monday))),
        )
        val found = OverlapFinder.nextSlot(thursday, tbilisi, team, lengthMinutes = 60)
        assertEquals(LocalDate(2026, 9, 1), found?.date, "Thursday, Friday and Monday are out and the weekend is not worked")
    }
}
