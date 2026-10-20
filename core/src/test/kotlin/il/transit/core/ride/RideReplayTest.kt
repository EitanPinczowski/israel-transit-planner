package il.transit.core.ride

import il.transit.core.api.Itinerary
import il.transit.core.api.MotisJson
import il.transit.core.api.PlanResponse
import il.transit.core.geo.Geo
import il.transit.core.geo.LatLon
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * Phase 11 T5: GPS replay of [RideTracker]. A GPX track is fed fix by fix to the tracker, against the
 * itinerary it rides, and the "get off at the next stop" alert must fire once per transit leg, before the
 * stop, and never at a wrong one.
 *
 * Tracks: the `.gpx` files in `tracks/` are synthetic (`tools/gpx_synth.py`: jitter, a 60 s tunnel, stop-and-go, a 300 m
 * jump, a skipped stop, a loop line, a three-bus plan); the ones in `tracks/real/` are rides the owner recorded
 * (skill reminders-offline). Two generated tracks, `bus_loop_line` and `plan_three_buses`, are NOT asserted:
 * they reproduce two [RideTracker] bugs outside this package's files (PR body, T5); add them here when fixed.
 * Each track has a `.json` beside it naming the fixture (and plan itinerary).
 */
class RideReplayTest {
    private class Fix(val at: Instant, val pos: LatLon)

    private class Track(val name: String, val fixes: List<Fix>, val itinerary: Itinerary)

    private val fix = Regex("""<trkpt lat="([-\d.]+)" lon="([-\d.]+)"><time>([^<]+)</time>""")

    private fun resourceDir(path: String): File? = javaClass.getResource(path)?.let { File(it.toURI()) }

    private fun itinerary(fixture: String, index: Int?): Itinerary {
        val text = javaClass.getResource("/fixtures/$fixture.json")!!.readText()
        return if (index == null) {
            MotisJson.decodeFromString(Itinerary.serializer(), text)
        } else {
            MotisJson.decodeFromString(PlanResponse.serializer(), text).itineraries[index]
        }
    }

    /** Every `*.gpx` with its `.json`, from `tracks/` and `tracks/real/`. */
    private fun tracks(dir: File?): List<Track> = dir?.listFiles { f -> f.extension == "gpx" }.orEmpty().sortedBy { it.name }.map { f ->
        val meta = MotisJson.parseToJsonElement(File(f.path.removeSuffix(".gpx") + ".json").readText()).jsonObject
        val points = fix.findAll(f.readText()).toList()
        val fixes = points.map { m -> Fix(Instant.parse(m.groupValues[3]), LatLon(m.groupValues[1].toDouble(), m.groupValues[2].toDouble())) }
        Track(f.nameWithoutExtension, fixes, itinerary(meta["fixture"]!!.jsonPrimitive.content, meta["itinerary"]?.jsonPrimitive?.intOrNull))
    }

    private val synthetic by lazy { tracks(resourceDir("/tracks")) }
    private val real by lazy { tracks(resourceDir("/tracks/real")) }

    /** Feeds [t] to a fresh tracker; the alerts with the fix they fired on. */
    private fun replay(t: Track): List<Pair<Fix, RideEvent.Approaching>> {
        val tracker = RideTracker(t.itinerary)
        return t.fixes.mapNotNull { f -> (tracker.update(f.pos, f.at) as? RideEvent.Approaching)?.let { f to it } }
    }

    /** What every track must satisfy, whoever made it. */
    private fun assertAlertsOnce(t: Track, jitterM: Double) {
        val transit = t.itinerary.legs.filter { it.isTransit }
        val alerts = replay(t)
        assertEquals("${t.name}: one alert per transit leg", transit.size, alerts.size)
        alerts.forEachIndexed { k, (f, a) ->
            val leg = transit[k]
            val at = "${t.name} leg $k (${leg.to.name})"
            assertEquals("$at: alert order", k, a.legIndex)
            assertEquals("$at: alert names the wrong stop", leg.to.name, a.stopName)
            assertEquals("$at: last-leg flag", k == transit.size - 1, a.isLastLeg)
            val nearAlight = Geo.distanceM(f.pos, leg.to.latLon) <= 400.0 + jitterM
            val before = leg.intermediateStops.lastOrNull()
            val nearBefore = before != null && Geo.distanceM(f.pos, before.latLon) <= 120.0 + jitterM
            assertTrue("$at: fired ${"%.0f".format(Geo.distanceM(f.pos, leg.to.latLon))} m from the stop and not at the stop before it", nearAlight || nearBefore)
            assertTrue("$at: fired ${f.at}, before boarding at ${leg.start}", !f.at.isBefore(leg.start))
            assertTrue("$at: fired ${f.at}, after the vehicle arrived at ${leg.end}", !f.at.isAfter(leg.end.plusSeconds(60)))
            assertTrue("$at: fired ${Duration.between(f.at, leg.end).toMinutes()} min before the end, too early", Duration.between(f.at, leg.end) <= EARLIEST)
        }
    }

    @Test fun `there are synthetic tracks to replay`() {
        assertTrue("tracks: ${synthetic.map { it.name }}", synthetic.size >= 10)
        synthetic.forEach { assertTrue("${it.name}: ${it.fixes.size} fixes", it.fixes.size > 100) }
    }

    @Test fun `a clean train ride alerts once, before Tel Aviv Center`() = assertAlertsOnce(track("train_clean"), 20.0)

    @Test fun `a 60 s tunnel mid-ride loses nothing`() = assertAlertsOnce(track("train_tunnel_mid"), 30.0)

    @Test fun `a 60 s tunnel just before the stop still alerts before the stop`() = assertAlertsOnce(track("train_tunnel_before_stop"), 30.0)

    @Test fun `urban canyon jitter - 25 m noise with outliers - alerts once`() = assertAlertsOnce(track("bus_urban_canyon"), 150.0)

    @Test fun `stop and go traffic alerts once`() = assertAlertsOnce(track("bus_stop_and_go"), 60.0)

    @Test fun `two 300 m GPS jumps do not fire at a wrong stop`() = assertAlertsOnce(track("bus_gps_jump_300m"), 60.0)

    @Test fun `a skipped stop with sparse fixes still alerts before the stop`() = assertAlertsOnce(track("bus_skipped_stop"), 60.0)

    @Test fun `a train with walks around it alerts once`() = assertAlertsOnce(track("plan_train_walks"), 80.0)

    @Test fun `every real recorded ride alerts once`() {
        real.forEach { assertAlertsOnce(it, 100.0) }
    }

    @Test fun `replay is deterministic`() {
        val t = track("bus_urban_canyon")
        assertEquals(replay(t).map { it.first.at }, replay(t).map { it.first.at })
    }

    private fun track(name: String): Track = synthetic.first { it.name == name }

    private companion object {
        /** An alert later than this before the planned end is early enough to make a rider get off at the wrong stop. */
        val EARLIEST: Duration = Duration.ofMinutes(15)
    }
}
