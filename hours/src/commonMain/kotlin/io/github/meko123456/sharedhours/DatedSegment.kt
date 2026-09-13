package io.github.meko123456.sharedhours

import kotlinx.datetime.LocalDate

/**
 * A [Segment] together with the day whose axis it belongs to.
 *
 * A segment on its own is meaningless once more than one day is in play — minute 780 is only "13:00"
 * relative to some particular midnight — so anything that searches across days hands back both.
 */
public data class DatedSegment(
    public val date: LocalDate,
    public val segment: Segment,
)
