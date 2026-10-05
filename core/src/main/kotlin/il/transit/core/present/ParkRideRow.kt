package il.transit.core.present

import il.transit.core.api.Itinerary
import il.transit.core.fare.FareProfile
import il.transit.core.features.ParkRideOption
import il.transit.core.features.TrafficProfile
import java.time.Instant

/** One park & ride option, ready to display. */
data class ParkRideRow(
    val station: String,
    /** Traffic-adjusted drive to the station. */
    val driveMin: Int,
    /** Leave home. */
    val leave: String,
    /** The first vehicle from the station. */
    val board: String,
    val arrive: String,
    /** Minutes earlier than the no-car baseline (may be ≤ 0 when the gain is fewer transfers). */
    val savedMin: Int?,
    val transfersSaved: Int?,
    val transfers: Int,
    val summary: ItinerarySummary,
)

fun parkRideRow(o: ParkRideOption, baseline: Itinerary?, fareProfile: FareProfile = FareProfile.REGULAR): ParkRideRow {
    val it = o.itinerary
    return ParkRideRow(
        station = o.station.name,
        driveMin = minutes(o.driveSec),
        leave = hhmm(o.leaveAt),
        board = hhmm(it.legs.firstOrNull { l -> l.isTransit }?.start ?: it.start),
        arrive = hhmm(it.end),
        savedMin = baseline?.let { b -> minutes((b.end.epochSecond - it.end.epochSecond).toInt()) },
        transfersSaved = baseline?.let { b -> b.transfers - it.transfers },
        transfers = it.transfers,
        summary = summarize(it, fareProfile),
    )
}

/**
 * "Drive home from there ≈ 18 min": the outbound drive with the traffic factor of the time
 * you would get back to the car ([backAt], e.g. the way-back trip's arrival). No request.
 */
fun driveHomeMin(o: ParkRideOption, backAt: Instant, traffic: TrafficProfile = TrafficProfile()): Int =
    minutes(o.driveHomeSec(backAt, traffic))
