package io.github.meko123456.sharedhours

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * When is everybody at work at the same time?
 *
 * Projects each person's working hours onto one day in one chosen zone — the **home day** — and
 * intersects them. The answer is a list of [Segment]s on that day's axis, which a caller turns into
 * text with [label] or draws as a strip.
 *
 * ```
 * val windows = OverlapFinder.sharedWindows(
 *     date = LocalDate(2026, 9, 14),
 *     home = TimeZone.of("Asia/Tbilisi"),
 *     schedules = listOf(
 *         ZoneSchedule(TimeZone.of("Asia/Tbilisi")),
 *         ZoneSchedule(TimeZone.of("Europe/London")),
 *         ZoneSchedule(TimeZone.of("America/New_York")),
 *     ),
 * )
 * OverlapFinder.label(date, home, windows)   // "17:00–18:00"
 * ```
 *
 * ## Three things this gets right that the obvious version does not
 *
 * **A day is not always 24 hours.** The home day runs between two consecutive local midnights, so on
 * a daylight-saving change it is 23 or 25 hours long and [dayLengthMinutes] says which. Everything
 * is measured as elapsed minutes from the first midnight, which stays linear across the change;
 * wall-clock labels are derived at the end, never computed with.
 *
 * **A working day need not sit inside the home day.** Nine to six in Los Angeles is eight in the
 * evening to five the next morning in Tbilisi, so it lands as *two* segments — one at each end of
 * the home day. That is why every result here is a list.
 *
 * **The working week is not the same everywhere.** A schedule only contributes on days it actually
 * works, checked against the *local* date in that person's own zone, which can be a different
 * calendar day from the home one.
 *
 * Every function is pure and takes the date it works on, so none of this reads a clock.
 */
public object OverlapFinder {

    /** The instant at which [date] begins in [home]. */
    public fun dayStart(date: LocalDate, home: TimeZone): Instant = date.atStartOfDayIn(home)

    /**
     * How long [date] lasts in [home], in minutes.
     *
     * 1440 on a normal day, 1380 when the clocks go forward and 1500 when they go back. Anything
     * that assumes 1440 is wrong twice a year, in the direction that quietly drops an hour of
     * everyone's availability.
     */
    public fun dayLengthMinutes(date: LocalDate, home: TimeZone): Int =
        minutesBetween(dayStart(date, home), dayStart(date.plus(1, DateTimeUnit.DAY), home))

    /** The wall-clock time in [home] at [minute] on the axis of [date]. */
    public fun wallTime(date: LocalDate, home: TimeZone, minute: Int): LocalTime =
        (dayStart(date, home) + minute.minutes).toLocalDateTime(home).time

    /**
     * Where [instant] falls on the axis of the home day [date].
     *
     * May be negative or past the end of the day; that is the caller's to interpret, and is how "the
     * meeting you are asking about is not today" is detected rather than silently clamped.
     */
    public fun minuteOf(instant: Instant, date: LocalDate, home: TimeZone): Int =
        minutesBetween(dayStart(date, home), instant)

    /**
     * Where one schedule's working hours land on the home day [date]: zero, one or two segments,
     * clipped to the day.
     *
     * Two because a shift can straddle either end of the home day, and zero because the person may
     * not work that day at all.
     */
    public fun project(date: LocalDate, home: TimeZone, schedule: ZoneSchedule): List<Segment> {
        val dayStart = dayStart(date, home)
        val dayEnd = dayStart(date.plus(1, DateTimeUnit.DAY), home)
        val dayLength = minutesBetween(dayStart, dayEnd)

        // Only three local dates in the other zone can touch this home day, whatever the offset:
        // the one the home day starts on, and its neighbours either side.
        val anchor = dayStart.toLocalDateTime(schedule.zone).date
        val found = ArrayList<Segment>(2)

        for (offset in -1..1) {
            val localDate = anchor.plus(offset, DateTimeUnit.DAY)
            // Checked against the local date in *their* zone, which can be a different calendar day
            // from the home one — the whole point of asking.
            if (!schedule.hours.startsOn(localDate)) continue

            val shiftStart = LocalDateTime(localDate, schedule.hours.start).toInstant(schedule.zone)
            val endDate = if (schedule.hours.crossesMidnight) {
                localDate.plus(1, DateTimeUnit.DAY)
            } else {
                localDate
            }
            val shiftEnd = LocalDateTime(endDate, schedule.hours.end).toInstant(schedule.zone)

            val from = max(minutesBetween(dayStart, shiftStart), 0)
            val to = min(minutesBetween(dayStart, shiftEnd), dayLength)
            if (to > from) found += Segment(from, to)
        }
        return merge(found)
    }

    /**
     * The minutes of the home day when **every** schedule is at work.
     *
     * Empty when there is no such moment — including when one person simply is not working that day,
     * which is the honest answer rather than a window they will not be in.
     *
     * An empty [schedules] list means nobody is constrained, so the answer is the whole day.
     */
    public fun sharedWindows(
        date: LocalDate,
        home: TimeZone,
        schedules: List<ZoneSchedule>,
    ): List<Segment> {
        var shared = listOf(Segment(0, dayLengthMinutes(date, home)))
        for (schedule in schedules) {
            shared = intersect(shared, project(date, home, schedule))
            if (shared.isEmpty()) break
        }
        return shared
    }

    /** Total length of [segments] in minutes. */
    public fun totalMinutes(segments: List<Segment>): Int = segments.sumOf { it.lengthMinutes }

    /**
     * `09:00–16:30` per segment, joined with `, `, or `null` when there is no shared time at all.
     *
     * Null rather than an empty string so "no overlap" has to be handled rather than rendered as a
     * blank line.
     */
    public fun label(date: LocalDate, home: TimeZone, segments: List<Segment>): String? {
        if (segments.isEmpty()) return null
        return segments.joinToString(", ") { segment ->
            "${format(wallTime(date, home, segment.startMinute))}–" +
                format(wallTime(date, home, segment.endMinute))
        }
    }

    /** Every span present in both lists. */
    internal fun intersect(a: List<Segment>, b: List<Segment>): List<Segment> {
        val out = ArrayList<Segment>()
        for (x in a) {
            for (y in b) {
                val from = max(x.startMinute, y.startMinute)
                val to = min(x.endMinute, y.endMinute)
                if (to > from) out += Segment(from, to)
            }
        }
        return merge(out)
    }

    /** Sorts, then joins spans that overlap or merely touch. */
    internal fun merge(segments: List<Segment>): List<Segment> {
        if (segments.size < 2) return segments
        val sorted = segments.sortedBy { it.startMinute }
        val out = ArrayList<Segment>()
        var current = sorted.first()
        for (next in sorted.drop(1)) {
            current = if (next.startMinute <= current.endMinute) {
                Segment(current.startMinute, max(current.endMinute, next.endMinute))
            } else {
                out += current
                next
            }
        }
        out += current
        return out
    }

    private fun minutesBetween(from: Instant, to: Instant): Int = (to - from).inWholeMinutes.toInt()

    private fun format(time: LocalTime): String =
        "${pad(time.hour)}:${pad(time.minute)}"

    private fun pad(value: Int): String = if (value < 10) "0$value" else "$value"
}
