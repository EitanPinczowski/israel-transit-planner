package il.transit.core.remind

import il.transit.core.api.Itinerary
import il.transit.core.api.Leg
import il.transit.core.api.parseTime
import il.transit.core.present.LegPalette
import il.transit.core.ride.RideProgress
import java.time.Duration
import java.time.Instant

/**
 * The progress bar of the Android 16 Live Update (Phase 10 D10), for "on the bus" and the
 * leave-now countdown. Pure: the Android side turns it into a `ProgressStyle` (16+) or a
 * plain progress bar (below), and [Chip] into "3 stops" / "8 min". 0 requests.
 *
 * The bar is the trip's planned timeline in seconds from its start: one segment per leg,
 * from the leg's start to the next leg's start (a wait at the stop belongs to the walk
 * before it), coloured like the leg everywhere else ([LegPalette]). A point marks each
 * boarding: walk → bus and every change of vehicle.
 */
data class LiveProgress(
    val segments: List<Segment>,
    /** Positions on the bar (same unit as [Segment.length]) where a vehicle is boarded. */
    val points: List<Int>,
    /** Where the tracker stands, 0..[max]. */
    val progress: Int,
    /** The status-bar chip; null leaves it to the notification's own countdown (`setWhen`). */
    val chip: Chip?,
) {
    data class Segment(val length: Int, val color: String)

    sealed interface Chip {
        /** "3 stops": the ride lists its stops. */
        data class Stops(val n: Int) : Chip

        /** "8 min": to the stop to get off at, when the leg lists no stops in between. */
        data class Minutes(val n: Int) : Chip
    }

    val max: Int get() = segments.sumOf { it.length }

    companion object {
        /** The bar before any fix: the tracker at the start. */
        fun start(itinerary: Itinerary): LiveProgress? = bar(itinerary)?.let { (s, p) -> LiveProgress(s, p, 0, null) }

        /**
         * During a ride. [p] is [il.transit.core.ride.RideTracker.progress] for the same fix;
         * null once every transit leg is done (the last walk: the tracker follows the clock).
         * [transitLeg] is [il.transit.core.ride.RideTracker.currentLeg]: the tracker's own index
         * among the transit legs, never re-guessed from names and times.
         * The tracker sits where the schedule is "now minus the delay", and never past the end
         * of the leg still being ridden: a late bus keeps it on that bus's segment.
         */
        fun ride(itinerary: Itinerary, p: RideProgress?, transitLeg: Int, now: Instant): LiveProgress? {
            val (segments, points) = bar(itinerary) ?: return null
            val max = segments.sumOf { it.length }
            val t0 = sched(itinerary.legs.first(), start = true)
            if (p == null) {
                val lastRide = itinerary.lastTransitLeg?.let { at(t0, sched(it, start = false)) } ?: 0
                return LiveProgress(segments, points, maxOf(lastRide, at(t0, now)).coerceIn(0, max), null)
            }
            // Early (or waiting for the next vehicle): the tracker follows the clock, never runs ahead of it.
            val planned = now.minusSeconds(60L * p.delayMin.coerceAtLeast(0))
            val leg = itinerary.legs.filter { it.isTransit }.getOrNull(transitLeg)
            val ceiling = leg?.let { at(t0, sched(it, start = false)) } ?: max
            val progress = at(t0, planned).coerceIn(0, ceiling.coerceAtMost(max))
            val chip = if (leg != null && leg.intermediateStops.isEmpty()) {
                // Never "0 min": at (or past) the estimated arrival the chip shows just the icon.
                minutesUntil(now, p.arriveAt).takeIf { it > 0 }?.let(Chip::Minutes)
            } else {
                Chip.Stops(p.stopsLeft)
            }
            return LiveProgress(segments, points, progress, chip)
        }

        /**
         * The leave-now countdown: the tracker walks towards the first boarding point and
         * stops there until the vehicle comes ([boardAt] = scheduled boarding + known delay).
         * No chip text: the chronometer counting down to [boardAt] stays right every second,
         * a fixed "8 min" would not (the countdown is not re-posted every minute).
         */
        fun countdown(itinerary: Itinerary, boardAt: Instant, now: Instant): LiveProgress? {
            val (segments, points) = bar(itinerary) ?: return null
            val board = points.firstOrNull() ?: return LiveProgress(segments, points, 0, null)
            val left = Duration.between(now, boardAt).seconds
            return LiveProgress(segments, points, (board - left).coerceIn(0L, board.toLong()).toInt(), null)
        }

        /** Segments and boarding points, or null for a trip with no time to show. */
        private fun bar(itinerary: Itinerary): Pair<List<Segment>, List<Int>>? {
            val legs = itinerary.legs
            if (legs.isEmpty()) return null
            val colors = LegPalette.colors(legs)
            val t0 = sched(legs.first(), start = true)
            val starts = legs.map { at(t0, sched(it, start = true)) }
            val end = at(t0, sched(legs.last(), start = false))
            val segments = mutableListOf<Segment>()
            val points = mutableListOf<Int>()
            for (i in legs.indices) {
                val from = starts[i]
                val to = (if (i + 1 < legs.size) starts[i + 1] else end).coerceAtLeast(from)
                if (legs[i].isTransit) points += from
                // A zero-length leg (a 0-min transfer walk) draws nothing; the bar must not have empty segments.
                if (to > from) segments += Segment(to - from, colors[i])
            }
            if (segments.isEmpty()) return null
            return segments to points.distinct()
        }

        /** Scheduled times, as [il.transit.core.ride.RideTracker] measures delays against them. */
        private fun sched(leg: Leg, start: Boolean): Instant =
            parseTime(if (start) leg.scheduledStartTime ?: leg.startTime else leg.scheduledEndTime ?: leg.endTime)

        private fun at(t0: Instant, t: Instant): Int = Duration.between(t0, t).seconds.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

        /** Whole minutes left, rounded up so "1 min" shows until arrival; never below 0. */
        private fun minutesUntil(now: Instant, t: Instant): Int =
            ((Duration.between(now, t).seconds + 59) / 60).coerceAtLeast(0L).toInt()
    }
}
