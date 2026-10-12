package il.transit.core.present

import il.transit.core.plan.BigStopChange

/** How the first vehicle changes: ride it further, leave it earlier, or leave it where planned. */
enum class BigStopHow {
    /** "Stay on line 5 2 more stops to …". */
    STAY_ON,
    /** "Get off line 5 2 stops earlier, at …". */
    EARLIER,
    /** Same stop off the first vehicle, the second boarded at a big stop nearby: "Take line 18 from … instead". */
    SAME_STOP,
}

/**
 * What the "Safer change" row says (Phase 10 D5): "Stay on line 5 one more stop to Be'er Sheva
 * Central, take line 18 there. Walk 3 min to the intercity platforms. Same arrival; 7 min to
 * change instead of 2." The app turns it into string resources; numbers and line labels stay
 * data so Hebrew can wrap them.
 */
data class BigStopText(
    val how: BigStopHow,
    /** How many stops more (or fewer) on the first vehicle; 0 for [BigStopHow.SAME_STOP]. */
    val stops: Int,
    /** Line label of the first vehicle ("5"), or null for a train without one (use [firstKind]). */
    val firstLine: String?,
    val firstKind: LegKind,
    val secondLine: String?,
    val secondKind: LegKind,
    /** Where to get off the first vehicle. */
    val stop: String,
    /** Where to board the second one ([stop] when it is the same stop). */
    val boardStop: String,
    /** Minutes of walking from [stop] to [boardStop]; 0: no walk line. */
    val walkMin: Int,
    /** Minutes to change at [stop], and at the original stop (never below 0 on screen). */
    val slackMin: Int,
    val originalSlackMin: Int,
)

fun bigStopText(c: BigStopChange): BigStopText {
    val a = c.original.legs[c.transfer.fromLeg]
    val b = c.original.legs[c.transfer.toLeg]
    return BigStopText(
        how = when {
            c.extraStops > 0 -> BigStopHow.STAY_ON
            c.extraStops < 0 -> BigStopHow.EARLIER
            else -> BigStopHow.SAME_STOP
        },
        stops = kotlin.math.abs(c.extraStops),
        // A train's label is its long route name: the UI says "the train" instead.
        firstLine = lineLabel(a).takeIf { legKind(a.mode) != LegKind.TRAIN },
        firstKind = legKind(a.mode),
        secondLine = lineLabel(b).takeIf { legKind(b.mode) != LegKind.TRAIN },
        secondKind = legKind(b.mode),
        stop = c.stop,
        boardStop = c.boardStop,
        walkMin = c.walkMin,
        slackMin = c.slackMin.coerceAtLeast(0),
        originalSlackMin = c.originalSlackMin.coerceAtLeast(0),
    )
}
