package io.github.meko123456.sharedhours

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * A working day as the [Shift]s it is made of, and which days of the week it happens on.
 *
 * Most days are one shift and the two-time constructor says so: `WorkingHours(LocalTime(9, 0),
 * LocalTime(18, 0))`. Plenty of days are not. A long lunch in Spain, a split shift on a support
 * rota, a driver's morning and evening runs — all of them are one working day with a hole in the
 * middle, and a model that can only hold `start` and `end` has to either lie about the hole or
 * pretend the person works two separate days.
 *
 * [days] then means the days a shift *starts*: a Friday night shift runs into Saturday morning and
 * is still a Friday shift.
 *
 * [days] defaults to Monday–Friday, which is right for most of the world and wrong for enough of it
 * to be worth setting. The working week is not universal: much of the Gulf ran Friday–Saturday
 * weekends until recently and parts still do, Nepal takes only Saturday, and Israel takes
 * Friday–Saturday. Getting this wrong is how a scheduler confidently offers a meeting slot on the
 * one morning nobody is there.
 *
 * ## The order shifts come in
 *
 * [shifts] must be in ascending order of start time and must not overlap each other. Both are
 * checked, because the alternative is silent nonsense: an overlapping pair double-counts in
 * [durationMinutes], and an unordered list makes two identical working days compare unequal.
 *
 * A shift that crosses midnight therefore has to be the last one, which is the natural reading
 * anyway — nothing can start after a shift that has already run into tomorrow.
 *
 * The check is within one day only. Two schedules whose shifts collide *across* a midnight — a
 * night shift ending at 06:00 and a morning shift starting at 05:00 the next day — are not
 * something a single day's data can see, and are left to the caller.
 */
public data class WorkingHours(
    public val shifts: List<Shift>,
    public val days: Set<DayOfWeek> = MONDAY_TO_FRIDAY,
) {
    init {
        require(shifts.isNotEmpty()) { "a working day needs at least one shift" }
        var previousEnd = Int.MIN_VALUE
        var previous: Shift? = null
        for (shift in shifts) {
            require(shift.startMinute >= previousEnd) {
                "shifts must be in ascending order and must not overlap; " +
                    "${shift.start} starts before ${previous?.end} ends"
            }
            previousEnd = shift.startMinute + shift.durationMinutes
            previous = shift
        }
    }

    /** The common case: one unbroken stretch. */
    public constructor(
        start: LocalTime,
        end: LocalTime,
        days: Set<DayOfWeek> = MONDAY_TO_FRIDAY,
    ) : this(listOf(Shift(start, end)), days)

    /** Time actually worked in a day, breaks excluded. */
    public val durationMinutes: Int get() = shifts.sumOf { it.durationMinutes }

    /** When the day's first shift begins. */
    public val start: LocalTime get() = shifts.first().start

    /** When the day's last shift ends — the following morning, for a night shift. */
    public val end: LocalTime get() = shifts.last().end

    /** True when the day's last shift runs past local midnight. */
    public val crossesMidnight: Boolean get() = shifts.last().crossesMidnight

    /** Whether a shift *starts* on [date], in the zone this schedule belongs to. */
    public fun startsOn(date: LocalDate): Boolean = date.dayOfWeek in days

    public companion object {
        /** Minutes in a nominal day. Note that a real day can be 1380 or 1500 — see [OverlapFinder]. */
        public const val MINUTES_PER_DAY: Int = 24 * 60

        /**
         * A day split by one break — the long-lunch shape, and the reason this type holds a list.
         *
         * ```
         * WorkingHours.withBreak(LocalTime(9, 0), LocalTime(14, 0), LocalTime(16, 0), LocalTime(20, 0))
         * ```
         * is nine until two, back at four, finishing at eight: eight hours worked across a day that
         * spans eleven.
         */
        public fun withBreak(
            start: LocalTime,
            breakStart: LocalTime,
            breakEnd: LocalTime,
            end: LocalTime,
            days: Set<DayOfWeek> = MONDAY_TO_FRIDAY,
        ): WorkingHours = WorkingHours(
            listOf(Shift(start, breakStart), Shift(breakEnd, end)),
            days,
        )

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
