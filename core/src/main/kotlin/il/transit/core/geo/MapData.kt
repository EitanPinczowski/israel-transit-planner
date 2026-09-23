package il.transit.core.geo

import il.transit.core.api.Itinerary
import il.transit.core.api.Place
import il.transit.core.api.TransitModes
import il.transit.core.present.LegKind
import il.transit.core.present.defaultColor
import il.transit.core.present.legColor
import il.transit.core.present.legKind
import il.transit.core.user.SavedPlace
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * GeoJSON for the map layers, built here (not in the app) so it is testable. The app
 * hands these strings straight to MapLibre's `GeoJsonSource.setGeoJson`.
 * Note GeoJSON order is [lon, lat].
 */
object MapData {
    const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

    /**
     * One LineString per leg (`kind`, `color` properties) + a Point at each boarding/alighting
     * stop. [carPath], when given, is drawn first as a CAR line: the ride before the itinerary.
     */
    fun itinerary(it: Itinerary, carPath: List<LatLon> = emptyList()): String = collection {
        if (carPath.size >= 2) {
            add(feature(lineString(carPath)) {
                put("kind", LegKind.CAR.name)
                put("color", defaultColor(LegKind.CAR))
            })
        }
        for (leg in it.legs) {
            val pts = leg.legGeometry?.let { g -> Geo.decodePolyline(g.points, g.precision) }
                ?.takeIf { p -> p.size >= 2 }
                ?: listOf(leg.from.latLon, leg.to.latLon)
            add(feature(lineString(pts)) {
                put("kind", legKind(leg.mode).name)
                put("color", legColor(leg))
            })
        }
        for (leg in it.legs.filter { l -> l.isTransit }) {
            for (p in listOf(leg.from, leg.to)) {
                add(feature(point(p.latLon)) { put("name", p.name); put("color", legColor(leg)) })
            }
        }
    }

    fun stops(stops: List<Place>): String = collection {
        for (s in stops) {
            add(feature(point(s.latLon)) {
                put("name", s.name)
                s.stopId?.let { id -> put("stopId", id) }
                put("rail", s.modes.orEmpty().any { m -> m in TransitModes.RAIL_LIKE })
            })
        }
    }

    fun places(places: List<SavedPlace>): String = collection {
        for (p in places) add(feature(point(p.latLon)) { put("name", p.name) })
    }

    /** Every coordinate of an itinerary, for fitting the camera around it. */
    fun bounds(it: Itinerary): List<LatLon> = it.legs.flatMap { leg ->
        leg.legGeometry?.let { g -> Geo.decodePolyline(g.points, g.precision) } ?: listOf(leg.from.latLon, leg.to.latLon)
    }

    private fun collection(build: MutableList<JsonObject>.() -> Unit): String {
        val features = mutableListOf<JsonObject>().apply(build)
        return buildJsonObject {
            put("type", "FeatureCollection")
            put("features", JsonArray(features))
        }.toString()
    }

    private fun feature(geometry: JsonObject, props: JsonObjectBuilder.() -> Unit) = buildJsonObject {
        put("type", "Feature")
        put("geometry", geometry)
        putJsonObject("properties", props)
    }

    private fun point(p: LatLon) = buildJsonObject {
        put("type", "Point")
        putJsonArray("coordinates") { add(p.lon); add(p.lat) }
    }

    private fun lineString(pts: List<LatLon>) = buildJsonObject {
        put("type", "LineString")
        put("coordinates", buildJsonArray { pts.forEach { p -> add(buildJsonArray { add(p.lon); add(p.lat) }) } })
    }
}

/**
 * When to (re)load the stops layer. Stops only show from [MIN_ZOOM]; a fetch covers the
 * view grown by half on every side, so small pans are served from what is already drawn
 * and do not cost a request.
 */
object StopsViewport {
    const val MIN_ZOOM = 15.0

    fun needsFetch(view: BBox, zoom: Double, loaded: BBox?): Boolean =
        zoom >= MIN_ZOOM && (loaded == null || !loaded.contains(view))

    fun fetchBox(view: BBox): BBox {
        val dLat = (view.max.lat - view.min.lat) / 2
        val dLon = (view.max.lon - view.min.lon) / 2
        return BBox(LatLon(view.min.lat - dLat, view.min.lon - dLon), LatLon(view.max.lat + dLat, view.max.lon + dLon))
    }
}

fun BBox.contains(other: BBox): Boolean =
    other.min.lat >= min.lat && other.min.lon >= min.lon && other.max.lat <= max.lat && other.max.lon <= max.lon
