package io.github.meko123456.sharedhours

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The tripwire for the one dependency this suite cannot control: the platform's timezone database.
 *
 * Every other test here asserts real offsets for real zones on real dates, which is the right way to
 * test a library about time zones — a suite that mocked the offsets would only be testing its own
 * mock. The cost is that the answers come from whichever tzdb the JVM, the Apple runtime or Node
 * happens to ship, and those get updated. Zones really do change their rules: Mexico abandoned
 * daylight saving in 2022, several Gulf states moved their weekend the same year, and the European
 * Union has been on the verge of abolishing the clock change for most of a decade.
 *
 * When that happens to a zone used here, a dozen behavioural tests go red at once and none of them
 * says why. This one names the cause. It asserts nothing about [OverlapFinder]; it asserts the raw
 * facts every other test is built on, so a tzdb update trips this first and reads as what it is.
 *
 * **If this test fails, the library is probably fine.** Check whether the zone really did change its
 * rules. If it did, the fix is to update the expectations here and then the tests that depended on
 * them — not to work around the new offset.
 *
 * ## The rule for adding tests
 *
 * Prefer dates whose rules are settled, which in practice means the past. One date below is an
 * exception worth knowing about: the October 2026 transition was still a few weeks away when these
 * expectations were written, so it rests on the European Union not abolishing the clock change
 * between now and then. It is here because a 25-hour day has to be tested somewhere and the autumn
 * transition is the only place one exists.
 */
class TimeZoneAssumptionsTest {

    private val tbilisi = TimeZone.of("Asia/Tbilisi")
    private val dubai = TimeZone.of("Asia/Dubai")
    private val london = TimeZone.of("Europe/London")
    private val madrid = TimeZone.of("Europe/Madrid")
    private val kolkata = TimeZone.of("Asia/Kolkata")
    private val tokyo = TimeZone.of("Asia/Tokyo")
    private val losAngeles = TimeZone.of("America/Los_Angeles")
    private val honolulu = TimeZone.of("Pacific/Honolulu")

    private val summer = LocalDate(2026, 8, 27)
    private val winter = LocalDate(2026, 1, 15)
    private val springForward = LocalDate(2026, 3, 29)
    private val fallBack = LocalDate(2026, 10, 25)

    /**
     * Minutes this zone is ahead of UTC at noon UTC on [date].
     *
     * Noon UTC rather than local noon on purpose: every clock change in these zones happens in the
     * small hours, so noon UTC is never ambiguous and never skipped in any of them.
     */
    private fun offsetAtNoonUtc(zone: TimeZone, date: LocalDate): Int =
        zone.offsetAt(LocalDateTime(date, LocalTime(12, 0)).toInstant(TimeZone.UTC)).totalSeconds / 60

    private fun assertOffset(expected: Int, zone: TimeZone, date: LocalDate) {
        val actual = offsetAtNoonUtc(zone, date)
        assertEquals(
            expected,
            actual,
            "$zone is $actual minutes from UTC on $date; this suite was written when it was $expected. " +
                "If the zone genuinely changed its rules, update this test first, then the tests that " +
                "depend on the old offset.",
        )
    }

    @Test
    fun zonesWithoutDaylightSavingStayWhereTheyAre() {
        listOf(summer, winter).forEach { date ->
            assertOffset(240, tbilisi, date)   // UTC+4 all year
            assertOffset(240, dubai, date)     // UTC+4 all year
            assertOffset(330, kolkata, date)   // UTC+5:30, the half-hour offset the axis has to survive
            assertOffset(540, tokyo, date)     // UTC+9 all year
            assertOffset(-600, honolulu, date) // UTC-10, the far end of the tests
        }
    }

    @Test
    fun zonesWithDaylightSavingAreWhereTheSuiteExpects() {
        assertOffset(60, london, summer)        // BST
        assertOffset(0, london, winter)         // GMT
        assertOffset(120, madrid, summer)       // CEST
        assertOffset(60, madrid, winter)        // CET
        assertOffset(-420, losAngeles, summer)  // PDT
        assertOffset(-480, losAngeles, winter)  // PST
    }

    @Test
    fun theTwoTransitionDaysStillTransition() {
        // Both changes land at 01:00 UTC, so noon UTC is after them either way.
        assertOffset(60, london, springForward)
        assertOffset(0, london, fallBack)
    }

    @Test
    fun theShortAndLongDaysAreStillShortAndLong() {
        // The reason the day axis counts elapsed minutes rather than wall-clock minutes. If these
        // three ever read 1440, the transitions have gone, not the arithmetic.
        assertEquals(1380, OverlapFinder.dayLengthMinutes(springForward, london), "23-hour day")
        assertEquals(1440, OverlapFinder.dayLengthMinutes(summer, london), "ordinary day")
        assertEquals(1500, OverlapFinder.dayLengthMinutes(fallBack, london), "25-hour day")
    }
}
