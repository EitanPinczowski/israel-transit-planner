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
        style.addSource(GeoJsonSource(OTHERS_SRC, MapData.EMPTY))
        style.addSource(GeoJsonSource(ROUTE_SRC, MapData.EMPTY))
        style.addSource(GeoJsonSource(PLACES_SRC, MapData.EMPTY))
        style.addSource(GeoJsonSource(VEHICLE_SRC, MapData.EMPTY))

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
        // The options not selected (D11): thin and faded, under the selected route; a tap selects one.
        style.addLayer(
            LineLayer(OTHERS_LAYER, OTHERS_SRC).withProperties(
                PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
                PropertyFactory.lineWidth(4f),
                PropertyFactory.lineOpacity(0.55f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
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
        // The trip sheet's vehicle, on top of everything. A timetable position (every MOT line)
        // is drawn see-through; the sheet labels it "scheduled position".
        style.addLayer(
            CircleLayer(VEHICLE_LAYER, VEHICLE_SRC).withProperties(
                PropertyFactory.circleRadius(9f),
                PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
                PropertyFactory.circleOpacity(
                    Expression.switchCase(Expression.toBool(Expression.get("realTime")), Expression.literal(1f), Expression.literal(0.7f)),
                ),
                PropertyFactory.circleStrokeColor(c.fill),
                PropertyFactory.circleStrokeWidth(3f),
            ),
        )
    }

    fun setStops(geoJson: String) = setSource(STOPS_SRC, geoJson)

    fun setRoute(geoJson: String) = setSource(ROUTE_SRC, geoJson)

    /** The other options (`OptionsMap.others`), drawn faded under [setRoute]'s. */
    fun setOthers(geoJson: String) = setSource(OTHERS_SRC, geoJson)

    fun setPlaces(geoJson: String) = setSource(PLACES_SRC, geoJson)

    /** The trip sheet's vehicle (`vehicleGeoJson`), or `MapData.EMPTY`. */
    fun setVehicle(geoJson: String) = setSource(VEHICLE_SRC, geoJson)

    /** A Compose effect can still hold this controller after a theme switch replaced [style]. */
    private fun setSource(id: String, geoJson: String) {
        MapGuard.run("set $id") { source(id)?.setGeoJson(geoJson) }
    }

    /**
     * Fit the camera around [points] inside the part of the map our panels leave visible
     * ([covered] plus [marginPx] on each side). On a short phone the panels can cover most
     * of a [viewWidth] x [viewHeight] map; the padding then shrinks so a route is never
     * squeezed into nothing.
     */
    fun fit(points: List<LatLon>, covered: MapPadding, marginPx: Int, viewWidth: Int, viewHeight: Int) {
        MapGuard.run("fit") { fitNow(points, covered, marginPx, viewWidth, viewHeight) }
    }

    private fun fitNow(points: List<LatLon>, covered: MapPadding, marginPx: Int, viewWidth: Int, viewHeight: Int) {
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
    fun stopAt(tap: LatLng): Pair<String, String>? = MapGuard.run("stopAt") { stopAtNow(tap) }

    private fun stopAtNow(tap: LatLng): Pair<String, String>? {
        if (!style.isFullyLoaded) return null
        val screen = map.projection.toScreenLocation(tap)
        val feature = map.queryRenderedFeatures(screen, STOPS_LAYER).firstOrNull() ?: return null
        val id = feature.getStringProperty("stopId") ?: return null
        return id to (feature.getStringProperty("name") ?: "")
    }

    /** The option whose faded line is under a tap (a finger's width around it), or null. */
    fun optionAt(tap: LatLng): Int? = MapGuard.run("optionAt") { optionAtNow(tap) }

    private fun optionAtNow(tap: LatLng): Int? {
        if (!style.isFullyLoaded) return null
        val p = map.projection.toScreenLocation(tap)
        val r = TAP_SLOP_DP * android.content.res.Resources.getSystem().displayMetrics.density
        val box = android.graphics.RectF(p.x - r, p.y - r, p.x + r, p.y + r)
        return map.queryRenderedFeatures(box, OTHERS_LAYER).firstNotNullOfOrNull { f -> f.getNumberProperty("option")?.toInt() }
    }

    private fun source(id: String): GeoJsonSource? = if (style.isFullyLoaded) style.getSourceAs(id) else null

    companion object {
        private const val STOPS_SRC = "app-stops"
        private const val ROUTE_SRC = "app-route"
        private const val PLACES_SRC = "app-places"
        private const val OTHERS_SRC = "app-others"
        private const val OTHERS_LAYER = "app-others-layer"

        /** Half the 48 dp touch target: a thin line is hard to hit exactly. */
        private const val TAP_SLOP_DP = 24f
        const val STOPS_LAYER = "app-stops-layer"
        private const val ROUTE_LAYER = "app-route-layer"
        private const val ROUTE_CASING_LAYER = "app-route-casing-layer"
        private const val ROUTE_WALK_LAYER = "app-route-walk-layer"
        private const val ROUTE_STOPS_LAYER = "app-route-stops-layer"
        private const val PLACES_LAYER = "app-places-layer"
        private const val VEHICLE_SRC = "app-vehicle"
        private const val VEHICLE_LAYER = "app-vehicle-layer"
        private const val STOPS_MIN_ZOOM = 15f

        /** Marker fill (stops, boarding points, saved-place ring), stop ring, walk line, ride halo. */
        private class Palette(val fill: String, val stopStroke: String, val walk: String, val casing: String)

        private val LIGHT = Palette(fill = "#FFFFFF", stopStroke = "#1E88E5", walk = "#6B6F76", casing = "#FFFFFF")
        private val DARK = Palette(fill = "#1D2024", stopStroke = "#9ECAFF", walk = "#B4B8C0", casing = "#111418")
    }
}
