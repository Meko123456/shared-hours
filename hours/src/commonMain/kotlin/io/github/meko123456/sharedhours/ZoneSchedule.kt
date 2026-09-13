package io.github.meko123456.sharedhours

import kotlinx.datetime.TimeZone

/**
 * Somebody's time zone together with the hours they work in it.
 *
 * One per person, or one per office — the finder does not care which, it intersects whatever it is
 * given.
 */
public data class ZoneSchedule(
    public val zone: TimeZone,
    public val hours: WorkingHours = WorkingHours.Default,
    /**
     * Dates this person is not working beyond the weekly pattern — holidays, leave, a shutdown.
     *
     * Defaults to no exceptions. Note that a [WorkCalendar] is compared by identity, so two
     * schedules built with separate but equivalent lambdas are not equal.
     */
    public val calendar: WorkCalendar = WorkCalendar.NoExceptions,
)
