package io.github.meko123456.sharedhours.sample

import io.github.meko123456.sharedhours.OverlapFinder
import io.github.meko123456.sharedhours.WorkingHours
import io.github.meko123456.sharedhours.ZoneSchedule
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

/**
 * A week of a distributed team, printed.
 *
 * Four people in four zones, one of them on a Sunday–Thursday week. The run shows the three things
 * that make this harder than subtracting offsets: the shared window moves when a zone changes its
 * clocks, it disappears entirely at the weekend, and it disappears on a *different* set of days for
 * the person whose weekend is not Saturday and Sunday.
 *
 * `./gradlew :sample:run`
 */
public fun main() {
    val tbilisi = TimeZone.of("Asia/Tbilisi")
    val team = listOf(
        "Merab, Tbilisi" to ZoneSchedule(tbilisi),
        "Aisha, Dubai" to ZoneSchedule(
            TimeZone.of("Asia/Dubai"),
            WorkingHours(LocalTime(8, 0), LocalTime(17, 0), WorkingHours.SUNDAY_TO_THURSDAY),
        ),
        "Tom, London" to ZoneSchedule(TimeZone.of("Europe/London")),
        // An early starter, which turns out to be the only reason this team has any shared time
        // at all: at 09:00 she arrives exactly as Dubai leaves, and half-open intervals do not
        // overlap when they merely touch.
        "Dana, New York" to ZoneSchedule(
            TimeZone.of("America/New_York"),
            WorkingHours(LocalTime(8, 0), LocalTime(17, 0)),
        ),
    )

    println("A four-person team, seen from Tbilisi.\n")
    team.forEach { (who, schedule) ->
        println(
            "  %-16s %-20s %s–%s  %s".format(
                who.substringBefore(','),
                schedule.zone.id,
                schedule.hours.start,
                schedule.hours.end,
                schedule.hours.days.sortedBy { it.ordinal }.joinToString(" ") { it.name.take(2) },
            ),
        )
    }

    println("\nWhen is everyone at work at once?\n")
    var date = LocalDate(2026, 9, 14) // a Monday
    repeat(7) {
        val windows = OverlapFinder.sharedWindows(date, tbilisi, team.map { it.second })
        val label = OverlapFinder.label(date, tbilisi, windows) ?: "— nobody"
        val minutes = OverlapFinder.totalMinutes(windows)
        println("  %-12s %-16s %s".format(date.dayOfWeek.name.lowercase(), label, if (minutes > 0) "${minutes}m" else ""))
        date = date.plus(1, DateTimeUnit.DAY)
    }

    println("\nDrop New York and the window opens up:\n")
    val withoutNewYork = team.dropLast(1).map { it.second }
    date = LocalDate(2026, 9, 14)
    repeat(7) {
        val windows = OverlapFinder.sharedWindows(date, tbilisi, withoutNewYork)
        val label = OverlapFinder.label(date, tbilisi, windows) ?: "— nobody"
        println("  %-12s %s".format(date.dayOfWeek.name.lowercase(), label))
        date = date.plus(1, DateTimeUnit.DAY)
    }

    // Georgia has no daylight saving, so a Tbilisi day is always 1440 minutes. Seen from London it
    // is a different story: on 25 October the clocks go back and the day is 25 hours long. Nothing
    // above had to know that, because the axis is elapsed minutes rather than wall-clock time.
    println("\nThe same team seen from London, across the weekend the clocks go back:\n")
    val london = TimeZone.of("Europe/London")
    date = LocalDate(2026, 10, 23)
    repeat(5) {
        val windows = OverlapFinder.sharedWindows(date, london, withoutNewYork)
        println(
            "  %-12s %-16s the day is %d minutes long".format(
                date.dayOfWeek.name.lowercase(),
                OverlapFinder.label(date, london, windows) ?: "— nobody",
                OverlapFinder.dayLengthMinutes(date, london),
            ),
        )
        date = date.plus(1, DateTimeUnit.DAY)
    }
}
