package il.transit.core

import il.transit.core.api.MotisJson
import il.transit.core.api.Place
import il.transit.core.api.PlanResponse
import il.transit.core.present.FewerStops
import il.transit.core.present.TrainLabel
import il.transit.core.present.fewerStops
import il.transit.core.present.rideDetails
import il.transit.core.present.trainLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Phase 11 E3 item 5: the train number, its terminus town, and "Fewer stops". No request. */
class TrainLabelTest {
    private fun plan(name: String) =
        MotisJson.decodeFromString(PlanResponse.serializer(), javaClass.getResource("/fixtures/$name.json")!!.readText())

    private fun station(name: String, id: String = "st-$name", parent: String? = null) =
        Place(name = name, lat = 31.25, lon = 34.79, stopId = id, parentId = parent)

    private val bsNorth = station("באר שבע צפון")
    private val telAviv = station("תל אביב מרכז")

    private fun train(number: String?, startMin: Long, between: Int, mode: String = "REGIONAL_RAIL", from: Place = bsNorth, to: Place = telAviv) =
        leg(mode, from, to, NOON.plusSeconds(startMin * 60), NOON.plusSeconds((startMin + 70) * 60)).copy(
            headsign = number,
            tripShortName = "",
            routeLongName = "נהריה-נהריה<->באר שבע מרכז-באר שבע",
            tripTo = station("נהריה"),
            intermediateStops = (1..between).map { station("mid$it") },
        )

    @Test
    fun `recorded trains 404 and 406 carry their number, terminus town and stops on the way`() {
        val t404 = plan("plan_pages_earlier").itineraries[1].legs[1]
        assertEquals(TrainLabel("404", "כרמיאל", 3), trainLabel(t404))
        val t406 = plan("plan_bgu_telaviv").itineraries[1].legs[1]
        assertEquals("406", trainLabel(t406)!!.number)
        assertEquals(4, trainLabel(t406)!!.stopsBetween)
        // The side of "A<->B" whose station is the trip's last stop: Be'er Sheva-bound trains read Be'er Sheva.
        val south = plan("plan_cardropoff_post_probe").itineraries[0].legs[1]
        assertEquals(TrainLabel("41", "באר שבע", 8), trainLabel(south))
    }

    @Test
    fun `the ride-details line carries the label`() {
        val itin = plan("plan_pages_earlier").itineraries[1]
        val ride = rideDetails(itin).legs.first { it.train != null }
        assertEquals("404", ride.train!!.number)
    }

    @Test
    fun `a bus headsign, a tram and a non-numeric headsign get no label`() {
        val bus = train("24", 0, 3, mode = "BUS")
        assertNull(trainLabel(bus))
        assertNull(trainLabel(train("1", 0, 3, mode = "TRAM")))
        assertNull(trainLabel(train("נהריה", 0, 3)))
        assertNull(trainLabel(train("12345", 0, 3)))
        assertNull(trainLabel(train(null, 0, 3)))
        // A numeric trip_short_name wins when the feed ever sends one.
        assertEquals("77", trainLabel(train("נהריה", 0, 3).copy(tripShortName = "77"))!!.number)
    }

    @Test
    fun `0 stops between, and a terminus not on the route name falls back to the last stop`() {
        val t = train("24", 0, 0).copy(routeLongName = null, tripTo = station("חיפה מרכז"))
        assertEquals(TrainLabel("24", "חיפה מרכז", 0), trainLabel(t))
        assertNull(trainLabel(train("24", 0, 0).copy(tripTo = null, routeLongName = null))!!.to)
    }

    @Test
    fun `two trains 25 min apart with 3 and 6 stops - the 3-stop one gets Fewer stops`() {
        val options = listOf(itinerary(train("24", 0, 3)), itinerary(train("26", 25, 6)))
        assertEquals(setOf(FewerStops(0, 0)), fewerStops(options))
    }

    @Test
    fun `equal stops, 1 fewer, over 30 min apart or other stations - no chip`() {
        assertTrue(fewerStops(listOf(itinerary(train("24", 0, 6)), itinerary(train("26", 25, 6)))).isEmpty())
        assertTrue(fewerStops(listOf(itinerary(train("24", 0, 5)), itinerary(train("26", 25, 6)))).isEmpty())
        assertTrue(fewerStops(listOf(itinerary(train("24", 0, 3)), itinerary(train("26", 31, 6)))).isEmpty())
        val other = train("26", 10, 6, to = station("תל אביב ההגנה"))
        assertTrue(fewerStops(listOf(itinerary(train("24", 0, 3)), itinerary(other))).isEmpty())
        // A tram is never compared, and one option alone has nothing to be compared with.
        assertTrue(fewerStops(listOf(itinerary(train("1", 0, 1, mode = "TRAM")), itinerary(train("2", 10, 6, mode = "TRAM")))).isEmpty())
        assertTrue(fewerStops(listOf(itinerary(train("24", 0, 3)))).isEmpty())
    }

    @Test
    fun `a platform and its parent station are the same station`() {
        val platform = station("באר שבע צפון", id = "p1", parent = bsNorth.stopId)
        val options = listOf(itinerary(train("24", 0, 3, from = platform)), itinerary(train("26", 20, 6)))
        assertEquals(setOf(FewerStops(0, 0)), fewerStops(options))
    }

    @Test
    fun `recorded answers - 406 over 28, 404 over 26`() {
        assertEquals(setOf(FewerStops(1, 1)), fewerStops(plan("plan_bgu_telaviv").itineraries))
        assertEquals(setOf(FewerStops(1, 1)), fewerStops(plan("plan_pages_earlier").itineraries))
        // The Later page: 32 (07:41, 7 stops) beats 638 (07:12, 10) 29 min earlier; 30 (06:41) is 31 min
        // before 638, 642 (08:12) 31 min after 32: too far apart to compare.
        assertEquals(setOf(FewerStops(5, 1)), fewerStops(plan("plan_pages_later").itineraries))
    }

    @Test
    fun `Hebrew word order - the number between FSI and PDI after the word, the town after it`() {
        val res = File("../android/app/src/main/res")
        fun string(dir: String, key: String) =
            Regex("""<string name="$key">([^<]*)</string>""").find(File(res, "$dir/strings_leg_labels.xml").readText())!!.groupValues[1]
        assertEquals("רכבת ⁨%1\$s⁩ ל%2\$s", string("values-iw", "train_to"))
        assertEquals("Train %1\$s to %2\$s", string("values", "train_to"))
        assertEquals("%1\$s · %2\$s", string("values-iw", "train_label"))
    }
}
