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

/**
 * A working day with a hole in it: the long lunch, the split shift, the rota with a gap.
 *
 * The hole is the entire point. A model that only holds a start and an end has to either pretend
 * the person is at their desk through lunch or pretend they work two separate days, and the first
 * of those is how a scheduler ends up confidently offering a meeting at two o'clock in Madrid.
 */
class SplitShiftTest {

    private val madrid = TimeZone.of("Europe/Madrid")
    private val london = TimeZone.of("Europe/London")
    private val thursday = LocalDate(2026, 8, 27)

    private fun minute(hhmm: String) = LocalTime.parse(hhmm).toSecondOfDay() / 60
    private fun seg(from: String, to: String) = Segment(minute(from), minute(to))
    private fun at(hhmm: String) = LocalTime.parse(hhmm)

    /** Nine to two, back at four, finishing at eight. */
    private val spanishDay = WorkingHours.withBreak(at("09:00"), at("14:00"), at("16:00"), at("20:00"))

    // ───────── the model ─────────

    @Test
    fun aBreakIsATrueGapNotALongerDay() {
        val projected = OverlapFinder.project(thursday, madrid, ZoneSchedule(madrid, spanishDay))
        assertEquals(listOf(seg("09:00", "14:00"), seg("16:00", "20:00")), projected)
    }

    @Test
    fun durationCountsTimeWorkedNotTimeElapsed() {
        assertEquals(9 * 60, spanishDay.durationMinutes) // five hours plus four, not eleven
        assertEquals(at("09:00"), spanishDay.start)
        assertEquals(at("20:00"), spanishDay.end)
    }

    @Test
    fun oneUnbrokenDayIsStillWrittenTheShortWay() {
        val short = WorkingHours(at("09:00"), at("18:00"))
        assertEquals(WorkingHours(listOf(Shift(at("09:00"), at("18:00")))), short)
        assertEquals(1, short.shifts.size)
        assertEquals(9 * 60, short.durationMinutes)
    }

    // ───────── the invariant ─────────

