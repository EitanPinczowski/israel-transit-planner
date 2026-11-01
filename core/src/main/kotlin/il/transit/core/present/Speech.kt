package il.transit.core.present

import il.transit.core.api.Leg
import il.transit.core.features.ISRAEL
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * What a screen reader says for a leg, an option, a departure row and a tight change (Phase 11 E5).
 * Pure: it returns [Phrase]s with string keys and the app turns each into its language's string
 * resource (`strings_a11y.xml`, English and Hebrew), so the word order of each language lives there.
 * "Bus 47, 8:10 to 8:34, 12 stops, get off at Rager/Oren".
 *
 * Times are spoken as times: `H:mm` without the leading zero ("8:10", never "08:10", which some
 * voices read digit by digit). Line numbers and train numbers stay text ("5א", "24").
 */
enum class SpeechKey(
    /** The resource is a `<plurals>` chosen by [Phrase.count]. */
    val plural: Boolean = false,
) {
    /** "Bus %1$s" */
    BUS_LINE,

    /** "Light rail %1$s" */
    LIGHT_RAIL_LINE,

    /** "Line %1$s" (a ferry, a cable car: anything else with a line name). */
    OTHER_LINE,

    /** "Train %1$s to %2$s" (E3: number and terminus town). */
    TRAIN_NUMBER_TO,

    /** "Train %1$s" (number, no town in the data). */
    TRAIN_NUMBER,

    /** A kind with no line name: "Bus", "Train", "Light rail", "Public transport". */
    KIND_BUS,
    KIND_TRAIN,
    KIND_LIGHT_RAIL,
    KIND_OTHER,

    /** "Walk %d minutes" */
    WALK_MIN(plural = true),

    /** "Drive %d minutes" */
    DRIVE_MIN(plural = true),

    /** "%1$s to %2$s": two times. */
    TIMES,

    /** "%d stops" */
    STOPS(plural = true),

    /** "%d minutes" (a ride or an option without times). */
    MINUTES(plural = true),

    /** "get off at %1$s" */
    GET_OFF,

    /** "to %1$s" (a walk or drive's end). */
    TO_PLACE,

    /** E3's badge in words: "accessible bus" / "may not be wheelchair accessible". */
    BADGE_ACCESSIBLE,
    BADGE_MAY_NOT_BE,

    /** "leave %1$s" / "arrive %1$s" (an option's two ends). */
    LEAVE_AT,
    ARRIVE_AT,

    /** "%d transfers" / "no transfers". */
    TRANSFERS(plural = true),
    DIRECT,

    /** A departure row: "to %1$s" and "leaves %1$s", "%d minutes late", "cancelled". */
    HEADSIGN,
    LEAVES_AT,
    LATE_MIN(plural = true),
    CANCELLED,

    /** A tight change: "warning: change at %1$s, %2$d minutes" (the count is the slack). */
    TIGHT_CHANGE(plural = true),
}

/** One phrase: a key, its text arguments, and the number that picks a plural form. */
data class Phrase(val key: SpeechKey, val args: List<String> = emptyList(), val count: Int? = null)

/** The phrases of one node, read in order, joined by ", ". */
data class Spoken(val phrases: List<Phrase>) {
    fun render(words: (Phrase) -> String): String = phrases.joinToString(", ") { words(it) }
}

object Speech {
    private val SPOKEN_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

    /** "8:10": Israel time, no leading zero. Past midnight it is "0:20". */
    fun time(t: Instant): String = SPOKEN_TIME.format(t.atZone(ISRAEL))

