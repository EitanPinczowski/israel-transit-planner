package il.transit.core.present

import il.transit.core.api.Itinerary
import il.transit.core.api.Leg
import il.transit.core.api.StreetModes
import il.transit.core.features.ISRAEL
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Pure presentation helpers: everything the UI needs to draw an itinerary, computed
 * without Android so it can be unit-tested. The UI layer only picks strings and colours.
 */
enum class LegKind { WALK, BUS, TRAIN, LIGHT_RAIL, CAR, OTHER }

fun legKind(mode: String): LegKind = when (mode) {
    StreetModes.WALK -> LegKind.WALK
    in StreetModes.CAR_LIKE -> LegKind.CAR
    "BUS", "COACH" -> LegKind.BUS
    "TRAM", "SUBWAY", "METRO" -> LegKind.LIGHT_RAIL
    "RAIL", "HIGHSPEED_RAIL", "LONG_DISTANCE", "NIGHT_RAIL", "REGIONAL_RAIL", "REGIONAL_FAST_RAIL", "SUBURBAN" -> LegKind.TRAIN
    else -> LegKind.OTHER
}

/** Fallback colours per kind (#RRGGBB), used when MOTIS sends no route colour. */
fun defaultColor(kind: LegKind): String = when (kind) {
    LegKind.WALK -> "#7A7A7A"
    LegKind.BUS -> "#1E88E5"
    LegKind.TRAIN -> "#2E7D32"
    LegKind.LIGHT_RAIL -> "#C62828"
    LegKind.CAR -> "#6A1B9A"
    LegKind.OTHER -> "#455A64"
}

/** MOTIS route colours come as "RRGGBB" without '#'; anything malformed falls back. */
fun legColor(leg: Leg): String {
    val c = leg.routeColor?.trim()?.removePrefix("#")
    return if (c != null && c.length == 6 && c.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) "#${c.uppercase()}"
    else defaultColor(legKind(leg.mode))
}

data class LegChip(
    val kind: LegKind,
    /** Line number / name for transit legs, null for street legs. */
    val label: String?,
    val minutes: Int,
    val realTime: Boolean,
    val color: String,
)

data class ItinerarySummary(
    val depart: String,
    val arrive: String,
    val durationMin: Int,
    val transfers: Int,
    val walkMin: Int,
    val chips: List<LegChip>,
    /** "line 5 at 12:14 from <stop>" — the first thing the user must not miss. */
    val firstBoarding: Boarding?,
)

data class Boarding(val line: String?, val kind: LegKind, val time: String, val stop: String, val realTime: Boolean)

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** 24-hour local Israel time, whatever the phone's own zone is. */
fun hhmm(t: Instant): String = HHMM.format(t.atZone(ISRAEL))

fun minutes(sec: Int): Int = Math.round(sec / 60.0).toInt()

fun lineLabel(leg: Leg): String? =
    leg.routeShortName?.takeIf { it.isNotBlank() } ?: leg.displayName?.takeIf { it.isNotBlank() }

fun summarize(it: Itinerary): ItinerarySummary {
    val chips = it.legs
        // Transfers inside a station show up as walks of a few seconds; they are noise.
        .filter { leg -> leg.isTransit || leg.duration >= 60 }
        .map { leg ->
            LegChip(
                kind = legKind(leg.mode),
                label = if (leg.isTransit) lineLabel(leg) else null,
                minutes = minutes(leg.duration),
                realTime = leg.realTime,
                color = legColor(leg),
            )
        }
    val board = it.firstTransitLeg?.let { l ->
        Boarding(lineLabel(l), legKind(l.mode), hhmm(l.start), l.from.name, l.realTime)
    }
    return ItinerarySummary(
        depart = hhmm(it.start),
        arrive = hhmm(it.end),
        durationMin = minutes(it.duration),
        transfers = it.transfers,
        walkMin = minutes(it.legs.filter { l -> l.mode == StreetModes.WALK }.sumOf { l -> l.duration }),
        chips = chips,
        firstBoarding = board,
    )
}

