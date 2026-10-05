package il.transit.core.present

import il.transit.core.api.Itinerary
import il.transit.core.api.Leg
import il.transit.core.api.StreetModes
import il.transit.core.fare.FareEstimate
import il.transit.core.fare.FareEstimator
import il.transit.core.fare.FareProfile
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
fun legColor(leg: Leg): String = routeColorOr(leg.routeColor, legKind(leg.mode))

private fun routeColorOr(raw: String?, kind: LegKind): String {
    val c = raw?.trim()?.removePrefix("#")
    return if (c != null && c.length == 6 && c.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) "#${c.uppercase()}"
    else defaultColor(kind)
}

/**
 * Text colour for a label drawn on [background] ("#RRGGBB"): white while it reaches the
 * WCAG 3:1 minimum for UI labels (the Material convention on mid blues and greens),
 * otherwise whichever of black/white contrasts more. Route colours come from the operator
 * and can be anything (yellow light rail, white night lines).
 */
fun onColor(background: String): String {
    val c = background.trim().removePrefix("#")
    if (c.length != 6) return WHITE
    val rgb = c.toLongOrNull(16) ?: return WHITE
    fun channel(shift: Int): Double {
        val v = ((rgb shr shift) and 0xFF) / 255.0
        return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }
    val l = 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    val onWhite = 1.05 / (l + 0.05)
    val onBlack = (l + 0.05) / 0.05
    return if (onWhite >= MIN_CONTRAST || onWhite >= onBlack) WHITE else BLACK
}

private const val MIN_CONTRAST = 3.0

private const val WHITE = "#FFFFFF"
private const val BLACK = "#000000"

data class LegChip(
    val kind: LegKind,
    /** Line number / name for transit legs, null for street legs. */
    val label: String?,
    val minutes: Int,
    val realTime: Boolean,
    val color: String,
    /** Minutes late (negative = early) from real-time data; null without it. */
    val delayMin: Int? = null,
    /** Index of the leg in the itinerary (chips skip short walks), to open its trip sheet. */
    val legIndex: Int = -1,
    /** The leg carries a service alert in effect at its departure: ⚠ on the chip. */
    val alert: Boolean = false,
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
    /** Any transit leg carries real-time data. */
    val hasRealTime: Boolean = false,
    /** Estimated fare for the chosen [FareProfile]; null for a walk-only trip. */
    val fare: FareEstimate? = null,
    /** Set when the trip leaves on another day than the one searched for (Friday → Saturday night). */
    val departDay: java.time.DayOfWeek? = null,
    /** Arrival is this many days after departure; 0 for a same-day trip. */
    val arriveDaysLater: Int = 0,
)

/** "≈ ₪8", "≈ ₪14.5", "≈ ₪87.32" — always an estimate, so always "≈". */
fun fareLabel(f: FareEstimate): String {
    val shekels = f.agorot / 100
    val agorot = f.agorot % 100
    val amount = if (agorot == 0) "$shekels" else String.format(java.util.Locale.US, "%d.%02d", shekels, agorot).trimEnd('0')
    return "≈ ₪$amount"
}

data class Boarding(
    val line: String?,
    val kind: LegKind,
    val time: String,
    val stop: String,
    val realTime: Boolean,
    val delayMin: Int? = null,
)

/** Real-time delay of a leg's departure, or null when the leg has no real-time data. */
fun legDelayMin(leg: Leg): Int? = if (leg.realTime) delayMin(leg.startTime, leg.scheduledStartTime) else null

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** 24-hour local Israel time, whatever the phone's own zone is. */
fun hhmm(t: Instant): String = HHMM.format(t.atZone(ISRAEL))

/** Israel calendar days from [a] to [b]. */
fun daysBetween(a: Instant, b: Instant): Int =
    java.time.temporal.ChronoUnit.DAYS.between(a.atZone(ISRAEL).toLocalDate(), b.atZone(ISRAEL).toLocalDate()).toInt()

fun minutes(sec: Int): Int = Math.round(sec / 60.0).toInt()

fun lineLabel(leg: Leg): String? = lineLabel(leg.routeShortName, leg.displayName)

/**
 * Short line name, or null. Israel Railways routes have no short name and a display name
 * like "באר שבע מרכז-באר שבע<->כרמיאל-כרמיאל" — too long for a chip; the train icon says enough.
 */
