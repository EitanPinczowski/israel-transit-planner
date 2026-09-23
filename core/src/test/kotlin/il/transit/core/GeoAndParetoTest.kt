package il.transit.core

import il.transit.core.features.Option
import il.transit.core.features.TrafficProfile
import il.transit.core.features.capLadder
import il.transit.core.features.paretoFront
import il.transit.core.geo.Geo
import il.transit.core.geo.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoAndParetoTest {
    @Test fun `decodes Google's reference polyline at precision 5`() {
        val pts = Geo.decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 5)
        assertEquals(listOf(LatLon(38.5, -120.2), LatLon(40.7, -120.95), LatLon(43.252, -126.453)), pts)
    }

    @Test fun `round-trips a precision-6 polyline`() {
        val pts = listOf(LatLon(31.262, 34.801), LatLon(31.9, 34.81), LatLon(32.08, 34.78))
        val enc = encodePolyline(pts, 6)
        assertEquals(pts, Geo.decodePolyline(enc.points, enc.precision))
    }

    @Test fun `distance to a line uses the segment, not only its vertices`() {
        val line = listOf(LatLon(31.0, 34.8), LatLon(32.0, 34.8))
        val beside = LatLon(31.5, 34.81) // ~950 m east of the midpoint
        val d = Geo.distanceToLineM(beside, line)
        assertTrue("got $d", d in 900.0..1000.0)
    }

    @Test fun `bbox is padded on every side`() {
        val box = Geo.bbox(listOf(LatLon(31.25, 34.79), LatLon(31.26, 34.80)), padM = 1000.0)
        assertTrue(box.min.lat < 31.25 && box.max.lat > 31.26)
        assertTrue(box.min.lon < 34.79 && box.max.lon > 34.80)
    }

    @Test fun `pareto keeps only undominated options, sorted by arrival`() {
        val opts = listOf(
            Option("slow-free", 0, NOON.plusSeconds(3600), 1),
            Option("fast-costly", 600, NOON.plusSeconds(1800), 1),
            Option("dominated", 700, NOON.plusSeconds(2400), 1), // worse than fast-costly on both
            Option("fewer-transfers", 900, NOON.plusSeconds(2400), 0),
        )
        val front = paretoFront(opts).map { it.payload }
        assertEquals(listOf("fast-costly", "fewer-transfers", "slow-free"), front)
    }

    @Test fun `pareto drops exact duplicates`() {
        val o = Option("x", 60, NOON, 0)
        assertEquals(1, paretoFront(listOf(o, o.copy(payload = "y"))).size)
    }

    @Test fun `traffic factor is peak on a Sunday morning and off-peak on Friday`() {
        val t = TrafficProfile()
        assertEquals(1.3, t.factorAt(SUNDAY_8AM), 1e-9)
        assertEquals(1.0, t.factorAt(NOON), 1e-9)
        assertEquals(1.0, t.factorAt(java.time.Instant.parse("2026-09-25T05:00:00Z")), 1e-9) // Friday 08:00
        assertEquals(780, t.adjust(600, SUNDAY_8AM))
    }

    @Test fun `cap ladder spreads the limit and drops caps under 3 minutes`() {
        assertEquals(listOf(200, 400, 600), capLadder(600))
        assertEquals(listOf(200, 300), capLadder(300))
        assertEquals(listOf(120), capLadder(120))
    }
}
