package il.transit.core.present

import il.transit.core.plan.BigStopChange

/**
 * What the "Safer change" row says (Phase 10 D5): "Stay on line 5 2 more stops to Be'er Sheva
 * Central, take line 18 there. Same arrival; 7 min to change instead of 2." The app turns it
 * into a string resource; numbers and line labels stay data so Hebrew can wrap them.
 */
data class BigStopText(
    /** True: stay on the first vehicle [stops] more stops. False: get off it [stops] earlier. */
    val stayOn: Boolean,
    /** How many stops more (or fewer) on the first vehicle; at least 1. */
    val stops: Int,
    /** Line label of the first vehicle ("5"), or null for a train without one (use [firstKind]). */
    val firstLine: String?,
    val firstKind: LegKind,
    val secondLine: String?,
    val secondKind: LegKind,
    /** Where to change instead. */
    val stop: String,
    /** Minutes to change at [stop], and at the original stop (never below 0 on screen). */
    val slackMin: Int,
    val originalSlackMin: Int,
)

fun bigStopText(c: BigStopChange): BigStopText {
    val a = c.original.legs[c.transfer.fromLeg]
    val b = c.original.legs[c.transfer.toLeg]
    return BigStopText(
        stayOn = c.extraStops > 0,
        stops = kotlin.math.abs(c.extraStops).coerceAtLeast(1),
        // A train's label is its long route name: the UI says "the train" instead.
        firstLine = lineLabel(a).takeIf { legKind(a.mode) != LegKind.TRAIN },
        firstKind = legKind(a.mode),
        secondLine = lineLabel(b).takeIf { legKind(b.mode) != LegKind.TRAIN },
        secondKind = legKind(b.mode),
        stop = c.stop,
        slackMin = c.slackMin.coerceAtLeast(0),
        originalSlackMin = c.originalSlackMin.coerceAtLeast(0),
    )
}
