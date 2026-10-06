package il.transit.planner

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.graphics.RectF
import android.os.Bundle
import androidx.annotation.VisibleForTesting
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import il.transit.core.geo.BBox
import il.transit.core.geo.LatLon
import il.transit.core.geo.MapData
import il.transit.planner.ui.AppTheme
import il.transit.planner.ui.MainScreen
import il.transit.planner.ui.screens.CalendarChip
import il.transit.planner.ui.screens.LocalCalendarChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import il.transit.planner.ui.MapPadding
import il.transit.planner.ui.MainViewModel
import il.transit.planner.ui.MapController
import il.transit.planner.ui.OfflineMapManager
import il.transit.planner.ui.ScreenActions
import il.transit.planner.ui.screens.CrashLogUi
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import il.transit.planner.ui.ViewModelActions
import il.transit.planner.ui.Shortcuts
import il.transit.planner.tile.NextTripTile
import il.transit.planner.remind.LastTripReceiver
import il.transit.planner.remind.Notifications
import il.transit.planner.ui.TripDetailsViewModel
import il.transit.planner.ui.screens.TripDetailsSheet
import il.transit.core.present.vehicleGeoJson
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    private val tripVm: TripDetailsViewModel by viewModels { TripDetailsViewModel.factory(application as TransitApp) }

    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null
    private var style: Style? = null

    /** Non-null once the style has loaded; Compose effects push layer data through it. */
    private val controller = MutableStateFlow<MapController?>(null)

    /** How much of the map the Compose panels and system bars cover, measured by MainScreen. */
    private val mapPadding = MutableStateFlow(MapPadding())

    private lateinit var offline: OfflineMapManager
    private var styleUrl: String = MAP_STYLE

    /** What the camera was last fitted around, for [routeOnScreen]. */
    private var lastFit: List<LatLon> = emptyList()

    /** A shortcut intent waiting for the saved places to load. */
    private var pendingShortcut by mutableStateOf<Intent?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasExtra(Shortcuts.EXTRA_SHORTCUT) || intent.hasExtra(Shortcuts.EXTRA_PLACE)) pendingShortcut = intent
        if (intent.getBooleanExtra(NextTripTile.EXTRA_TRIP_HOME, false)) vm.openTripHome()
        if (intent.getBooleanExtra(Notifications.EXTRA_START_RIDE, false)) startReminderRide()
    }

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
        // Only a fresh launch: after a rotation, a theme change or a reopen from Recents the same
        // intent comes back, and acting on it again would undo whatever the user did since.
        val fresh = savedInstanceState == null && (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0
        if (fresh && (intent.hasExtra(Shortcuts.EXTRA_SHORTCUT) || intent.hasExtra(Shortcuts.EXTRA_PLACE))) pendingShortcut = intent
        // The Quick Settings tile (C4), and C5's alert: the trip home, planned once the store is read.
        if (fresh && intent.getBooleanExtra(NextTripTile.EXTRA_TRIP_HOME, false)) vm.openTripHome()
        // "Start trip" on the leave-now countdown (C5).
        if (fresh && intent.getBooleanExtra(Notifications.EXTRA_START_RIDE, false)) startReminderRide()

        vm.locationProvider = {
            map?.locationComponent?.takeIf { it.isLocationComponentActivated }?.lastKnownLocation
                ?.let { LatLon(it.latitude, it.longitude) }
        }

        lifecycleScope.launch { app.crashLog.refresh() }

        setContent {
            AppTheme {
                val state by vm.state.collectAsState()
                val stops by vm.stops.collectAsState()
                val ctl by controller.collectAsState()
                val offlineState by offline.state.collectAsState()
                val pad by mapPadding.collectAsState()
                val crashes by app.crashLog.count.collectAsState()
                val ui = remember { ViewModelActions(vm) }

                LaunchedEffect(ctl, stops) { ctl?.setStops(stops) }
                LaunchedEffect(ctl, state.savedPlaces) { ctl?.setPlaces(MapData.places(state.savedPlaces)) }
                LaunchedEffect(state.savedPlaces) { Shortcuts.update(this@MainActivity, state.savedPlaces) }
                // An app-icon shortcut opened us: once the saved places are loaded, act on it.
                LaunchedEffect(state.savedPlaces, pendingShortcut) {
                    val i = pendingShortcut ?: return@LaunchedEffect
                    when {
                        i.getStringExtra(Shortcuts.EXTRA_SHORTCUT) == Shortcuts.LINES -> { vm.showFavorites(true); pendingShortcut = null }
                        i.getStringExtra(Shortcuts.EXTRA_PLACE) != null && state.savedPlaces.isNotEmpty() -> {
                            vm.goToPlaceNamed(i.getStringExtra(Shortcuts.EXTRA_PLACE)!!)
                            pendingShortcut = null
                        }
                    }
                }
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
                    if (selected == null) { lastFit = emptyList(); return@LaunchedEffect }
                    lastFit = carPath + MapData.bounds(selected)
                    val margin = (24 * resources.displayMetrics.density).toInt()
                    c.fit(lastFit, pad, margin, mapView.width, mapView.height)
                }
                LaunchedEffect(ctl, pad) { if (ctl != null) placeMapChrome(pad) }

                // Trip sheet (B1): the vehicle on the map; closes when its option is no longer shown.
                val tripSheet by tripVm.sheet.collectAsState()
                LaunchedEffect(ctl, tripSheet?.vehicle, tripSheet?.details?.color) {
                    ctl?.setVehicle(vehicleGeoJson(tripSheet?.vehicle, tripSheet?.details?.color ?: "#000000"))
                }
                LaunchedEffect(selected) {
                    val open = tripVm.sheet.value ?: return@LaunchedEffect
                    if (selected?.legs?.contains(open.leg) != true) tripVm.close()
                }

                val actions = ScreenActions(
                    offline = offlineState,
                    downloadOffline = ::downloadOfflineArea,
                    deleteOffline = offline::delete,
                    remind = ::remind,
                    startRide = ::startRide,
                    onMapPadding = { mapPadding.value = it },
                    crashLog = CrashLogUi(crashes, share = ::shareCrashLog, clear = { lifecycleScope.launch { app.crashLog.clear() } }),
                    openLeg = tripVm::open,
                )
                val openSheet = tripSheet
                val sheetUi: (@Composable (Modifier, Shape) -> Unit)? =
                    if (openSheet != null) ({ m, shape -> TripDetailsSheet(openSheet, onClose = tripVm::close, modifier = m, shape = shape) }) else null
                CompositionLocalProvider(LocalCalendarChip provides { s -> CalendarChip(s, vm) }) {
                    MainScreen(state, ui, actions, tripSheet = sheetUi) {
                        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }

        if (!hasLocationPermission()) {
            askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private val app get() = application as TransitApp

    /** Plain text to whatever app the user picks (mail, WhatsApp…). Nothing leaves without that tap. */
    private fun shareCrashLog() {
        lifecycleScope.launch {
            val text = app.crashLog.report() ?: return@launch
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name) + " – " + getString(R.string.crash_log))
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(send, getString(R.string.crash_log_share)))
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

    /** The same checks as [startRide], for the reminder's own option. */
    private fun startReminderRide() {
        if (!hasLocationPermission()) {
            askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        withNotifications { vm.startReminderRide() }
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
        styleUrl = (application as TransitApp).mapStyle(isNight())
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
     * Where the fitted route sits on screen (window pixels), or null with no route. UI tests
     * check it falls in the part of the map that the panels don't cover.
     */
    @VisibleForTesting
    fun routeOnScreen(): RectF? {
        val m = map ?: return null
        if (lastFit.size < 2) return null
        val pts = lastFit.map { m.projection.toScreenLocation(LatLng(it.lat, it.lon)) }
        return RectF(pts.minOf { it.x }, pts.minOf { it.y }, pts.maxOf { it.x }, pts.maxOf { it.y })
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
    override fun onResume() { super.onResume(); mapView.onResume(); vm.onVisible(true); tripVm.onVisible(true) }
    override fun onPause() { LastTripReceiver.saveSeen(this, vm.locationProvider()); vm.onVisible(false); tripVm.onVisible(false); mapView.onPause(); super.onPause() }
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
