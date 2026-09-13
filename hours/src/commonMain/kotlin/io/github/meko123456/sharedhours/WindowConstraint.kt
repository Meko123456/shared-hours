package io.github.meko123456.sharedhours

/**
 * A shared window together with whose working hours are holding it in.
 *
 * When the answer is "you have one hour", the useful next sentence is "because Dana starts at
 * eight" — a team can act on that. A bare hour tells them nothing they can change.
 */
public data class WindowConstraint(
    public val window: Segment,
    /** Schedules whose own working day begins exactly when this window opens. */
    public val opensWith: List<ZoneSchedule>,
    /** Schedules whose own working day ends exactly when this window closes. */
    public val closesWith: List<ZoneSchedule>,
) {
    /**
     * True when the edge is the day itself rather than anybody's hours — a window running to
     * midnight is cut off by the date being asked about, not by a person, and moving somebody's
     * hours will not widen it.
     */
    public val boundedByTheDay: Boolean get() = opensWith.isEmpty() || closesWith.isEmpty()
}