    @Test
    fun overlappingShiftsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            WorkingHours(listOf(Shift(at("09:00"), at("13:00")), Shift(at("12:00"), at("17:00"))))
        }
    }

    @Test
    fun shiftsOutOfOrderAreRejected() {
        // Not pedantry: an unordered list would make two identical working days compare unequal.
        assertFailsWith<IllegalArgumentException> {
            WorkingHours(listOf(Shift(at("16:00"), at("20:00")), Shift(at("09:00"), at("14:00"))))
        }
    }

    @Test
    fun aDayWithNoShiftsIsRejected() {
        assertFailsWith<IllegalArgumentException> { WorkingHours(emptyList()) }
    }

    @Test
    fun aShiftRunningPastMidnightHasToBeTheLastOne() {
        // Falls out of the ordering rule rather than being a special case: nothing can start after
        // a shift that has already run into tomorrow.
        WorkingHours(listOf(Shift(at("09:00"), at("13:00")), Shift(at("22:00"), at("06:00"))))
        assertFailsWith<IllegalArgumentException> {
            WorkingHours(listOf(Shift(at("22:00"), at("06:00")), Shift(at("09:00"), at("13:00"))))
        }
    }

    @Test
    fun touchingShiftsAreAllowedAndReadAsOneStretch() {
        // Half-open intervals, so 13:00 belongs to the second shift only.
        val handover = WorkingHours(listOf(Shift(at("09:00"), at("13:00")), Shift(at("13:00"), at("17:00"))))
        assertEquals(8 * 60, handover.durationMinutes)
        assertEquals(
            listOf(seg("09:00", "17:00")),
            OverlapFinder.project(thursday, madrid, ZoneSchedule(madrid, handover)),
        )
    }

    // ───────── what it changes for callers ─────────

    @Test
    fun nobodyIsOfferedAMeetingDuringSomebodyElsesLunch() {
        // London works straight through; Madrid does not. Madrid is an hour ahead, so its 14:00–16:00
        // break is 13:00–15:00 in London, and the shared day has to be two windows rather than one.
        val schedules = listOf(
            ZoneSchedule(london, WorkingHours(at("09:00"), at("18:00"))),
            ZoneSchedule(madrid, spanishDay),
        )
        val windows = OverlapFinder.sharedWindows(thursday, london, schedules)
        assertEquals(listOf(seg("09:00", "13:00"), seg("15:00", "18:00")), windows)
        assertEquals(7 * 60, OverlapFinder.totalMinutes(windows))
    }

    @Test
    fun aSlotIsNeverOfferedStraddlingTheBreak() {
        // The failure that would matter most in practice: an hour-long meeting starting at 12:30
        // London runs into Madrid's lunch, so it must not be offered.
        val schedules = listOf(
            ZoneSchedule(london, WorkingHours(at("09:00"), at("18:00"))),
            ZoneSchedule(madrid, spanishDay),
        )
        val slots = OverlapFinder.slots(thursday, london, schedules, lengthMinutes = 60, stepMinutes = 30)
        assertTrue(slots.isNotEmpty())
        assertTrue(
            slots.none { it.startMinute < minute("13:00") && it.endMinute > minute("13:00") },
            "a slot crossed the start of lunch: $slots",
        )
        assertTrue(
            slots.none { it.startMinute in minute("13:00") until minute("15:00") },
            "a slot began during lunch: $slots",
        )
    }

    @Test
    fun theLabelShowsBothHalvesOfTheDay() {
        val windows = OverlapFinder.sharedWindows(thursday, madrid, listOf(ZoneSchedule(madrid, spanishDay)))
        assertEquals("09:00–14:00, 16:00–20:00", OverlapFinder.label(thursday, madrid, windows))
    }

    @Test
    fun aBreakEdgeIsAttributedToWhoeverTakesTheBreak() {
        val madridSchedule = ZoneSchedule(madrid, spanishDay)
        val londonSchedule = ZoneSchedule(london, WorkingHours(at("09:00"), at("18:00")))
        val constraints = OverlapFinder.constraints(thursday, london, listOf(londonSchedule, madridSchedule))

        assertEquals(2, constraints.size)
        // The morning window closes because Madrid goes to lunch, not because London leaves.
        assertEquals(listOf(madridSchedule), constraints[0].closesWith)
        // The afternoon window opens when Madrid comes back.
        assertEquals(listOf(madridSchedule), constraints[1].opensWith)
    }

    @Test
    fun aSplitShiftAcrossMidnightStillProjectsBothHalves() {
        // A support rota: a morning stint and an overnight one, on Thursdays.
        val rota = WorkingHours(
            listOf(Shift(at("08:00"), at("12:00")), Shift(at("22:00"), at("04:00"))),
            days = setOf(DayOfWeek.THURSDAY),
        )
        val projected = OverlapFinder.project(thursday, madrid, ZoneSchedule(madrid, rota))
        assertEquals(listOf(seg("08:00", "12:00"), Segment(minute("22:00"), 24 * 60)), projected)
        assertEquals(10 * 60, rota.durationMinutes)
    }

    @Test
    fun theOvernightHalfLandsOnTheFollowingDayToo() {
        val rota = WorkingHours(
            listOf(Shift(at("08:00"), at("12:00")), Shift(at("22:00"), at("04:00"))),
            days = setOf(DayOfWeek.THURSDAY),
        )
        val friday = LocalDate(2026, 8, 28)
        val projected = OverlapFinder.project(friday, madrid, ZoneSchedule(madrid, rota))
        assertEquals(listOf(Segment(0, minute("04:00"))), projected)
    }

    @Test
    fun twoPeopleWithDifferentBreaksCanStillMiss() {
        // Both work a split day, and their halves interleave so there is nothing shared at all.
        val morningOnly = WorkingHours(listOf(Shift(at("09:00"), at("12:00"))), days = WorkingHours.EVERY_DAY)
        val afternoonOnly = WorkingHours(listOf(Shift(at("13:00"), at("17:00"))), days = WorkingHours.EVERY_DAY)
        val windows = OverlapFinder.sharedWindows(
            thursday,
            madrid,
            listOf(ZoneSchedule(madrid, morningOnly), ZoneSchedule(madrid, afternoonOnly)),
        )
        assertEquals(emptyList(), windows)
        assertNull(OverlapFinder.label(thursday, madrid, windows))
    }
}
