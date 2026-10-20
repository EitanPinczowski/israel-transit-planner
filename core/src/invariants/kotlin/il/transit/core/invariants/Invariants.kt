package il.transit.core.invariants

import il.transit.core.api.Itinerary
import il.transit.core.api.Place
import il.transit.core.api.parseTime
import il.transit.core.fare.FareEstimator
import il.transit.core.fare.FareProfile
import il.transit.core.features.Option
import il.transit.core.geo.Geo
import il.transit.core.geo.LatLon
import il.transit.core.plan.TripPlanner
import il.transit.core.present.RideDetails
import il.transit.core.present.minutes
import il.transit.core.present.rideDetails
import java.time.Instant

/**
 * Rules that hold for ANY routing answer (Phase 10 D4, moved here by Phase 11 T5 so the unit tests on
 * recordings and the live golden trips share them). Each function returns what it found, empty = holds;
 * `where` names the answer in the message.
 */
object Invariants {
    private fun times(p: Place): List<Instant> = listOfNotNull(p.arrival, p.departure).map(::parseTime)

    /** Times in order, ride details that add up, fares ≥ 0: everything that needs no map. */
    fun itinerary(where: String, it: Itinerary): List<String> {
        val bad = mutableListOf<String>()
        fun check(ok: Boolean, msg: () -> String) { if (!ok) bad += "$where: ${msg()}" }
        check(!it.end.isBefore(it.start)) { "arrival before departure" }
        check(it.duration >= 0) { "negative duration" }
        check(it.transfers >= 0) { "transfers < 0" }
        check(it.legs.isNotEmpty()) { "no legs" }
        it.legs.forEachIndexed { i, leg ->
            val at = "leg $i (${leg.mode})"
            check(!leg.end.isBefore(leg.start)) { "$at ends before it starts" }
            check(leg.duration >= 0) { "$at: negative duration" }
            (listOf(leg.from) + leg.intermediateStops + leg.to).forEach { s ->
                val arr = s.arrival
                val dep = s.departure
                if (arr != null && dep != null && s !== leg.from && s !== leg.to) {
                    check(!parseTime(dep).isBefore(parseTime(arr))) { "$at: stop '${s.name}' departs before it arrives" }
                }
            }
            val sequence = listOfNotNull(leg.from.departure?.let(::parseTime)) +
                leg.intermediateStops.flatMap(::times) + listOfNotNull(leg.to.arrival?.let(::parseTime))
            sequence.zipWithNext().forEachIndexed { k, (a, b) -> check(!a.isAfter(b)) { "$at: stop times go back in time at #$k ($a > $b)" } }
            if (i > 0) check(!leg.start.isBefore(it.legs[i - 1].end)) { "leg $i starts before leg ${i - 1} ends" }
        }
        if (it.legs.isNotEmpty()) {
            check(!it.start.isAfter(it.legs.first().start)) { "itinerary starts after its first leg" }
            check(!it.end.isBefore(it.legs.last().end)) { "itinerary ends before its last leg" }
            bad += rideDetailsAddUp(where, it)
            bad += fares(where, it)
        }
        return bad
    }

    /** Ride details add up to the legs: stops, minutes, count, and what the card shows of them. */
    fun rideDetailsAddUp(where: String, it: Itinerary): List<String> {
        val bad = mutableListOf<String>()
        fun check(ok: Boolean, msg: () -> String) { if (!ok) bad += "$where: ${msg()}" }
        val d = rideDetails(it)
        val transit = it.legs.filter { l -> l.isTransit }
        check(transit.size == d.legs.size) { "ride legs ${d.legs.size} ≠ ${transit.size}" }
        d.legs.forEach { r ->
            val leg = it.legs[r.legIndex]
            check(leg.isTransit) { "ride leg points at a walk" }
            check(r.stops == leg.intermediateStops.size + 1) { "stops of leg ${r.legIndex}: ${r.stops}" }
            check(r.minutes == minutes((leg.end.epochSecond - leg.start.epochSecond).toInt())) { "minutes of leg ${r.legIndex}: ${r.minutes}" }
            check(r.getOff == leg.to.name) { "get-off of leg ${r.legIndex}: ${r.getOff}" }
            check(r.minutes >= 0 && r.stops >= 1) { "ride minutes < 0" }
        }
        check(d.legs.size == d.shown.size + d.more) { "shown + more ≠ legs" }
        check(d.shown.size <= RideDetails.MAX_LINES) { "too many lines" }
        val total = d.total
        if (transit.isEmpty()) {
            check(total == null) { "total without a ride" }
        } else if (total != null) {
            check(total.stops == d.legs.sumOf { r -> r.stops }) { "total stops ${total.stops}" }
            check(total.minutes == d.legs.sumOf { r -> r.minutes }) { "total minutes ${total.minutes}" }
            check(total.getOff == transit.last().to.name) { "total get-off ${total.getOff}" }
            check(total.minutes <= minutes(it.duration) + d.legs.size) { "on board longer than the trip" }
        } else {
            bad += "$where: no total for a trip with rides"
        }
        return bad
    }

