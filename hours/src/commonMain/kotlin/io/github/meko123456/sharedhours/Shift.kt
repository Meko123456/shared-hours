package io.github.meko123456.sharedhours

import kotlinx.datetime.LocalTime

/**
 * One continuous stretch of a working day, in the worker's own local time.
 *
 * The interval is **half-open**: with `09:00–18:00`, 18:00 is already off work. That is what makes
 * two adjacent shifts meet exactly rather than overlapping by a minute.
 *
 * An [end] at or before [start] means the shift **crosses midnight** — `22:00–06:00` is a night
 * shift, not an empty one.
 */
public data class Shift(
    public val start: LocalTime,
    public val end: LocalTime,
) {
    /** True when [end] is at or before [start], meaning this shift runs past local midnight. */
    public val crossesMidnight: Boolean get() = end <= start

    /** Length in minutes. A `start == end` pair is a full 24 hours, not zero. */
    public val durationMinutes: Int
        get() {
            val startMinute = start.toSecondOfDay() / 60
            val endMinute = end.toSecondOfDay() / 60
            return if (crossesMidnight) {
                WorkingHours.MINUTES_PER_DAY - startMinute + endMinute
            } else {
                endMinute - startMinute
            }
        }

    /** Minutes from local midnight to [start]. The axis the day-order invariant is checked on. */
    internal val startMinute: Int get() = start.toSecondOfDay() / 60
}
