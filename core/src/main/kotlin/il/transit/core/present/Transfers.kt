package il.transit.core.present

import il.transit.core.api.Itinerary

/**
 * The changes of vehicle in an option, and which are tight (Phase 10 D1, item 6). Computed
 * from the `plan` answer alone: no request.
 */
data class Transfer(
    /** Index into [Itinerary.legs] of the transit leg arrived on. */
    val fromLeg: Int,
    /** Index of the next transit leg. */
    val toLeg: Int,
    /** Where the next vehicle is boarded. */
    val stop: String,
    /** Next departure − arrival − the walk between, in seconds (negative: the plan can't make it). */
    val slackSec: Long,
    /** Minutes of walking between the two vehicles (0 when there is no walk). */
    val walkMin: Int,
    /** Same stop (or no walk between): no walking to do. */
    val sameStop: Boolean,
    /** A train on either side: a missed train costs more, so the warning comes earlier. */
    val train: Boolean,
) {
    /** Whole minutes of slack, rounded down (2 min 50 s is "2 min"). */
    val slackMin: Int get() = Math.floorDiv(slackSec, 60L).toInt()
}

/** How a tight transfer is said on the card. */
enum class TransferWarning {
    /** "Change at Savidor in 3 min". */
    CHANGE_IN,
    /** "Walk 4 min, 2 min to spare". */
    WALK_SPARE,
    /** Under a minute of slack, or none: "Tight: may miss the change". */
    TIGHT,
}

/**
 * Every change of vehicle: each pair of consecutive transit legs, with the street legs between
 * them counted as the walk. The leg times are live when the answer has them, else scheduled
 * (MOTIS's `startTime`/`endTime` are already the live ones), so a delay eats the slack.
 */
fun transfers(itin: Itinerary): List<Transfer> {
    val transit = itin.legs.indices.filter { itin.legs[it].isTransit }
    return transit.zipWithNext().map { (a, b) ->
        val prev = itin.legs[a]
        val next = itin.legs[b]
        val between = itin.legs.subList(a + 1, b)
        val walkSec = between.sumOf { (it.end.epochSecond - it.start.epochSecond).coerceAtLeast(0) }
        val walkMin = minutes(walkSec.toInt())
        val sameId = prev.to.stopId != null && prev.to.stopId == next.from.stopId
        Transfer(
            fromLeg = a,
            toLeg = b,
            stop = next.from.name,
            slackSec = next.start.epochSecond - prev.end.epochSecond - walkSec,
            walkMin = if (sameId) 0 else walkMin,
            sameStop = sameId || walkMin == 0,
            train = legKind(prev.mode) == LegKind.TRAIN || legKind(next.mode) == LegKind.TRAIN,
        )
    }
}

/** Owner decision 1 (2026-10-12): under 5 min, under 8 with a train on either side. */
const val TIGHT_MIN = 5
const val TIGHT_TRAIN_MIN = 8

/** Warn below this many minutes of slack; never lower than the user's buffer. */
fun warnBelowMin(t: Transfer, bufferMin: Int?): Int =
    maxOf(if (t.train) TIGHT_TRAIN_MIN else TIGHT_MIN, bufferMin ?: 0)

/** How to warn about [t], or null when it has room enough. */
fun transferWarning(t: Transfer, bufferMin: Int?): TransferWarning? = when {
    t.slackSec >= warnBelowMin(t, bufferMin) * 60L -> null
    t.slackMin <= 0 -> TransferWarning.TIGHT
    t.sameStop -> TransferWarning.CHANGE_IN
    else -> TransferWarning.WALK_SPARE
}

/** The transfers of [itin] that get an amber chip, with how to say each. */
fun tightTransfers(itin: Itinerary, bufferMin: Int?): List<Pair<Transfer, TransferWarning>> =
    transfers(itin).mapNotNull { t -> transferWarning(t, bufferMin)?.let { t to it } }
