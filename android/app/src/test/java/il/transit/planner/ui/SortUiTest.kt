package il.transit.planner.ui

import il.transit.core.api.Itinerary
import il.transit.core.api.Leg
import il.transit.core.api.Place
import il.transit.core.features.DropOffKind
import il.transit.core.features.DropOffOption
import il.transit.core.features.DropOffResult
import il.transit.core.features.Option
import il.transit.core.features.ParkRideOption
import il.transit.core.features.ParkRideResult
import il.transit.core.geo.LatLon
import il.transit.core.plan.TripSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant

/** D12: a re-sort keeps the opened option, its card and its car path together. */
class SortUiTest {
    private val t0 = Instant.parse("2026-10-13T05:00:00Z")

    private fun place(name: String, lat: Double) = Place(name = name, lat = lat, lon = 34.8, stopId = name)

    /** Walks [walkMin], then one bus until [endMin]; [transfers] as given. */
    private fun itin(name: String, endMin: Long, transfers: Int, walkMin: Long): Itinerary {
        val a = place("$name-a", 31.2)
        val b = place("$name-b", 32.0)
        val board = t0.plusSeconds(walkMin * 60)
        val end = t0.plusSeconds(endMin * 60)
        val legs = listOf(
            Leg(mode = "WALK", from = a, to = a, duration = (walkMin * 60).toInt(), startTime = t0.toString(), endTime = board.toString()),
            Leg(mode = "BUS", from = a, to = b, duration = (end.epochSecond - board.epochSecond).toInt(), startTime = board.toString(), endTime = end.toString(), tripId = name),
        )
        return Itinerary(duration = (endMin * 60).toInt(), startTime = t0.toString(), endTime = end.toString(), transfers = transfers, legs = legs)
    }

    // Fastest: first, second, third. Least walking: third, first, second.
    private val first = itin("first", 60, 1, walkMin = 10)
    private val second = itin("second", 65, 1, walkMin = 12)
    private val third = itin("third", 90, 0, walkMin = 2)

    @Test fun `drop-off - option 2 opened, re-sorted - the card and the car path still match`() {
        fun d(it: Itinerary, lat: Double) = Option(
            DropOffOption(DropOffKind.STOP_ON_THE_WAY, null, 300, 600, it, listOf(LatLon(31.0, 34.8), LatLon(lat, 34.8))),
            300, it.end, it.transfers,
        )
        val s = UiState(mode = AppMode.DROP_OFF, dropOff = DropOffResult(4000, listOf(d(first, 31.1), d(second, 31.2), d(third, 31.3)), 3)).arranged()
            .copy(selected = 1)
        assertSame(second, s.selectedItinerary)
        val r = s.resorted(TripSort.LEAST_WALKING)
        assertEquals(listOf(third, first, second), r.options)
        assertEquals(TripSort.LEAST_WALKING, r.settings.tripSort)
        assertSame(second, r.selectedItinerary)
        assertEquals(2, r.selected)
        assertEquals(LatLon(31.2, 34.8), r.selectedCarPath.last())
        assertSame(second, r.dropOff!!.options[r.selected].payload.transit) // the card the panel highlights
        // And back: the planner's order again, still on the same option.
        val back = r.resorted(TripSort.FASTEST)
        assertEquals(listOf(first, second, third), back.options)
        assertSame(second, back.selectedItinerary)
    }

    @Test fun `park and ride - option 2 opened, re-sorted - the station and the car path follow`() {
        val origin = LatLon(31.28, 34.93)
        fun pr(it: Itinerary, lat: Double) = Option(
            ParkRideOption(place("station-$lat", lat), 600, 500, it, it.start, it.start),
            600, it.end, it.transfers,
        )
        val result = ParkRideResult(null, listOf(pr(first, 31.1), pr(second, 31.2), pr(third, 31.3)), 3)
        val s = UiState(mode = AppMode.PARK_RIDE, parkRide = ParkRideUi(result = result, origin = origin)).arranged().copy(selected = 1)
        val r = s.resorted(TripSort.FEWEST_TRANSFERS)
        assertEquals(listOf(third, first, second), r.options)
        assertSame(second, r.selectedItinerary)
        assertEquals(listOf(origin, LatLon(31.2, 34.8)), r.selectedCarPath)
        assertSame(second, r.parkRide.selected(r.selected)!!.itinerary)
    }
}
