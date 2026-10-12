package il.transit.core

import il.transit.core.api.Place
import il.transit.core.geo.LatLon
import il.transit.core.present.COLLAPSE_OVER
import il.transit.core.present.LegKind
import il.transit.core.present.RideDetails
import il.transit.core.present.StopListItem
import il.transit.core.present.StopRole
import il.transit.core.present.TripStopRow
import il.transit.core.present.collapse
import il.transit.core.present.rideDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 10 D1: ride details on every option and the folded stop list. No request. */
class RideDetailsTest {
    private fun stop(name: String, code: String? = null, modes: List<String>? = null) =
        Place(name = name, lat = 31.25, lon = 34.79, stopId = "s-$name", stopCode = code, modes = modes)

    private fun ride(mode: String, line: String?, from: Place, to: Place, startMin: Long, endMin: Long, between: Int) =
        leg(mode, from, to, NOON.plusSeconds(startMin * 60), NOON.plusSeconds(endMin * 60))
            .copy(routeShortName = line, intermediateStops = (1..between).map { stop("mid$it") })

    private fun walk(from: Place, to: Place, startMin: Long, endMin: Long) =
        leg("WALK", from, to, NOON.plusSeconds(startMin * 60), NOON.plusSeconds(endMin * 60))

    @Test
    fun `a bus leg says stops, minutes, get-off stop and its number`() {
        val bus = ride("BUS", "5", stop("Soroka", "40001"), stop("Rager/Oren", "47899"), 2, 25, between = 11)
        val d = rideDetails(itinerary(walk(stop("Home"), stop("Soroka"), 0, 2), bus))
        val r = d.legs.single()
        assertEquals(1, r.legIndex)
        assertEquals("5", r.line)
        assertEquals(12, r.stops)
        assertEquals(23, r.minutes)
        assertEquals("Rager/Oren", r.getOff)
        assertEquals("47899", r.stopCode)
        assertEquals(d.total!!.stops, 12)
        assertEquals(d.total!!.minutes, 23)
    }

    @Test
    fun `a leg with no intermediate stops is one stop`() {
        val d = rideDetails(itinerary(ride("BUS", "1", stop("A", "1"), stop("B", "2"), 0, 3, between = 0)))
        assertEquals(1, d.legs.single().stops)
        assertEquals(3, d.legs.single().minutes)
    }

    @Test
    fun `a train leg has no stop number and no line`() {
        val savidor = stop("Tel Aviv Savidor", code = "17", modes = listOf("RAIL"))
        val d = rideDetails(itinerary(ride("RAIL", null, stop("Be'er Sheva North", "13", listOf("RAIL")), savidor, 0, 60, between = 4)))
        val r = d.legs.single()
        assertEquals(LegKind.TRAIN, r.kind)
        assertNull(r.line)
        assertNull(r.stopCode)
        assertEquals("Tel Aviv Savidor", r.getOff)
        assertEquals(5, r.stops)
    }

    @Test
    fun `no stop code in the data gives the name alone`() {
        val r = rideDetails(itinerary(ride("BUS", "5", stop("A"), stop("B", code = "  "), 0, 5, between = 1))).legs.single()
        assertNull(r.stopCode)
        assertEquals("B", r.getOff)
    }

    @Test
    fun `five legs show the first three and plus two, totals cover all`() {
        val stops = (0..5).map { stop("S$it", "${100 + it}") }
        val legs = (0 until 5).map { i -> ride("BUS", "${i + 1}", stops[i], stops[i + 1], i * 10L, i * 10L + 8, between = i) }
        val d = rideDetails(itinerary(*legs.toTypedArray(), transfers = 4))
        assertEquals(5, d.legs.size)
        assertEquals(listOf("1", "2", "3"), d.shown.map { it.line })
        assertEquals(2, d.more)
        assertEquals(1 + 2 + 3 + 4 + 5, d.total!!.stops)
        assertEquals(40, d.total!!.minutes)
        assertEquals("S5", d.total!!.getOff)
    }

