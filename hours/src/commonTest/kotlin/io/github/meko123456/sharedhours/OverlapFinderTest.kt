package io.github.meko123456.sharedhours

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Every case Dro asserted on the version this was extracted from, plus the ones its version could
 * not express because it had no idea what a weekend was.
 *
 * The base date is a Thursday rather than Dro's Saturday: the hours here default to Monday–Friday,
 * so a weekend date would now correctly return nothing and the projections would prove nothing.
 * Offsets are identical either way.
 */
class OverlapFinderTest {

    private val tbilisi = TimeZone.of("Asia/Tbilisi")
    private val dubai = TimeZone.of("Asia/Dubai")
    private val london = TimeZone.of("Europe/London")
    private val kolkata = TimeZone.of("Asia/Kolkata")
    private val tokyo = TimeZone.of("Asia/Tokyo")
    private val losAngeles = TimeZone.of("America/Los_Angeles")
    private val honolulu = TimeZone.of("Pacific/Honolulu")

    private val thursday = LocalDate(2026, 8, 27)
    private val monday = LocalDate(2026, 8, 31)

    private fun minute(hhmm: String) = LocalTime.parse(hhmm).toSecondOfDay() / 60
    private fun seg(from: String, to: String) = Segment(minute(from), minute(to))

    // ───────── the working day itself ─────────

    @Test
    fun `working hours know their length and whether they cross midnight`() {
        val nineToSix = WorkingHours.Default
        assertEquals(540, nineToSix.durationMinutes)
        assertTrue(!nineToSix.crossesMidnight)

        val night = WorkingHours(LocalTime(22, 0), LocalTime(6, 0))
        assertTrue(night.crossesMidnight)
        assertEquals(480, night.durationMinutes)
    }

    @Test
    fun `a start equal to the end is a full day rather than an empty one`() {
        val alwaysOn = WorkingHours(LocalTime(0, 0), LocalTime(0, 0), WorkingHours.EVERY_DAY)
        assertTrue(alwaysOn.crossesMidnight)
        assertEquals(1440, alwaysOn.durationMinutes)
        assertEquals(listOf(Segment(0, 1440)), OverlapFinder.project(thursday, tbilisi, ZoneSchedule(tbilisi, alwaysOn)))
    }

    @Test
    fun `a segment must cover at least a minute`() {
        assertFailsWith<IllegalArgumentException> { Segment(600, 600) }
        assertFailsWith<IllegalArgumentException> { Segment(600, 500) }
    }

    // ───────── projecting one zone onto another ─────────

    @Test
    fun `a zone with the same offset projects onto the same minutes`() {
        assertEquals(listOf(seg("09:00", "18:00")), OverlapFinder.project(thursday, tbilisi, ZoneSchedule(dubai)))
    }

    @Test
    fun `the London working day lands three hours later in summer and four in winter`() {
        assertEquals(listOf(seg("12:00", "21:00")), OverlapFinder.project(thursday, tbilisi, ZoneSchedule(london)))
        val winter = LocalDate(2026, 1, 15)
        assertEquals(listOf(seg("13:00", "22:00")), OverlapFinder.project(winter, tbilisi, ZoneSchedule(london)))
    }

    @Test
    fun `half hour zones keep half hour boundaries`() {
        assertEquals(listOf(seg("07:30", "16:30")), OverlapFinder.project(thursday, tbilisi, ZoneSchedule(kolkata)))
    }

    @Test
    fun `a working day that straddles home midnight becomes two segments`() {
        // 09:00–18:00 in Los Angeles is 20:00 to 05:00 the next morning in Tbilisi.
        assertEquals(
            listOf(seg("00:00", "05:00"), Segment(1200, 1440)),
            OverlapFinder.project(thursday, tbilisi, ZoneSchedule(losAngeles)),
        )
    }