/** Whole minutes from [now] until [t], never negative. For "leaves in 7 min". */
fun minutesUntil(t: Instant, now: Instant): Int =
    Duration.between(now, t).toMinutes().toInt().coerceAtLeast(0)

/** Delay of a real-time time against its schedule, in whole minutes (negative = early). */
fun delayMin(actual: String?, scheduled: String?): Int? {
    if (actual == null || scheduled == null) return null
    return runCatching {
        Duration.between(il.transit.core.api.parseTime(scheduled), il.transit.core.api.parseTime(actual)).toMinutes().toInt()
    }.getOrNull()
}

/** One row of a stop's departure board. */
data class DepartureRow(
    val line: String,
    val headsign: String,
    val kind: LegKind,
    val time: String,
    /** Minutes late (negative = early); null when there is no real-time data. */
    val delayMin: Int?,
    val cancelled: Boolean,
    val instant: Instant?,
)

fun departureRow(st: il.transit.core.api.StopTime): DepartureRow {
    val actual = st.place.departure ?: st.place.arrival
    val scheduled = st.place.scheduledDeparture ?: st.place.scheduledArrival
    val at = (actual ?: scheduled)?.let { runCatching { il.transit.core.api.parseTime(it) }.getOrNull() }
    return DepartureRow(
        line = st.routeShortName.ifBlank { st.displayName.orEmpty() },
        headsign = st.headsign,
        kind = legKind(st.mode),
        time = at?.let(::hhmm) ?: "",
        delayMin = if (st.realTime) delayMin(actual, scheduled) else null,
        cancelled = st.cancelled || st.tripCancelled,
        instant = at,
    )
}

/** One "better start" option, ready to display. Savings are null without a baseline. */
data class BetterStartRow(
    val driveMin: Int,
    val stop: String,
    val depart: String,
    val arrive: String,
    /** Minutes earlier than the no-ride baseline (may be ≤ 0 when the gain is fewer transfers). */
    val savedMin: Int?,
    val transfersSaved: Int?,
    val tight: Boolean,
    val summary: ItinerarySummary,
)

fun betterStartRow(o: il.transit.core.features.BetterStartOption, baseline: Itinerary?): BetterStartRow {
    val it = o.itinerary
    return BetterStartRow(
        driveMin = minutes(o.driveSec),
        stop = o.dropOffStopName,
        depart = hhmm(it.start),
        arrive = hhmm(it.end),
        savedMin = baseline?.let { b -> minutes((b.end.epochSecond - it.end.epochSecond).toInt()) },
        transfersSaved = baseline?.let { b -> b.transfers - it.transfers },
        tight = o.tight,
        summary = summarize(it),
    )
}

/** One "let me off on the way" option, ready to display. */
data class DropOffRow(
    val kind: il.transit.core.features.DropOffKind,
    /** Where to get out; null for the two baselines. */
    val stop: String?,
    val stopAt: il.transit.core.geo.LatLon?,
    /** Extra minutes for the driver (0 for the baselines). */
    val detourMin: Int,
    /** Minutes in the car before getting out. */
    val rideMin: Int,
    /** When the transit part leaves / you reach C. */
    val depart: String,
    val arrive: String,
    val transfers: Int,
    val summary: ItinerarySummary,
)

fun dropOffRow(o: il.transit.core.features.DropOffOption): DropOffRow = DropOffRow(
    kind = o.kind,
    stop = o.stop?.name,
    stopAt = o.stop?.latLon,
    detourMin = minutes(o.detourSec),
    rideMin = minutes(o.rideSec),
    depart = hhmm(o.transit.start),
    arrive = hhmm(o.transit.end),
    transfers = o.transit.transfers,
    summary = summarize(o.transit),
)
