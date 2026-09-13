package io.github.meko123456.sharedhours

/**
 * A half-open span `[startMinute, endMinute)` on the **home-day axis**: elapsed minutes since the
 * home zone's midnight.
 *
 * The axis measures *elapsed* time rather than wall-clock time, which is what keeps it linear on a
 * day that gains or loses an hour to daylight saving. Minute 780 is 780 minutes after midnight
 * whatever the clocks did in between; it is only when that has to be shown to somebody that it
 * becomes a wall-clock time, via [OverlapFinder.wallTime].
 */
public data class Segment(
    public val startMinute: Int,
    public val endMinute: Int,
) {
    init {
        require(endMinute > startMinute) {
            "a segment must cover at least a minute, was $startMinute..$endMinute"
        }
    }

    public val lengthMinutes: Int get() = endMinute - startMinute
}