    @Test
    fun `a night shift in the home zone itself wraps too`() {
        val night = WorkingHours(LocalTime(22, 0), LocalTime(6, 0))
        assertEquals(
            listOf(Segment(0, 360), Segment(1320, 1440)),
            OverlapFinder.project(thursday, tbilisi, ZoneSchedule(tbilisi, night)),
        )
    }

    // ───────── the working week ─────────

    @Test
    fun `a schedule contributes nothing on a day it does not work`() {
        val saturday = LocalDate(2026, 8, 29)
        assertTrue(OverlapFinder.project(saturday, tbilisi, ZoneSchedule(tbilisi)).isEmpty())
        assertTrue(OverlapFinder.sharedWindows(saturday, tbilisi, listOf(ZoneSchedule(tbilisi))).isEmpty())
    }

    @Test
    fun `the working week is read in the schedule's own zone and not the home one`() {
        // On a Tbilisi Thursday, the small hours belong to Los Angeles' Wednesday, which is a
        // working day — so the day has two segments.
        assertEquals(2, OverlapFinder.project(thursday, tbilisi, ZoneSchedule(losAngeles)).size)
        // On a Tbilisi Monday, those same small hours belong to Los Angeles' Sunday, which is not.
        // A version that checked the home day's weekday would wrongly keep them.
        assertEquals(
            listOf(Segment(1200, 1440)),
            OverlapFinder.project(monday, tbilisi, ZoneSchedule(losAngeles)),
        )
    }

    @Test
    fun `a Gulf working week is on for Sunday and off for Friday`() {
        val gulf = ZoneSchedule(dubai, WorkingHours(LocalTime(8, 0), LocalTime(17, 0), WorkingHours.SUNDAY_TO_THURSDAY))
        val friday = LocalDate(2026, 8, 28)
        val sunday = LocalDate(2026, 8, 30)
        assertTrue(OverlapFinder.project(friday, dubai, gulf).isEmpty())
        assertEquals(listOf(seg("08:00", "17:00")), OverlapFinder.project(sunday, dubai, gulf))
    }

    @Test
    fun `a night shift belongs to the day it starts on`() {
        // Friday 22:00–06:00 runs into Saturday morning and is still a Friday shift; Saturday night
        // is not a shift at all.
        val fridayNights = WorkingHours(LocalTime(22, 0), LocalTime(6, 0), setOf(DayOfWeek.FRIDAY))
        val friday = LocalDate(2026, 8, 28)
        val saturday = LocalDate(2026, 8, 29)
        assertEquals(listOf(Segment(1320, 1440)), OverlapFinder.project(friday, tbilisi, ZoneSchedule(tbilisi, fridayNights)))
        assertEquals(listOf(Segment(0, 360)), OverlapFinder.project(saturday, tbilisi, ZoneSchedule(tbilisi, fridayNights)))
    }

    @Test
    fun `one person being off is enough to empty the shared window`() {
        val gulf = ZoneSchedule(dubai, WorkingHours(LocalTime(8, 0), LocalTime(17, 0), WorkingHours.SUNDAY_TO_THURSDAY))
        val friday = LocalDate(2026, 8, 28)
        // Tbilisi is at work on the Friday and Dubai is not, so there is no shared time.
        assertTrue(OverlapFinder.sharedWindows(friday, tbilisi, listOf(ZoneSchedule(tbilisi), gulf)).isEmpty())
    }

    // ───────── intersecting ─────────

    @Test
    fun `the shared window of home and London is noon to six`() {
        val shared = OverlapFinder.sharedWindows(thursday, tbilisi, listOf(ZoneSchedule(tbilisi), ZoneSchedule(london)))
        assertEquals(listOf(seg("12:00", "18:00")), shared)
        assertEquals(360, OverlapFinder.totalMinutes(shared))
        assertEquals("12:00–18:00", OverlapFinder.label(thursday, tbilisi, shared))
    }

    @Test
    fun `the shared window with Kolkata ends on the half hour`() {
        val shared = OverlapFinder.sharedWindows(thursday, tbilisi, listOf(ZoneSchedule(tbilisi), ZoneSchedule(kolkata)))
        assertEquals("09:00–16:30", OverlapFinder.label(thursday, tbilisi, shared))
    }