fun lineLabel(shortName: String?, displayName: String?): String? =
    shortName?.takeIf { it.isNotBlank() } ?: displayName?.takeIf { it.isNotBlank() && "<->" !in it }

/** Where the vehicle goes. Israel Railways sends the train number ("406") as the headsign. */
fun headsignText(headsign: String?, tripTo: il.transit.core.api.Place?): String {
    val h = headsign.orEmpty()
    return if ((h.isBlank() || h.all { it.isDigit() }) && !tripTo?.name.isNullOrBlank()) tripTo!!.name else h
}

/**
 * [searchedAt] is the time the user searched for (or now); a trip leaving on another day is
 * marked with [ItinerarySummary.departDay]. Days are Israel calendar days.
 */
fun summarize(
    it: Itinerary,
    fareProfile: FareProfile = FareProfile.REGULAR,
    searchedAt: Instant? = null,
): ItinerarySummary {
    val chips = it.legs.withIndex()
        // Transfers inside a station show up as walks of a few seconds; they are noise.
        .filter { (_, leg) -> leg.isTransit || leg.duration >= 60 }
        .map { (i, leg) ->
            LegChip(
                kind = legKind(leg.mode),
                label = if (leg.isTransit) lineLabel(leg) else null,
                minutes = minutes(leg.duration),
                realTime = leg.realTime,
                color = legColor(leg),
                delayMin = if (leg.isTransit) legDelayMin(leg) else null,
                legIndex = i,
                alert = leg.isTransit && legAlerts(leg, leg.start).isNotEmpty(),
            )
        }
    val board = it.firstTransitLeg?.let { l ->
        Boarding(lineLabel(l), legKind(l.mode), hhmm(l.start), l.from.name, l.realTime, legDelayMin(l))
    }
    return ItinerarySummary(
        depart = hhmm(it.start),
        arrive = hhmm(it.end),
        durationMin = minutes(it.duration),
        transfers = it.transfers,
        walkMin = minutes(it.legs.filter { l -> l.mode == StreetModes.WALK }.sumOf { l -> l.duration }),
        chips = chips,
        firstBoarding = board,
        hasRealTime = it.legs.any { l -> l.isTransit && l.realTime },
        fare = FareEstimator.estimate(it, fareProfile),
        departDay = searchedAt?.let { t -> it.start.atZone(ISRAEL).dayOfWeek.takeIf { _ -> daysBetween(t, it.start) != 0 } },
        arriveDaysLater = daysBetween(it.start, it.end),
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
    /** Service alerts on this departure or its stop, in effect when it leaves. Usually none. */
    val alerts: List<AlertText> = emptyList(),
    /** To open the trip sheet from the board; null when the answer had none. */
    val tripId: String? = null,
    /** The line's colour ("#RRGGBB"), the kind's default when the operator sends none. */
    val color: String = defaultColor(kind),
)

fun departureRow(st: il.transit.core.api.StopTime): DepartureRow {
    val actual = st.place.departure ?: st.place.arrival
    val scheduled = st.place.scheduledDeparture ?: st.place.scheduledArrival
    val at = (actual ?: scheduled)?.let { runCatching { il.transit.core.api.parseTime(it) }.getOrNull() }
    return DepartureRow(
        line = lineLabel(st.routeShortName, st.displayName).orEmpty(),
        headsign = headsignText(st.headsign, st.tripTo),
        kind = legKind(st.mode),
        time = at?.let(::hhmm) ?: "",
        delayMin = if (st.realTime) delayMin(actual, scheduled) else null,
        cancelled = st.cancelled || st.tripCancelled,
        instant = at,
        alerts = alertTexts(st.alerts + st.place.alerts, at ?: Instant.EPOCH),
        tripId = st.tripId,
        color = routeColorOr(st.routeColor, legKind(st.mode)),
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
    val summary: ItinerarySummary,
)

fun betterStartRow(
    o: il.transit.core.features.BetterStartOption,
    baseline: Itinerary?,
    fareProfile: FareProfile = FareProfile.REGULAR,
): BetterStartRow {
    val it = o.itinerary
    return BetterStartRow(
        driveMin = minutes(o.driveSec),
        stop = o.dropOffStopName,
        depart = hhmm(o.leaveAt),
        arrive = hhmm(it.end),
        savedMin = baseline?.let { b -> minutes((b.end.epochSecond - it.end.epochSecond).toInt()) },
        transfersSaved = baseline?.let { b -> b.transfers - it.transfers },
        summary = summarize(it, fareProfile),
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

fun dropOffRow(o: il.transit.core.features.DropOffOption, fareProfile: FareProfile = FareProfile.REGULAR): DropOffRow = DropOffRow(
    kind = o.kind,
    stop = o.stop?.name,
    stopAt = o.stop?.latLon,
    detourMin = minutes(o.detourSec),
    rideMin = minutes(o.rideSec),
    depart = hhmm(o.transit.start),
    arrive = hhmm(o.transit.end),
    transfers = o.transit.transfers,
    summary = summarize(o.transit, fareProfile),
)

/** One "best pick-up point" option, ready to display. Savings are null without a baseline. */
data class PickUpRow(
    val stop: String,
    val stopAt: il.transit.core.geo.LatLon,
    /** When you reach the stop = when the driver must be there. */
    val pickUpTime: String,
    val driverLeaves: String,
    /** Driver's round trip home → stop → home. */
    val roundTripMin: Int,
    val arriveHome: String,
    val savedMin: Int?,
    val transfersSaved: Int?,
    val summary: ItinerarySummary,
)

fun pickUpRow(
    o: il.transit.core.features.Option<il.transit.core.features.PickUpOption>,
    baseline: Itinerary?,
    fareProfile: FareProfile = FareProfile.REGULAR,
): PickUpRow {
    val p = o.payload
    return PickUpRow(
        stop = p.pickUpStopName,
        stopAt = p.pickUpAt,
        pickUpTime = hhmm(p.pickUpTime),
        driverLeaves = hhmm(p.driverLeavesAt),
        roundTripMin = minutes(o.driverCostSec),
        arriveHome = hhmm(o.arrival),
        savedMin = baseline?.let { b -> minutes((b.end.epochSecond - o.arrival.epochSecond).toInt()) },
        transfersSaved = baseline?.let { b -> b.transfers - o.transfers },
        summary = summarize(p.itinerary, fareProfile),
    )
}

/**
 * True when a short one-word search (2–4 characters) got answers but none of them contains
 * what was typed. Transitous matches very short words loosely ("רגר" finds Hagar, not Rager
 * Boulevard, which is not even in the top 30), so re-ranking cannot help; the UI suggests
 * typing the full name instead.
 */
fun needsFullNameHint(query: String, names: List<String>): Boolean {
    val q = normalizeForMatch(query.trim())
    if (q.length !in 2..4 || q.any { it.isWhitespace() } || names.isEmpty()) return false
    return names.none { normalizeForMatch(it).contains(q) }
}

/** Lower case without quote marks, so "צה\"ל", "צה״ל" and "צהל" compare equal. */
private fun normalizeForMatch(s: String): String = s.lowercase().filterNot { it in "\"'״׳`" }

/** What the trip list says about the evening's last trip. */
data class LastRideNote(
    /** HH:mm of the last trip. */
    val lastTime: String,
    /** The selected option is that last trip. */
    val selectedIsLast: Boolean,
    /** Service stops for hours after it (Shabbat, a holiday): when it resumes, if known. */
    val longGap: Boolean,
    val resumesDay: java.time.DayOfWeek?,
    val resumesTime: String?,
)

/**
 * The note for [lr], or null when there is nothing worth saying: the selected trip is not
 * the last, the last is more than [SOON] away, and service does not stop for long after it.
 * [always] skips that filter (the "last trip back" button, which the user asked for).
 */
fun lastRideNote(lr: il.transit.core.plan.LastRide, selected: Itinerary?, always: Boolean = false): LastRideNote? {
    val last = lr.last ?: return null
    if (lr.runsAllNight) return null
    val sel = selected?.start
    val selectedIsLast = sel != null && Duration.between(sel, last.start).abs() < Duration.ofMinutes(1)
    val soon = sel != null && !sel.isAfter(last.start) && Duration.between(sel, last.start) <= SOON
    if (!always && !selectedIsLast && !soon && !lr.longGap) return null
    val next = lr.next?.takeIf { lr.longGap }
    val nextDay = next?.start?.atZone(ISRAEL)?.toLocalDate()
    return LastRideNote(
        lastTime = hhmm(last.start),
        selectedIsLast = selectedIsLast,
        longGap = lr.longGap,
        resumesDay = nextDay?.takeIf { it != last.start.atZone(ISRAEL).toLocalDate() }?.dayOfWeek,
        resumesTime = next?.let { hhmm(it.start) },
    )
}

private val SOON: Duration = Duration.ofHours(3)