    /** A leg of an itinerary, with E3's train number and badge. */
    fun leg(leg: Leg, badge: AccessibleBadge? = null): Spoken {
        val kind = legKind(leg.mode)
        val min = minutes((leg.end.epochSecond - leg.start.epochSecond).coerceAtLeast(0).toInt())
        if (kind == LegKind.WALK || kind == LegKind.CAR) {
            val head = Phrase(if (kind == LegKind.WALK) SpeechKey.WALK_MIN else SpeechKey.DRIVE_MIN, count = min)
            val end = leg.to.name
            val to = if (end.isBlank()) null else Phrase(SpeechKey.TO_PLACE, listOf(end))
            return Spoken(listOfNotNull(head, to))
        }
        val train = trainLabel(leg)
        val out = mutableListOf(name(kind, lineLabel(leg), train))
        badge?.let { out += Phrase(if (it == AccessibleBadge.ACCESSIBLE) SpeechKey.BADGE_ACCESSIBLE else SpeechKey.BADGE_MAY_NOT_BE) }
        out += Phrase(SpeechKey.TIMES, listOf(time(leg.start), time(leg.end)))
        val stops = leg.intermediateStops.size + 1
        out += Phrase(SpeechKey.STOPS, count = stops)
        if (leg.to.name.isNotBlank()) out += Phrase(SpeechKey.GET_OFF, listOf(leg.to.name))
        return Spoken(out)
    }

    /** A leg chip without its leg (the car features' cards): "Bus 370, 25 minutes". */
    fun chip(c: LegChip): Spoken = when (c.kind) {
        LegKind.WALK -> Spoken(listOf(Phrase(SpeechKey.WALK_MIN, count = c.minutes)))
        LegKind.CAR -> Spoken(listOf(Phrase(SpeechKey.DRIVE_MIN, count = c.minutes)))
        else -> Spoken(listOf(name(c.kind, c.label, null), Phrase(SpeechKey.MINUTES, count = c.minutes)))
    }

    /** An option's header: "leave 8:21, arrive 9:46, 85 minutes, 1 transfer". */
    fun option(start: Instant, end: Instant, durationMin: Int, transfers: Int): Spoken = Spoken(
        listOf(
            Phrase(SpeechKey.LEAVE_AT, listOf(time(start))),
            Phrase(SpeechKey.ARRIVE_AT, listOf(time(end))),
            Phrase(SpeechKey.MINUTES, count = durationMin),
            if (transfers == 0) Phrase(SpeechKey.DIRECT) else Phrase(SpeechKey.TRANSFERS, count = transfers),
        ),
    )

    /** A stop's departure row: "Bus 5, to Central station, leaves 8:28, 3 minutes late". */
    fun departure(r: DepartureRow): Spoken {
        val out = mutableListOf(name(r.kind, r.line.takeIf { it.isNotBlank() }, null))
        if (r.headsign.isNotBlank()) out += Phrase(SpeechKey.HEADSIGN, listOf(r.headsign))
        out += Phrase(SpeechKey.LEAVES_AT, listOf(r.instant?.let(::time) ?: r.time.removePrefix("0")))
        when {
            r.cancelled -> out += Phrase(SpeechKey.CANCELLED)
            (r.delayMin ?: 0) > 0 -> out += Phrase(SpeechKey.LATE_MIN, count = r.delayMin)
        }
        return Spoken(out)
    }

    /** A tight change of vehicle: "warning: change at Lehavim, 3 minutes". */
    fun tightChange(stop: String, slackMin: Int): Spoken = Spoken(listOf(Phrase(SpeechKey.TIGHT_CHANGE, listOf(stop), count = slackMin)))

    private fun name(kind: LegKind, line: String?, train: TrainLabel?): Phrase = when {
        train != null && train.to != null -> Phrase(SpeechKey.TRAIN_NUMBER_TO, listOf(train.number, train.to))
        train != null -> Phrase(SpeechKey.TRAIN_NUMBER, listOf(train.number))
        line != null && kind == LegKind.BUS -> Phrase(SpeechKey.BUS_LINE, listOf(line))
        line != null && kind == LegKind.LIGHT_RAIL -> Phrase(SpeechKey.LIGHT_RAIL_LINE, listOf(line))
        line != null && kind != LegKind.TRAIN -> Phrase(SpeechKey.OTHER_LINE, listOf(line))
        kind == LegKind.BUS -> Phrase(SpeechKey.KIND_BUS)
        kind == LegKind.TRAIN -> Phrase(SpeechKey.KIND_TRAIN)
        kind == LegKind.LIGHT_RAIL -> Phrase(SpeechKey.KIND_LIGHT_RAIL)
        else -> Phrase(SpeechKey.KIND_OTHER)
    }
}
