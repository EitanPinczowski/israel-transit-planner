package il.transit.core.geo

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double) {
    /** MOTIS `fromPlace`/`toPlace`/`place` format. */
    fun comma(): String = "$lat,$lon"

    /** MOTIS one-to-many `one`/`many` format. */
    fun semicolon(): String = "$lat;$lon"
}

data class BBox(val min: LatLon, val max: LatLon)

object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun distanceM(a: LatLon, b: LatLon): Double {
        val dLat = rad(b.lat - a.lat)
        val dLon = rad(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) + cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    /**
     * Decodes a Google-style encoded polyline. MOTIS sends `precision` with every
     * polyline (usually 6, not Google's 5), so it is a parameter, never assumed.
     */
    fun decodePolyline(encoded: String, precision: Int): List<LatLon> {
        val factor = 10.0.pow(precision)
        val out = ArrayList<LatLon>()
        var index = 0
        var lat = 0L
        var lon = 0L
        while (index < encoded.length) {
            val (dLat, next1) = decodeValue(encoded, index)
            val (dLon, next2) = decodeValue(encoded, next1)
            index = next2
            lat += dLat
            lon += dLon
            out += LatLon(lat / factor, lon / factor)
        }
        return out
    }

    private fun decodeValue(s: String, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var i = start
        while (true) {
            val b = s[i++].code - 63
            result = result or ((b and 0x1f).toLong() shl shift)
            shift += 5
            if (b < 0x20) break
        }
        val value = if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        return value to i
    }

    /** Shortest distance from [p] to any segment of [line], in metres. */
    fun distanceToLineM(p: LatLon, line: List<LatLon>): Double {
        if (line.isEmpty()) return Double.POSITIVE_INFINITY
        if (line.size == 1) return distanceM(p, line[0])
        // Local equirectangular projection around p: accurate to <1% at corridor scale.
        val mPerDegLat = PI * EARTH_RADIUS_M / 180
        val mPerDegLon = mPerDegLat * cos(rad(p.lat))
        fun xy(q: LatLon) = Pair((q.lon - p.lon) * mPerDegLon, (q.lat - p.lat) * mPerDegLat)
        var best = Double.POSITIVE_INFINITY
        for (i in 0 until line.size - 1) {
            val (ax, ay) = xy(line[i])
            val (bx, by) = xy(line[i + 1])
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else ((-ax * dx - ay * dy) / len2).coerceIn(0.0, 1.0)
            best = minOf(best, hypot(ax + t * dx, ay + t * dy))
        }
        return best
    }

    /** Bounding box of [points], grown by [padM] metres on every side. */
    fun bbox(points: List<LatLon>, padM: Double): BBox {
        require(points.isNotEmpty()) { "bbox of no points" }
        val padLat = padM / (PI * EARTH_RADIUS_M / 180)
        val midLat = points.sumOf { it.lat } / points.size
        val padLon = padLat / cos(rad(midLat))
        return BBox(
            LatLon(points.minOf { it.lat } - padLat, points.minOf { it.lon } - padLon),
            LatLon(points.maxOf { it.lat } + padLat, points.maxOf { it.lon } + padLon),
        )
    }

    private fun rad(deg: Double) = deg * PI / 180
}
