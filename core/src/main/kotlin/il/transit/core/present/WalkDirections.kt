package il.transit.core.present

import il.transit.core.api.Leg
import il.transit.core.geo.Geo
import il.transit.core.geo.LatLon
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt

/** A real-world turn. Left is left: never mirror these for RTL. */
enum class Turn { START, STRAIGHT, SLIGHT_LEFT, LEFT, SHARP_LEFT, SLIGHT_RIGHT, RIGHT, SHARP_RIGHT, U_TURN, STAIRS }

/** One line of walking directions: "→ Right · 145 m · Basel". [street] null = an unnamed path. */
data class WalkStep(val turn: Turn, val meters: Int, val street: String?)

/**
 * Turn-by-turn walking directions from a leg's `steps`. Transitous says only CONTINUE or
 * STAIRS and names the street on some steps, so:
 *  1. consecutive steps on the same street (and short unnamed bits between) become one;
 *  2. a short piece between two others (a roundabout, a crossing) folds into the next one;
 *  3. the turn between two pieces is the change of heading where they meet, from their polylines.
 * Empty when the leg has no steps (walk-only legs from old caches, or a server without them).
 */
fun walkSteps(leg: Leg): List<WalkStep> {
    data class Piece(var street: String?, var meters: Double, val stairs: Boolean, val points: MutableList<LatLon>)

    val pieces = mutableListOf<Piece>()
    for (s in leg.steps) {
        val pts = s.polyline?.let { Geo.decodePolyline(it.points, it.precision) }.orEmpty()
        val stairs = s.relativeDirection == "STAIRS"
        val name = s.streetName.ifBlank { null }
        val prev = pieces.lastOrNull()
        val joins = prev != null && prev.stairs == stairs && (
            stairs || prev.street == name ||
                (name == null && s.distance < SHORT_M) || (prev.street == null && prev.meters < SHORT_M)
            )
        if (prev != null && joins) {
            if (prev.street == null) prev.street = name
            prev.meters += s.distance
            prev.points += pts
        } else {
            pieces += Piece(name, s.distance, stairs, pts.toMutableList())
        }
    }
    // A 10-20 m roundabout or crossing between two streets is not a step of its own.
    var i = 1
    while (i < pieces.size - 1) {
        val p = pieces[i]
        if (!p.stairs && p.meters < SHORT_M && !pieces[i + 1].stairs) {
            pieces[i + 1].meters += p.meters
            pieces.removeAt(i)
        } else {
            i++
        }
    }
    return pieces.mapIndexed { i, p ->
        val turn = when {
            p.stairs -> Turn.STAIRS
            i == 0 -> Turn.START
            else -> turnBetween(pieces[i - 1].points, p.points)
        }
        WalkStep(turn, (p.meters / 10).roundToInt() * 10, p.street)
    }
}

/** Heading change where [before] ends and [after] starts; STRAIGHT when either is too short. */
internal fun turnBetween(before: List<LatLon>, after: List<LatLon>): Turn {
    val inH = heading(before.takeLast(2)) ?: return Turn.STRAIGHT
    val outH = heading(after.take(2).let { if (it.size == 2 && Geo.distanceM(it[0], it[1]) < 1) after.take(3).drop(1) else it })
        ?: return Turn.STRAIGHT
    var d = outH - inH
    while (d > 180) d -= 360
    while (d <= -180) d += 360
    val a = abs(d)
    return when {
        a < 25 -> Turn.STRAIGHT
        a > 160 -> Turn.U_TURN
        d > 0 -> if (a < 60) Turn.SLIGHT_RIGHT else if (a <= 130) Turn.RIGHT else Turn.SHARP_RIGHT
        else -> if (a < 60) Turn.SLIGHT_LEFT else if (a <= 130) Turn.LEFT else Turn.SHARP_LEFT
    }
}

/** Compass heading of the segment a→b in degrees (0 = north, 90 = east). */
private fun heading(seg: List<LatLon>): Double? {
    if (seg.size < 2) return null
    val (a, b) = seg
    val dx = (b.lon - a.lon) * cos(a.lat * PI / 180)
    val dy = b.lat - a.lat
    if (dx == 0.0 && dy == 0.0) return null
    return atan2(dx, dy) * 180 / PI
}

private const val SHORT_M = 30.0