    @Test
    fun `Tokyo and Los Angeles only share the small hours`() {
        val shared = OverlapFinder.sharedWindows(thursday, tbilisi, listOf(ZoneSchedule(tokyo), ZoneSchedule(losAngeles)))
        assertEquals("04:00–05:00", OverlapFinder.label(thursday, tbilisi, shared))
    }

    @Test
    fun `no shared hours gives an empty list and a null label`() {
        val shared = OverlapFinder.sharedWindows(thursday, tbilisi, listOf(ZoneSchedule(tbilisi), ZoneSchedule(honolulu)))
        assertTrue(shared.isEmpty())
        assertNull(OverlapFinder.label(thursday, tbilisi, shared))
        assertEquals(0, OverlapFinder.totalMinutes(shared))
    }

    @Test
    fun `no schedules means the whole day is shared`() {
        assertEquals(listOf(Segment(0, 1440)), OverlapFinder.sharedWindows(thursday, tbilisi, emptyList()))
    }

    // ───────── daylight saving ─────────

    @Test
    fun `a spring forward day is 23 hours long and wall times skip the gap`() {
        // Europe/London springs forward at 01:00 GMT on 2026-03-29.
        val springForward = LocalDate(2026, 3, 29)
        assertEquals(1380, OverlapFinder.dayLengthMinutes(springForward, london))
        assertEquals(1440, OverlapFinder.dayLengthMinutes(thursday, london))
        assertEquals(LocalTime(0, 30), OverlapFinder.wallTime(springForward, london, 30))
        // Sixty elapsed minutes after midnight the wall clock already says 02:00.
        assertEquals(LocalTime(2, 0), OverlapFinder.wallTime(springForward, london, 60))
    }

    @Test
    fun `a fall back day is 25 hours long`() {
        assertEquals(1500, OverlapFinder.dayLengthMinutes(LocalDate(2026, 10, 25), london))
    }

    @Test
    fun `projection onto a shortened day still measures elapsed minutes`() {
        // Tbilisi 09:00 is 05:00Z is 06:00 BST, which is 300 elapsed minutes into London's 23-hour
        // day even though the wall clock jumped an hour inside that stretch.
        val springForward = LocalDate(2026, 3, 29)
        assertEquals(listOf(Segment(300, 840)), OverlapFinder.project(springForward, london, ZoneSchedule(tbilisi, WorkingHours(LocalTime(9, 0), LocalTime(18, 0), WorkingHours.EVERY_DAY))))
        assertEquals(LocalTime(6, 0), OverlapFinder.wallTime(springForward, london, 300))
    }

    // ───────── the axis ─────────

    @Test
    fun `minuteOf maps an instant onto the home axis`() {
        // 14:00 in Tbilisi on the Thursday.
        val instant = Instant.parse("2026-08-27T10:00:00Z")
        assertEquals(840, OverlapFinder.minuteOf(instant, thursday, tbilisi))
        // The same instant seen from the following day is before that day began.
        assertEquals(-600, OverlapFinder.minuteOf(instant, LocalDate(2026, 8, 28), tbilisi))
    }

    @Test
    fun `merge joins touching and overlapping segments`() {
        assertEquals(
            listOf(Segment(0, 300), Segment(600, 900)),
            OverlapFinder.merge(listOf(Segment(600, 800), Segment(0, 100), Segment(100, 300), Segment(700, 900))),
        )
    }

    @Test
    fun `intersect handles multi segment inputs`() {
        val a = listOf(Segment(0, 300), Segment(1200, 1440))
        val b = listOf(Segment(240, 780))
        assertEquals(listOf(Segment(240, 300)), OverlapFinder.intersect(a, b))
        assertTrue(OverlapFinder.intersect(a, listOf(Segment(300, 1200))).isEmpty(), "touching is not overlapping")
    }
}
