package il.transit.planner

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import android.view.Gravity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import il.transit.core.geo.BBox
import il.transit.core.geo.LatLon
import il.transit.core.geo.MapData
import il.transit.planner.ui.AppTheme
import il.transit.planner.ui.MainScreen
import il.transit.planner.ui.MapPadding
import il.transit.planner.ui.MainViewModel
import il.transit.planner.ui.MapController
import il.transit.planner.ui.OfflineMapManager
import il.transit.planner.ui.ScreenActions
import il.transit.planner.ui.ViewModelActions
import kotlinx.coroutines.flow.MutableStateFlow
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * Hosts the map and the Compose UI. The MapView is owned here (not by Compose) so its
 * lifecycle calls are forwarded exactly once, from the Activity's own callbacks. Map
 * layers are driven by [MapController]; everything else is [MainViewModel] state.
 */
class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels { MainViewModel.factory(application as TransitApp) }

    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null
    private var style: Style? = null

    /** Non-null once the style has loaded; Compose effects push layer data through it. */
    private val controller = MutableStateFlow<MapController?>(null)

    /** How much of the map the Compose panels and system bars cover, measured by MainScreen. */
    private val mapPadding = MutableStateFlow(MapPadding())

    private lateinit var offline: OfflineMapManager
    private var styleUrl: String = MAP_STYLE

    /** What to do once the notification-permission prompt is answered. */
    private var afterNotificationPrompt: () -> Unit = {}

    /** Whatever the answer, carry on: alarms and rides still work, only the banners may be blocked. */
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        afterNotificationPrompt()
    }

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        enableLocationIfAllowed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        // Before the first tiles arrive the map shows this colour, not a white flash at night.
        val options = MapLibreMapOptions.createFromAttributes(this, null)
            .foregroundLoadColor(getColor(R.color.window_background))
        mapView = MapView(this, options).apply { onCreate(savedInstanceState) }
        mapView.getMapAsync(::onMapReady)
        offline = OfflineMapManager(this)

        vm.locationProvider = {
            map?.locationComponent?.takeIf { it.isLocationComponentActivated }?.lastKnownLocation
                ?.let { LatLon(it.latitude, it.longitude) }
        }

        setContent {
            AppTheme {
                val state by vm.state.collectAsState()
                val stops by vm.stops.collectAsState()
                val ctl by controller.collectAsState()
                val offlineState by offline.state.collectAsState()
                val pad by mapPadding.collectAsState()
                val ui = remember { ViewModelActions(vm) }

                LaunchedEffect(ctl, stops) { ctl?.setStops(stops) }
                LaunchedEffect(ctl, state.savedPlaces) { ctl?.setPlaces(MapData.places(state.savedPlaces)) }
                val selected = state.selectedItinerary
                val carPath = state.selectedCarPath
                LaunchedEffect(ctl, selected, carPath) {
                    val c = ctl ?: return@LaunchedEffect
                    c.setRoute(if (selected == null) MapData.EMPTY else MapData.itinerary(selected, carPath))
                }
                // Re-fit when the panels change size too (sheet folded, search card unfolded):
                // the route always sits in the part of the map that is actually visible.
                LaunchedEffect(ctl, selected, carPath, pad) {
                    val c = ctl ?: return@LaunchedEffect
                    if (selected == null) return@LaunchedEffect
                    val margin = (24 * resources.displayMetrics.density).toInt()
                    c.fit(carPath + MapData.bounds(selected), pad, margin, mapView.width, mapView.height)
                }
                LaunchedEffect(ctl, pad) { if (ctl != null) placeMapChrome(pad) }

                val actions = ScreenActions(
                    offline = offlineState,
                    downloadOffline = ::downloadOfflineArea,
                    deleteOffline = offline::delete,
                    remind = ::remind,
                    startRide = ::startRide,
                    onMapPadding = { mapPadding.value = it },
                )
                MainScreen(state, ui, actions) {
                    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
                }
            }
        }

        if (!hasLocationPermission()) {
            askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun remind() = withNotifications { vm.remindSelected() }

    /** The ride service tracks GPS, so it also needs location. */
    private fun startRide() {
        if (!hasLocationPermission()) {
            askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        withNotifications { vm.startRide() }
    }

    private fun withNotifications(then: () -> Unit) {
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsk) {
            afterNotificationPrompt = then
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            then()
        }
    }

    /** Downloads what is on screen now; the manager refuses areas larger than a city. */
    private fun downloadOfflineArea() {
        val m = map ?: return
        offline.download(m.projection.visibleRegion.latLngBounds, styleUrl, resources.displayMetrics.density)
    }

    private fun onMapReady(m: MapLibreMap) {
        map = m
        m.cameraPosition = CameraPosition.Builder().target(BEER_SHEVA).zoom(12.0).build()
        m.uiSettings.apply {
            // Our attribution chip names MapLibre's data sources; the logo only adds clutter.
            // The ⓘ (OpenStreetMap credits, required) moves to the bottom end, away from the chip.
            isLogoEnabled = false
            attributionGravity = Gravity.BOTTOM or Gravity.END
            setAttributionTintColor(getColor(R.color.brand))
        }
        placeMapChrome(mapPadding.value)
        styleUrl = if (isNight()) MAP_STYLE_DARK else MAP_STYLE
        m.setStyle(Style.Builder().fromUri(styleUrl)) { s ->
            style = s
            controller.value = MapController(m, s, isNight())
            enableLocationIfAllowed()
        }
        m.addOnMapLongClickListener { p ->
            vm.setDestinationFromMap(LatLon(p.latitude, p.longitude))
            true
        }
        m.addOnMapClickListener { p ->
            val hit = controller.value?.stopAt(p) ?: return@addOnMapClickListener false
            vm.openStop(hit.first, hit.second)
            true
        }
        m.addOnCameraIdleListener {
            val b = m.projection.visibleRegion.latLngBounds
            vm.onViewport(
                BBox(LatLon(b.latitudeSouth, b.longitudeWest), LatLon(b.latitudeNorth, b.longitudeEast)),
                m.cameraPosition.zoom,
            )
        }
    }

    /**
     * Keeps the compass and the ⓘ inside the visible map: below the search card, above the
     * results sheet, beside a side panel, and clear of bars, notches and rounded corners.
     */
    private fun placeMapChrome(p: MapPadding) {
        val m = map ?: return
        val g = (12 * resources.displayMetrics.density).toInt()
        m.uiSettings.setCompassMargins(p.left + g, p.top + g, p.right + g, p.bottom + g)
        m.uiSettings.setAttributionMargins(p.left + g, p.top + g, p.right + g, p.bottom + g)
    }

    private fun isNight(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked by hasLocationPermission()
    private fun enableLocationIfAllowed() {
        val m = map ?: return
        val s = style ?: return
        if (!hasLocationPermission() || m.locationComponent.isLocationComponentActivated) return
        m.locationComponent.apply {
            activateLocationComponent(LocationComponentActivationOptions.builder(this@MainActivity, s).build())
            isLocationComponentEnabled = true
            cameraMode = CameraMode.TRACKING
            renderMode = RenderMode.COMPASS
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume(); vm.onVisible(true) }
    override fun onPause() { vm.onVisible(false); mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }

    companion object {
        private val BEER_SHEVA = LatLng(31.2622, 34.8013)

        /** OpenFreeMap: free, no key, allowed in apps. */
        const val MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"
        const val MAP_STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"
    }
}
