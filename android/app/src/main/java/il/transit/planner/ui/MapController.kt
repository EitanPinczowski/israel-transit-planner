package il.transit.planner.ui

import il.transit.core.geo.LatLon
import il.transit.core.geo.MapData
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * Owns the app's map layers. Everything drawn comes in as GeoJSON built by
 * `il.transit.core.geo.MapData`, so this class only wires sources to layers.
 * [dark] matches the OpenFreeMap `dark` style: no white discs glaring at night, and a
 * walking line light enough to see on a dark street.
 */
class MapController(private val map: MapLibreMap, private val style: Style, dark: Boolean) {
    private val c = if (dark) DARK else LIGHT

    init {
        style.addSource(GeoJsonSource(STOPS_SRC, MapData.EMPTY))
        style.addSource(GeoJsonSource(ROUTE_SRC, MapData.EMPTY))
        style.addSource(GeoJsonSource(PLACES_SRC, MapData.EMPTY))

        style.addLayer(
            CircleLayer(STOPS_LAYER, STOPS_SRC).withProperties(
                PropertyFactory.circleRadius(
                    Expression.switchCase(Expression.toBool(Expression.get("rail")), Expression.literal(6f), Expression.literal(4f)),
                ),
                PropertyFactory.circleColor(c.fill),
                PropertyFactory.circleStrokeColor(c.stopStroke),
                PropertyFactory.circleStrokeWidth(2f),
            ).apply { setMinZoom(STOPS_MIN_ZOOM) },
        )
        style.addLayer(
            LineLayer(ROUTE_WALK_LAYER, ROUTE_SRC)
                .withFilter(Expression.eq(Expression.get("kind"), "WALK"))
                .withProperties(
                    PropertyFactory.lineColor(c.walk),
                    PropertyFactory.lineWidth(4f),
                    PropertyFactory.lineDasharray(arrayOf(1f, 1.5f)),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                ),
        )
        // A halo under every ride, so a blue bus line still stands out on blue water or a
        // dark map, and crossings stay readable.
        style.addLayer(
            LineLayer(ROUTE_CASING_LAYER, ROUTE_SRC)
                .withFilter(Expression.neq(Expression.get("kind"), "WALK"))
                .withProperties(
                    PropertyFactory.lineColor(c.casing),
                    PropertyFactory.lineWidth(9f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
        )
        style.addLayer(
            LineLayer(ROUTE_LAYER, ROUTE_SRC)
                .withFilter(Expression.neq(Expression.get("kind"), "WALK"))
                .withProperties(
                    PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
                    PropertyFactory.lineWidth(6f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
        )
        style.addLayer(
            CircleLayer(ROUTE_STOPS_LAYER, ROUTE_SRC)
                .withFilter(Expression.eq(Expression.geometryType(), "Point"))
                .withProperties(
                    PropertyFactory.circleRadius(6f),
                    PropertyFactory.circleColor(c.fill),
                    PropertyFactory.circleStrokeColor(Expression.toColor(Expression.get("color"))),
                    PropertyFactory.circleStrokeWidth(3f),
                ),
        )
        style.addLayer(
            CircleLayer(PLACES_LAYER, PLACES_SRC).withProperties(
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleColor("#FFB300"),
                PropertyFactory.circleStrokeColor(c.fill),
                PropertyFactory.circleStrokeWidth(2f),
            ),
        )
    }

    fun setStops(geoJson: String) = source(STOPS_SRC)?.setGeoJson(geoJson)

    fun setRoute(geoJson: String) = source(ROUTE_SRC)?.setGeoJson(geoJson)

    fun setPlaces(geoJson: String) = source(PLACES_SRC)?.setGeoJson(geoJson)

    /**
     * Fit the camera around [points] inside the part of the map our panels leave visible
     * ([covered] plus [marginPx] on each side). On a short phone the panels can cover most
     * of a [viewWidth] x [viewHeight] map; the padding then shrinks so a route is never
     * squeezed into nothing.
     */
    fun fit(points: List<LatLon>, covered: MapPadding, marginPx: Int, viewWidth: Int, viewHeight: Int) {
        val distinct = points.distinct()
        if (distinct.size < 2 || viewWidth <= 0 || viewHeight <= 0) return
        // Location tracking would pull the camera straight back to the user.
        if (map.locationComponent.isLocationComponentActivated) map.locationComponent.cameraMode = CameraMode.NONE
        val bounds = LatLngBounds.Builder().includes(distinct.map { LatLng(it.lat, it.lon) }).build()
        val (left, right) = squeeze(covered.left + marginPx, covered.right + marginPx, viewWidth)
        val (top, bottom) = squeeze(covered.top + marginPx, covered.bottom + marginPx, viewHeight)
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, left, top, right, bottom), 600)
    }

    /** Scales two paddings down so at least a fifth of [size] stays for the route. */
    private fun squeeze(a: Int, b: Int, size: Int): Pair<Int, Int> {
        val max = (size * 0.8).toInt()
        if (a + b <= max) return a to b
        val k = max.toDouble() / (a + b)
        return (a * k).toInt() to (b * k).toInt()
    }

    /** The stop under a tap, as (stopId, name), or null. */
    fun stopAt(tap: LatLng): Pair<String, String>? {
        val screen = map.projection.toScreenLocation(tap)
        val feature = map.queryRenderedFeatures(screen, STOPS_LAYER).firstOrNull() ?: return null
        val id = feature.getStringProperty("stopId") ?: return null
        return id to (feature.getStringProperty("name") ?: "")
    }

    private fun source(id: String): GeoJsonSource? = style.getSourceAs(id)

    companion object {
        private const val STOPS_SRC = "app-stops"
        private const val ROUTE_SRC = "app-route"
        private const val PLACES_SRC = "app-places"
        const val STOPS_LAYER = "app-stops-layer"
        private const val ROUTE_LAYER = "app-route-layer"
        private const val ROUTE_CASING_LAYER = "app-route-casing-layer"
        private const val ROUTE_WALK_LAYER = "app-route-walk-layer"
        private const val ROUTE_STOPS_LAYER = "app-route-stops-layer"
        private const val PLACES_LAYER = "app-places-layer"
        private const val STOPS_MIN_ZOOM = 15f

        /** Marker fill (stops, boarding points, saved-place ring), stop ring, walk line, ride halo. */
        private class Palette(val fill: String, val stopStroke: String, val walk: String, val casing: String)

        private val LIGHT = Palette(fill = "#FFFFFF", stopStroke = "#1E88E5", walk = "#6B6F76", casing = "#FFFFFF")
        private val DARK = Palette(fill = "#1D2024", stopStroke = "#9ECAFF", walk = "#B4B8C0", casing = "#111418")
    }
}