    fun fares(where: String, it: Itinerary): List<String> {
        val regular = FareEstimator.estimate(it)
        val half = FareEstimator.estimate(it, FareProfile.HALF)
        val free = FareEstimator.estimate(it, FareProfile.FREE)
        if (it.legs.none { l -> l.isTransit }) return if (regular == null) emptyList() else listOf("$where: fare without a ride")
        val bad = mutableListOf<String>()
        if (regular == null || regular.agorot < 0) {
            bad += "$where: fare < 0 (${regular?.agorot})"
        } else if (half == null || half.agorot !in 0..regular.agorot) {
            bad += "$where: half fare dearer than full"
        }
        if (free?.agorot != 0) bad += "$where: free fare ${free?.agorot}"
        return bad
    }

    /** Legs chain in space (a leg starts within [maxGapM] of where the last one ended) and, with endpoints, begin and end near them. */
    fun space(where: String, it: Itinerary, from: LatLon?, to: LatLon?, maxGapM: Double = 400.0, endM: Double = 1500.0): List<String> {
        val bad = mutableListOf<String>()
        it.legs.zipWithNext().forEachIndexed { i, (a, b) ->
            val gap = Geo.distanceM(LatLon(a.to.lat, a.to.lon), LatLon(b.from.lat, b.from.lon))
            if (gap > maxGapM) bad += "$where: legs ${i + 1}→${i + 2} jump %.0f m".format(gap)
        }
        if (from != null && it.legs.isNotEmpty()) {
            val d = Geo.distanceM(
                from,
                LatLon(
                    it.legs
                        .first()
                        .from.lat,
                    it.legs
                        .first()
                        .from.lon,
                ),
            )
            if (d > endM) bad += "$where: starts %.0f m from the origin".format(d)
        }
        if (to != null && it.legs.isNotEmpty()) {
            val d = Geo.distanceM(
                to,
                LatLon(
                    it.legs
                        .last()
                        .to.lat,
                    it.legs
                        .last()
                        .to.lon,
                ),
            )
            if (d > endM) bad += "$where: ends %.0f m from the destination".format(d)
        }
        return bad
    }

    /** Legs chain in time with [slackSec] of overlap allowed (live answers round to the minute). */
    fun timeChain(where: String, it: Itinerary, slackSec: Long = 60): List<String> =
        it.legs.zipWithNext().mapIndexedNotNull { i, (a, b) ->
            if (b.start.isBefore(a.end.minusSeconds(slackSec))) "$where: leg ${i + 2} starts before leg ${i + 1} ends" else null
        }

    /** A Pareto front: no member dominated, costs ≥ 0, sorted by arrival. */
    fun <T> front(where: String, front: List<Option<T>>): List<String> {
        val bad = mutableListOf<String>()
        fun dominates(a: Option<T>, b: Option<T>) =
            a.driverCostSec <= b.driverCostSec &&
                !a.arrival.isAfter(b.arrival) &&
                a.transfers <= b.transfers &&
                (a.driverCostSec < b.driverCostSec || a.arrival.isBefore(b.arrival) || a.transfers < b.transfers)
        front.forEach { o ->
            if (o.driverCostSec < 0) bad += "$where: driver cost ${o.driverCostSec} < 0"
            if (o.transfers < 0) bad += "$where: transfers < 0"
        }
        for (a in front) {
            for (b in front) {
                if (a !== b && dominates(a, b)) bad += "$where: ${b.driverCostSec}s/${b.arrival}/${b.transfers} is dominated"
            }
        }
        if (front.map { it.arrival } != front.sortedBy { it.arrival }.map { it.arrival }) bad += "$where: not sorted by arrival"
        return bad
    }

    /**
     * No unreasonable chains (D12), judged with the planner's own constants and the 10-minute window on
     * BOTH ends: X is unreasonable when some Y has [TripPlanner.CHAIN_FEWER_CHANGES]+ fewer changes (or, for
     * X with [TripPlanner.LONG_CHAIN_CHANGES]+ changes, at most [TripPlanner.SHORT_CHAIN_CHANGES]), arrives at
     * most [TripPlanner.CHAIN_WINDOW_SEC] after X and leaves at most that much before X. A long chain beside
     * a short one that is hours away is fine.
     */
    fun chains(where: String, options: List<Itinerary>): List<String> {
        val window = TripPlanner.CHAIN_WINDOW_SEC.toLong()
        fun departure(i: Itinerary) = i.firstTransitLeg?.start ?: i.start
        fun alternative(y: Itinerary, x: Itinerary) =
            !y.end.isAfter(x.end.plusSeconds(window)) && !departure(y).isBefore(departure(x).minusSeconds(window))
        val bad = mutableListOf<String>()
        options.forEachIndexed { xi, x ->
            options.forEachIndexed { yi, y ->
                if (xi == yi || !alternative(y, x)) return@forEachIndexed
                if (y.transfers <= x.transfers - TripPlanner.CHAIN_FEWER_CHANGES) {
                    bad += "$where: option ${xi + 1} (${x.transfers} changes) kept beside option ${yi + 1} (${y.transfers}) within ${window / 60}′"
                } else if (x.transfers >= TripPlanner.LONG_CHAIN_CHANGES && y.transfers <= TripPlanner.SHORT_CHAIN_CHANGES) {
                    bad += "$where: option ${xi + 1} (${x.transfers} changes) kept beside option ${yi + 1} (${y.transfers}) within ${window / 60}′"
                }
            }
        }
        return bad.distinct()
    }
}
