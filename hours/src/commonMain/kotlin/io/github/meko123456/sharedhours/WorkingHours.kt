package io.github.meko123456.sharedhours

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * A working day in its own local time, and which days of the week it happens on.
 *
 * The interval is **half-open**: with `09:00–18:00`, 18:00 is already off work. That is what makes
 * two adjacent shifts meet exactly rather than overlapping by a minute.
 *
 * An [end] at or before [start] means the shift **crosses midnight** — `22:00–06:00` is a night
 * shift, not an empty one. [days] then means the days the shift *starts*: a Friday night shift runs
 * into Saturday morning and is still a Friday shift.
 *
 * [days] defaults to Monday–Friday, which is right for most of the world and wrong for enough of it
 * to be worth setting. The working week is not universal: much of the Gulf ran Friday–Saturday
 * weekends until recently and parts still do, Nepal takes only Saturday, and Israel takes
 * Friday–Saturday. Getting this wrong is how a scheduler confidently offers a meeting slot on the
 * one morning nobody is there.
 */
public data class WorkingHours(
    public val start: LocalTime,
    public val end: LocalTime,
    public val days: Set<DayOfWeek> = MONDAY_TO_FRIDAY,
) {
    /** True when [end] is at or before [start], meaning the shift runs past local midnight. */
    public val crossesMidnight: Boolean get() = end <= start

    /** Length of one shift in minutes. A `start == end` pair is a full 24 hours, not zero. */
    public val durationMinutes: Int
        get() {
            val startMinute = start.toSecondOfDay() / 60
            val endMinute = end.toSecondOfDay() / 60
            return if (crossesMidnight) MINUTES_PER_DAY - startMinute + endMinute else endMinute - startMinute
        }

    /** Whether a shift *starts* on [date], in the zone this schedule belongs to. */
    public fun startsOn(date: LocalDate): Boolean = date.dayOfWeek in days

    public companion object {
        /** Minutes in a nominal day. Note that a real day can be 1380 or 1500 — see [OverlapFinder]. */
        public const val MINUTES_PER_DAY: Int = 24 * 60

        /** Monday to Friday. */
        public val MONDAY_TO_FRIDAY: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )

        /** Sunday to Thursday — the working week in much of the Middle East. */
        public val SUNDAY_TO_THURSDAY: Set<DayOfWeek> = setOf(
            DayOfWeek.SUNDAY,
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
        )

        /** Every day, for a rota or an always-on service desk. */
        public val EVERY_DAY: Set<DayOfWeek> = DayOfWeek.entries.toSet()

        /** Nine to six, Monday to Friday. */
        public val Default: WorkingHours = WorkingHours(LocalTime(9, 0), LocalTime(18, 0))
    }
}
