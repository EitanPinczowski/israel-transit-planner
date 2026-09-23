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
 */
class MapController(private val map: MapLibreMap, private val style: Style) {

    init {
        style.addSource(GeoJsonSource(STOPS_SRC, MapData.EMPTY))
        style.addSource(GeoJsonSource(ROUTE_SRC, MapData.EMPTY))
        style.addSource(GeoJsonSource(PLACES_SRC, MapData.EMPTY))

        style.addLayer(
            CircleLayer(STOPS_LAYER, STOPS_SRC).withProperties(
                PropertyFactory.circleRadius(
                    Expression.switchCase(Expression.toBool(Expression.get("rail")), Expression.literal(6f), Expression.literal(4f)),
                ),
                PropertyFactory.circleColor("#FFFFFF"),
                PropertyFactory.circleStrokeColor("#1E88E5"),
                PropertyFactory.circleStrokeWidth(2f),
            ).apply { setMinZoom(STOPS_MIN_ZOOM) },
        )
        style.addLayer(
            LineLayer(ROUTE_WALK_LAYER, ROUTE_SRC)
                .withFilter(Expression.eq(Expression.get("kind"), "WALK"))
                .withProperties(
                    PropertyFactory.lineColor("#7A7A7A"),
                    PropertyFactory.lineWidth(4f),
                    PropertyFactory.lineDasharray(arrayOf(1f, 1.5f)),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
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
                    PropertyFactory.circleColor("#FFFFFF"),
                    PropertyFactory.circleStrokeColor(Expression.toColor(Expression.get("color"))),
                    PropertyFactory.circleStrokeWidth(3f),
                ),
        )
        style.addLayer(
            CircleLayer(PLACES_LAYER, PLACES_SRC).withProperties(
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleColor("#FFB300"),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(2f),
            ),
        )
    }

    fun setStops(geoJson: String) = source(STOPS_SRC)?.setGeoJson(geoJson)

    fun setRoute(geoJson: String) = source(ROUTE_SRC)?.setGeoJson(geoJson)

    fun setPlaces(geoJson: String) = source(PLACES_SRC)?.setGeoJson(geoJson)

    /** Fit the camera around [points], leaving [bottomPaddingPx] for the results panel. */
    fun fit(points: List<LatLon>, sidePaddingPx: Int, topPaddingPx: Int, bottomPaddingPx: Int) {
        val distinct = points.distinct()
        if (distinct.size < 2) return
        // Location tracking would pull the camera straight back to the user.
        if (map.locationComponent.isLocationComponentActivated) map.locationComponent.cameraMode = CameraMode.NONE
        val bounds = LatLngBounds.Builder().includes(distinct.map { LatLng(it.lat, it.lon) }).build()
        map.animateCamera(
            CameraUpdateFactory.newLatLngBounds(bounds, sidePaddingPx, topPaddingPx, sidePaddingPx, bottomPaddingPx),
            600,
        )
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
        private const val ROUTE_WALK_LAYER = "app-route-walk-layer"
        private const val ROUTE_STOPS_LAYER = "app-route-stops-layer"
        private const val PLACES_LAYER = "app-places-layer"
        private const val STOPS_MIN_ZOOM = 15f
    }
}
