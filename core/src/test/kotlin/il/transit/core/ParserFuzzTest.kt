package il.transit.core

import il.transit.core.api.DurationEntry
import il.transit.core.api.GeocodeMatch
import il.transit.core.api.Itinerary
import il.transit.core.api.MotisJson
import il.transit.core.api.Place
import il.transit.core.api.PlanResponse
import il.transit.core.api.StopTimesResponse
import il.transit.core.api.TripSegment
import il.transit.core.fare.FareEstimator
import il.transit.core.fare.FareProfile
import il.transit.core.present.LegPalette
import il.transit.core.present.departureRow
import il.transit.core.present.geocodeDetail
import il.transit.core.present.geocodeTown
import il.transit.core.present.rankByTypedTown
import il.transit.core.present.rideDetails
import il.transit.core.present.summarize
import il.transit.core.present.tightTransfers
import il.transit.core.present.transfers
import il.transit.core.search.parsePhoton
import il.transit.core.weather.parseOpenMeteo
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.DateTimeException

/**
 * Phase 11 T1: every recorded answer, mutated ~200 ways, must parse or throw one of the documented errors
 * (`SerializationException` / `IllegalArgumentException` / `IllegalStateException`, see the fault table in the
 * `transitous-api` skill) and never a NullPointerException, ClassCastException, index error or stack overflow.
 * Whatever parses then goes through the `present/` code. A bad timestamp may fail there with a
 * [DateTimeException] (a time that cannot be read cannot be shown); anything else is a bug.
 *
 * Deterministic: seed = [SEED] + the fixture's index. A failure prints the fixture, the seed and the mutant's number.
 */
class ParserFuzzTest {
    companion object {
        const val SEED = 20261013L
        const val MUTANTS = 200
    }

    private val dir = File(javaClass.getResource("/fixtures/plan_bgu_telaviv.json")!!.toURI()).parentFile

    private class Kind(val prefix: String, val parse: (String) -> Any, val present: (Any) -> Unit)

    private fun itineraryChecks(it: Itinerary) {
        summarize(it, FareProfile.REGULAR, it.start)
        summarize(it, FareProfile.STUDENT)
        rideDetails(it)
        transfers(it)
        tightTransfers(it, 5)
        FareEstimator.estimate(it, FareProfile.HALF)
        LegPalette.colors(it.legs)
        it.legs.forEach { l -> l.start; l.end; l.legGeometry?.let { g -> il.transit.core.geo.Geo.decodePolyline(g.points, g.precision) } }
    }

    private val kinds = listOf(
        Kind("plan_", { MotisJson.decodeFromString(PlanResponse.serializer(), it) }) { r ->
            r as PlanResponse
            (r.itineraries + r.direct).forEach(::itineraryChecks)
        },
        Kind("stoptimes_", { MotisJson.decodeFromString(StopTimesResponse.serializer(), it) }) { r ->
            (r as StopTimesResponse).stopTimes.forEach { departureRow(it) }
        },
        Kind("trip_", { MotisJson.decodeFromString(Itinerary.serializer(), it) }) { itineraryChecks(it as Itinerary) },
        Kind("map_trips_", { MotisJson.decodeFromString(ListSerializer(TripSegment.serializer()), it) }) { r ->
            @Suppress("UNCHECKED_CAST")
            (r as List<TripSegment>).forEach { s -> s.tripId; s.routeName; s.depart; s.arrive; s.path() }
        },
        Kind("one_to_many_", { MotisJson.decodeFromString(ListSerializer(DurationEntry.serializer()), it) }) { r ->
            @Suppress("UNCHECKED_CAST")
            (r as List<DurationEntry>).forEach { d -> d.duration?.toInt(); d.distance?.toInt() }
        },
        Kind("map_stops_", { MotisJson.decodeFromString(ListSerializer(Place.serializer()), it) }) { r ->
            @Suppress("UNCHECKED_CAST")
            (r as List<Place>).forEach { p -> p.name.length; p.latLon }
        },
        Kind("geocode_", { MotisJson.decodeFromString(ListSerializer(GeocodeMatch.serializer()), it) }, ::geocodePresent),
        Kind("reverse_geocode_", { MotisJson.decodeFromString(ListSerializer(GeocodeMatch.serializer()), it) }, ::geocodePresent),
        Kind("openmeteo_", { parseOpenMeteo(it) }) { f ->
            f as il.transit.core.weather.RainForecast
            f.hours.forEach { h -> il.transit.core.weather.RainAdvice.rainy(h) }
        },
        Kind("photon_", { parsePhoton(it) }, ::geocodePresent),
    )

