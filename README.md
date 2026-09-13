# shared-hours

[![CI](https://github.com/Meko123456/shared-hours/actions/workflows/ci.yml/badge.svg)](https://github.com/Meko123456/shared-hours/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![API docs](https://img.shields.io/badge/API-docs-blue.svg)](https://meko123456.github.io/shared-hours/)

**When is everyone at work at the same time?**

Working-hours overlap across time zones, for Kotlin Multiplatform. Give it a set of people, their
zones and the hours they work; it gives back the minutes of a chosen day when all of them are at
their desks.

```kotlin
val windows = OverlapFinder.sharedWindows(
    date = LocalDate(2026, 9, 14),
    home = TimeZone.of("Asia/Tbilisi"),
    schedules = listOf(
        ZoneSchedule(TimeZone.of("Asia/Tbilisi")),
        ZoneSchedule(TimeZone.of("Europe/London")),
        ZoneSchedule(TimeZone.of("America/New_York"), WorkingHours(LocalTime(8, 0), LocalTime(17, 0))),
    ),
)

OverlapFinder.label(date, home, windows)   // "16:00–17:00"
OverlapFinder.totalMinutes(windows)        // 60
```

## Why it is not just subtracting offsets

Three things make this harder than it looks, and all three are why this exists as a library rather
than a helper function.

**A day is not always 24 hours.** The day runs between two consecutive local midnights in your home
zone, so when the clocks change it is 23 or 25 hours long. Everything here is measured in *elapsed*
minutes from the first midnight, which stays linear across the change; wall-clock times are derived
at the very end and never computed with. Code that assumes 1440 is wrong twice a year, in the
direction that silently deletes an hour of everyone's availability.

```kotlin
OverlapFinder.dayLengthMinutes(LocalDate(2026, 10, 25), TimeZone.of("Europe/London"))  // 1500
```

**A working day need not sit inside yours.** Nine to six in Los Angeles is eight in the evening to
five the next morning in Tbilisi, so it lands as *two* pieces, one at each end of your day. That is
why every result here is a list of [`Segment`](hours/src/commonMain/kotlin/io/github/meko123456/sharedhours/Segment.kt)
rather than a single range.

**The working week is not the same everywhere.** Much of the Gulf ran a Friday–Saturday weekend until
recently and parts still do; Nepal takes only Saturday; Israel takes Friday–Saturday. A schedule only
contributes on days it actually works — checked against the local date **in that person's own zone**,
which can be a different calendar day from yours.

```kotlin
ZoneSchedule(
    TimeZone.of("Asia/Dubai"),
    WorkingHours(LocalTime(8, 0), LocalTime(17, 0), WorkingHours.SUNDAY_TO_THURSDAY),
)
```

**And neither is the calendar.** Holidays and leave sit outside the weekly rhythm, and they are
exactly where a confident wrong answer wastes a morning:

```kotlin
ZoneSchedule(london, calendar = WorkCalendar.excluding(bankHolidays))
ZoneSchedule(tbilisi, calendar = { it !in danasLeave })     // or any rule at all
```

A predicate rather than a list of dates, deliberately: holiday calendars are national, they move
(Easter, Eid, substitute days when a holiday falls at a weekend), and keeping them correct for every
country is a maintenance job with no end. Back it with whatever source you already trust. Like the
weekday check, it is evaluated against the local date **in that person's own zone**.

## Install

```kotlin
dependencies {
    implementation("io.github.meko123456:shared-hours:0.1.0")
}
```

> **Not on Maven Central yet.** The release pipeline is wired and green, but `0.1.0` only publishes
> once the signing key is in place — [#1](https://github.com/Meko123456/shared-hours/issues/1).

Targets: **JVM**, **Android** (minSdk 21), **iosArm64**, **iosSimulatorArm64**. The suite runs on the
JVM and on an iOS simulator in CI, which is where the platform time-zone databases differ.

One dependency, and an unavoidable one: `kotlinx-datetime`, because this is time-zone arithmetic and
that needs a tz database. Everything above it is integer maths.

## The day axis

Results are [`Segment`](hours/src/commonMain/kotlin/io/github/meko123456/sharedhours/Segment.kt)s —
half-open spans `[startMinute, endMinute)` of elapsed minutes since your home midnight. Half-open
because two adjacent shifts should meet exactly rather than overlap by a minute, which also means a
window that closes at 17:00 as another opens at 17:00 is correctly **no overlap at all**.

| Call | Gives you |
|---|---|
| `sharedWindows(date, home, schedules)` | The minutes everyone is at work |
| `project(date, home, schedule)` | Where one person's day lands on yours |
| `dayStart` / `dayLengthMinutes` | The day's bounds, DST included |
| `wallTime(date, home, minute)` | A point on the axis as a clock time |
| `minuteOf(instant, date, home)` | An instant as a point on the axis |
| `slots(…, lengthMinutes)` | Every place a meeting of that length fits |
| `nextSlot(from, …)` | The first slot from a date forward, searching days |
| `longestWindow(…)` | The longest single stretch, when nothing fits |
| `constraints(…)` | Whose hours are holding each window in |
| `totalMinutes` / `label` | How much, and how to print it |

Nothing reads a clock. Every function takes the date it works on, which is what makes all of the
above testable without mocking time.

## Where does a meeting fit?

"When is everyone free" is not the question people ask. A twenty-minute window is no use for a
half-hour call, and a four-hour window has eight plausible starts in it.

```kotlin
OverlapFinder.slots(date, home, schedules, lengthMinutes = 30)             // every half-hour slot
OverlapFinder.slots(date, home, schedules, lengthMinutes = 60, stepMinutes = 15)  // overlapping
OverlapFinder.longestWindow(date, home, schedules)                          // the best you can do
```

A slot never straddles two windows — the gap between them is there because somebody is away from
their desk. Starts align to multiples of the step, so the default lands them on the hour and the half
hour.

One day at a time is rarely the real question either. The answer to "when can these four *next* meet
for an hour" may be this afternoon, or a week on Tuesday because a bank holiday and a Friday–Saturday
weekend are in the way:

```kotlin
OverlapFinder.nextSlot(from = today, home, schedules, lengthMinutes = 60)
// DatedSegment(date=2026-08-31, segment=12:00–13:00)
```

The search is bounded — `withinDays` defaults to 14 — because a set of zones that can never overlap
would otherwise be searched forever. `null` means "nothing in the next two weeks", which is a real
answer worth showing.

## Who is the constraint?

"You have one hour" is not actionable. "One hour, because London arrives at 12:00 your time and
Dubai leaves at 17:00" is — a team can move one of those.

```kotlin
val (window, opensWith, closesWith) = OverlapFinder.constraints(date, home, schedules).first()
```

An edge can also belong to nobody: a window running to midnight is bounded by the day you asked
about rather than by a person, and no amount of moving hours will widen it. That is an empty list,
and `boundedByTheDay` names it.

## A worked example

[`:sample`](sample/src/main/kotlin/Main.kt) runs a four-person team across a week:

```sh
./gradlew :sample:run
```

```
When is everyone at work at once?

  monday       16:00–17:00      60m
  ...
  friday       — nobody
  saturday     — nobody

Why is the window only an hour?

  16:00–17:00
    opens when Dana arrives
    closes when Aisha leaves

London is on leave all week. When can the four next meet for an hour?

  2026-09-21 (monday)  16:00–17:00

The same team seen from London, across the weekend the clocks go back:

  sunday       — nobody         the day is 1500 minutes long
  monday       09:00–13:00      the day is 1440 minutes long
```

Four zones leaves exactly one shared hour, and it survives only because Dana starts at eight. Friday
is empty because Dubai's week ends on Thursday, and a week of leave in London pushes the next
possible meeting out by seven days.

## Where it came from

Extracted from [Dro](https://github.com/Meko123456/Dro), a multi-city clock built around this exact
question. Its sixteen tests are carried over here unchanged in meaning, on a weekday rather than a
Saturday — because the library, unlike the app it came from, now knows the difference.

## Building

```sh
./gradlew apiCheck                        # the public ABI still matches api/
./gradlew :hours:jvmTest                  # the suite on the JVM
./gradlew :hours:iosSimulatorArm64Test    # the same suite on an iOS simulator
./gradlew :sample:run                     # the worked example
```

## License

MIT — see [LICENSE](LICENSE).
