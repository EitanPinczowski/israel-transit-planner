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
    fun `0 stops between (shown as nothing), and a terminus not on the route name falls back to the last stop`() {
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
    fun `boundaries - 2 fewer gets the chip, exactly 30 min apart still does, 30 min 1 s does not`() {
        assertEquals(setOf(FewerStops(0, 0)), fewerStops(listOf(itinerary(train("24", 0, 3)), itinerary(train("26", 20, 5)))))
        assertEquals(setOf(FewerStops(0, 0)), fewerStops(listOf(itinerary(train("24", 0, 3)), itinerary(train("26", 30, 6)))))
        val late = train("26", 30, 6).let { it.copy(startTime = it.start.plusSeconds(1).toString()) }
        assertTrue(fewerStops(listOf(itinerary(train("24", 0, 3)), itinerary(late))).isEmpty())
    }

    @Test
    fun `a train after a change, two trains in one option, the same trip twice`() {
        val bus = leg("BUS", station("בית"), bsNorth, NOON.minusSeconds(1200), NOON.minusSeconds(300))
        // The fast train is the option's second leg: the chip points at that leg.
        assertEquals(setOf(FewerStops(0, 1)), fewerStops(listOf(itinerary(bus, train("24", 0, 3)), itinerary(train("26", 10, 6)))))
        // Two trains in one option are never compared with each other.
        val haifa = station("חיפה")
        val two = itinerary(train("24", 0, 3), train("90", 80, 6, from = telAviv, to = haifa))
        assertTrue(fewerStops(listOf(two)).isEmpty())
        // ...but each is compared with the other options' trains on its own stations.
        assertEquals(setOf(FewerStops(1, 0)), fewerStops(listOf(two, itinerary(train("92", 70, 2, from = telAviv, to = haifa)))))
        // The same trip in two options has the same stops: no chip.
        val same = train("24", 0, 3).copy(tripId = "t24")
        assertTrue(fewerStops(listOf(itinerary(same), itinerary(same))).isEmpty())
    }

    @Test
    fun `a parent station on the getting-off side only, and a train with no stops listed never wins`() {
        val platform = station("תל אביב מרכז", id = "p3", parent = telAviv.stopId)
        assertEquals(setOf(FewerStops(0, 0)), fewerStops(listOf(itinerary(train("24", 0, 3, to = platform)), itinerary(train("26", 10, 6)))))
        assertTrue(fewerStops(listOf(itinerary(train("24", 0, 0)), itinerary(train("26", 10, 6)))).isEmpty())
    }

    @Test
    fun `route names - no arrow falls back to the last stop, a station with a hyphen still matches`() {
        assertEquals("נהריה", trainLabel(train("24", 0, 3).copy(routeLongName = "נהריה - באר שבע"))!!.to)
        val hyphen = train("24", 0, 3).copy(
            routeLongName = "באר שבע מרכז-באר שבע<->תל אביב-יפו ההגנה-תל אביב יפו",
            tripTo = station("תל אביב-יפו ההגנה"),
        )
        assertEquals("תל אביב יפו", trainLabel(hyphen)!!.to)
    }

    /** The UI only joins these strings; rendered the way Android does (`String.format`), in both languages. */
    @Test
    fun `word order - the number and a Hebrew town are isolated, the dot never ends a line`() {
        val res = File("../android/app/src/main/res")
        fun xml(dir: String) = File(res, "$dir/strings_leg_labels.xml").readText()
        fun string(dir: String, key: String) = Regex("""<string name="$key">([^<]*)</string>""").find(xml(dir))!!.groupValues[1]
        fun plural(dir: String, q: String): String {
            val items = Regex("""<plurals name="train_stops_between">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL).find(xml(dir))!!
            return Regex("""<item quantity="$q">([^<]*)</item>""").find(items.groupValues[1])!!.groupValues[1]
        }
        fun render(dir: String) = string(dir, "train_label").format(string(dir, "train_to").format("406", "כרמיאל"), plural(dir, "other").format(4))
        // English: "Train 406 to ⁨כרמיאל⁩ · 4 stops…": the Hebrew town cannot pull "· 4" into its RTL run.
        assertEquals("Train 406 to \u2068כרמיאל\u2069\u00a0·\u00a04 stops on the way", render("values"))
        assertEquals("רכבת \u2068406\u2069 ל\u2068כרמיאל\u2069\u00a0·\u00a0\u20684\u2069 עצירות בדרך", render("values-iw"))
    }
}