    @Suppress("UNCHECKED_CAST")
    private fun geocodePresent(r: Any) {
        val matches = r as List<GeocodeMatch>
        matches.forEach { m -> geocodeTown(m); geocodeDetail(m) }
        rankByTypedTown(matches)
    }

    private fun documented(t: Throwable) = t is SerializationException || t is IllegalArgumentException || t is IllegalStateException

    @Test fun `every recorded answer survives about 200 mutants each`() {
        val problems = ArrayList<String>()
        var mutants = 0
        var parsed = 0
        var fixtures = 0
        for (kind in kinds) {
            val files = dir.listFiles { f -> f.name.startsWith(kind.prefix) && f.name.endsWith(".json") }!!.sortedBy { it.name }
            for (file in files) {
                val text = file.readText()
                // The unmutated answer must parse and present, or the fixture (not the mutator) is the problem.
                // (A recorded error answer, openmeteo_error, throws the documented error itself.)
                try { kind.present(kind.parse(text)) } catch (t: Throwable) { if (!documented(t)) throw t }
                fixtures++
                val seed = SEED + fixtures
                // Large answers get fewer mutants so the whole test stays well under a minute.
                val n = if (text.length > 400_000) 60 else if (text.length > 100_000) 120 else MUTANTS
                Fuzz(seed).mutants(text, n).forEachIndexed { i, m ->
                    mutants++
                    val where = "${file.name} seed=$seed mutant #$i"
                    val value = try {
                        kind.parse(m)
                    } catch (t: Throwable) {
                        if (!documented(t) && problems.size < 20) problems += "$where: parse threw ${t::class.simpleName}: ${t.message?.take(120)}"
                        return@forEachIndexed
                    }
                    parsed++
                    try {
                        kind.present(value)
                    } catch (t: Throwable) {
                        if (t !is DateTimeException && !documented(t) && problems.size < 20) {
                            problems += "$where: present threw ${t::class.simpleName}: ${t.message?.take(120)}\n    at ${t.stackTrace.filter { f -> f.className.startsWith("il.transit") }.take(3).joinToString(" <- ")}"
                        }
                    }
                }
            }
        }
        println("ParserFuzzTest: $fixtures fixtures, $mutants mutants, $parsed parsed")
        assertTrue("$mutants mutants:\n" + problems.joinToString("\n"), problems.isEmpty())
        assertTrue("fuzz did not run", fixtures >= 40 && mutants >= 5000)
    }

    // Found by the fuzz above: both used to throw an undocumented error.
    @Test fun `a cut-off polyline keeps the points before the cut`() {
        val full = encodePolyline(listOf(il.transit.core.geo.LatLon(31.0, 34.8), il.transit.core.geo.LatLon(31.1, 34.9)))
        assertTrue(il.transit.core.geo.Geo.decodePolyline(full.points, 6).size == 2)
        for (cut in 0..full.points.length) il.transit.core.geo.Geo.decodePolyline(full.points.take(cut), 6)
    }

    @Test fun `an Open-Meteo time out of range is the documented error`() {
        val body = """{"hourly":{"time":[9223372036854775807],"precipitation_probability":[1],"precipitation":[0.0]}}"""
        try { parseOpenMeteo(body); org.junit.Assert.fail("should throw") } catch (e: IllegalStateException) { }
    }
}
