package io.github.meko123456.sharedhours

import kotlinx.datetime.LocalDate

/**
 * Whether a particular date is a working one, beyond the weekly rhythm.
 *
 * [WorkingHours.days] handles "Aisha does not work Fridays". It cannot express "Dana is on leave next
 * week" or "the 25th is a holiday in London and a normal Thursday in Tokyo" — and public holidays are
 * exactly where a confident wrong answer wastes a morning for everybody.
 *
 * This is a predicate rather than a list of dates on purpose. Holiday calendars are national, they
 * move (Easter, Eid, substitute days when a holiday falls at a weekend), and keeping them correct for
 * every country is a maintenance job with no end. A predicate lets a caller back it with whatever
 * source they already trust and leaves this library out of it.
 *
 * The date passed is always the **local** date in that schedule's own zone, which can be a different
 * calendar day from the one being asked about.
 */
public fun interface WorkCalendar {

    /** Whether work happens on [date], in this schedule's own zone. */
    public fun worksOn(date: LocalDate): Boolean

    public companion object {
        /** No exceptions — the weekly pattern is the whole story. */
        public val NoExceptions: WorkCalendar = WorkCalendar { true }

        /** Everything except the given dates: a holiday list, a block of leave. */
        public fun excluding(dates: Set<LocalDate>): WorkCalendar = WorkCalendar { it !in dates }
    }
}