    @Test
    fun `four legs are all shown`() {
        val stops = (0..4).map { stop("S$it") }
        val legs = (0 until RideDetails.MAX_LINES).map { i -> ride("BUS", "$i", stops[i], stops[i + 1], i * 10L, i * 10L + 5, 0) }
        val d = rideDetails(itinerary(*legs.toTypedArray()))
        assertEquals(4, d.shown.size)
        assertEquals(0, d.more)
    }

    @Test
    fun `a walk-only option has no ride details`() {
        val d = rideDetails(itinerary(walk(stop("A"), stop("B"), 0, 12)))
        assertTrue(d.legs.isEmpty())
        assertNull(d.total)
    }

    // --- collapse ---

    private fun row(i: Int, role: StopRole, next: Boolean = false, cancelled: Boolean = false, alert: Boolean = false) = TripStopRow(
        name = "R$i", stopId = "r$i", at = LatLon(31.0, 34.0), scheduled = "08:00", live = null, delayMin = null,
        cancelled = cancelled, role = role, passed = false, isNext = next,
        alerts = if (alert) listOf(il.transit.core.present.AlertText("Detour", "")) else emptyList(), time = null,
    )

    /** 2 stops before, boarding, [ride] stops ridden, get off, 1 after. */
    private fun rows(ride: Int, mark: (Int) -> TripStopRow? = { null }): List<TripStopRow> {
        val roles = List(2) { StopRole.BEFORE } + StopRole.BOARD + List(ride) { StopRole.RIDE } + StopRole.ALIGHT + StopRole.AFTER
        return roles.mapIndexed { i, r -> mark(i) ?: row(i, r) }
    }

    @Test
    fun `six stops in between stay open`() {
        val r = rows(COLLAPSE_OVER)
        assertEquals(r.indices.map { StopListItem.Stop(it) }, collapse(r))
    }

    @Test
    fun `twelve stops fold to board, first, N stops, last before yours, get off`() {
        val r = rows(12) // board = 2, ride 3..14, alight = 15
        val c = collapse(r)
        assertEquals(
            listOf(StopListItem.Stop(0), StopListItem.Stop(1), StopListItem.Stop(2), StopListItem.Stop(3), StopListItem.Folded(4, 13),
                StopListItem.Stop(14), StopListItem.Stop(15), StopListItem.Stop(16)),
            c,
        )
        assertEquals(10, (c[4] as StopListItem.Folded).count)
    }

    @Test
    fun `tap expands, tap again collapses`() {
        val r = rows(12)
        val open = collapse(r, expanded = setOf(4))
        assertEquals(StopListItem.Folded(4, 13, open = true), open[4])
        assertEquals((4..13).map { StopListItem.Stop(it) }, open.subList(5, 15))
        assertEquals(StopListItem.Folded(4, 13), collapse(r, expanded = emptySet())[4])
    }

    @Test
    fun `the next stop, a skipped stop and an alert are never folded`() {
        val r = rows(12) { i ->
            when (i) {
                7 -> row(i, StopRole.RIDE, next = true)
                9 -> row(i, StopRole.RIDE, cancelled = true)
                10 -> row(i, StopRole.RIDE, alert = true)
                else -> null
            }
        }
        val c = collapse(r)
        assertTrue(StopListItem.Stop(7) in c && StopListItem.Stop(9) in c && StopListItem.Stop(10) in c)
        assertTrue(StopListItem.Folded(4, 6) in c)
        // A fold of one stop (8) is just shown.
        assertTrue(StopListItem.Stop(8) in c)
        assertTrue(StopListItem.Folded(11, 13) in c)
        // Every row is either shown or inside exactly one fold.
        val covered = c.flatMap { if (it is StopListItem.Folded) (it.from..it.to).toList() else listOf((it as StopListItem.Stop).index) }
        assertEquals(r.indices.toList(), covered)
    }

    @Test
    fun `no boarding stop known shows every row`() {
        val r = (0 until 20).map { row(it, StopRole.RIDE) }
        assertEquals(r.size, collapse(r).size)
    }
}
