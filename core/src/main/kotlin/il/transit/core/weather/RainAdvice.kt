package il.transit.core.weather

import il.transit.core.api.Itinerary
import il.transit.core.api.StreetModes
import il.transit.core.plan.walkSec
import java.time.Instant

/**
 * "Rain likely around [at] ([probability]% / [mm] mm). Least walking: [leastWalkMin] min (the
 * [leastWalking] option)". [leastWalking] is null when there is only one option to choose from.
 */
data class RainNote(
    val at: Instant,
    val probability: Int?,
    val mm: Double?,
    val leastWalking: Itinerary?,
    val leastWalkMin: Int,
) {
    /** The chance is what made it rainy (else the amount did): the note shows that number. */
    val byProbability: Boolean get() = (probability ?: 0) >= RainAdvice.MIN_PROBABILITY
}

/**
 * Rain on the walking parts (Phase 10 D9, owner decision 7): an hour is rainy at a chance of
 * [MIN_PROBABILITY]% or [MIN_MM] mm, and it counts when it overlaps at least [MIN_WALK_SEC] of
 * walking in at least one option. Pure: the plan request is never changed, the note only
 * offers the Least walking sort.
 */
object RainAdvice {
    const val MIN_PROBABILITY = 60
    const val MIN_MM = 0.5
    const val MIN_WALK_SEC = 5 * 60

    fun rainy(h: RainHour): Boolean = (h.probability ?: 0) >= MIN_PROBABILITY || (h.mm ?: 0.0) >= MIN_MM

    /** Seconds of walking in [option] between [from] and [to]. */
    fun walkingIn(option: Itinerary, from: Instant, to: Instant): Long =
        option.legs.filter { it.mode == StreetModes.WALK }.sumOf { leg ->
            val s = maxOf(leg.start, from)
            val e = minOf(leg.end, to)
            if (e.isAfter(s)) e.epochSecond - s.epochSecond else 0L
        }

    /** The note for [options] under [forecast], or null when no option walks in the rain. */
    fun advise(options: List<Itinerary>, forecast: RainForecast?): RainNote? {
        if (forecast == null || options.isEmpty()) return null
        val wet = forecast.hours.filter(::rainy)
        // For each option, the first rainy hour it walks ≥ 5 min in, and when that walk begins.
        val hits = options.mapNotNull { o ->
            val h = wet.firstOrNull { walkingIn(o, it.start, it.end) >= MIN_WALK_SEC } ?: return@mapNotNull null
            val walkStart = o.legs.filter { it.mode == StreetModes.WALK && it.end.isAfter(h.start) && it.start.isBefore(h.end) }
                .minOf { maxOf(it.start, h.start) }
            walkStart to h
        }
        val (at, hour) = hits.minByOrNull { it.first } ?: return null
        val least = options.minWithOrNull(compareBy<Itinerary> { it.walkSec }.thenBy { it.start })!!
        return RainNote(
            at = at,
            probability = hour.probability,
            mm = hour.mm,
            leastWalking = least.takeIf { options.size > 1 },
            leastWalkMin = (least.walkSec + 30) / 60,
        )
    }
}
