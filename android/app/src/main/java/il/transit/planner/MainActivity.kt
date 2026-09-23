package il.transit.planner

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * Phase-0 shell: a MapLibre map on OpenFreeMap tiles, centred on the user's location
 * once permission is granted. Planning screens arrive in phase 1 (see ROADMAP.md).
 *
 * The MapView is owned by the Activity (not by Compose) so its lifecycle calls are
 * forwarded exactly once, from the Activity's own callbacks.
 */
class MainActivity : ComponentActivity() {
    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null
    private var style: Style? = null

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        enableLocationIfAllowed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        mapView = MapView(this).apply { onCreate(savedInstanceState) }
        mapView.getMapAsync { m ->
            map = m
            m.cameraPosition = CameraPosition.Builder().target(BEER_SHEVA).zoom(12.0).build()
            m.setStyle(Style.Builder().fromUri(MAP_STYLE)) { s ->
                style = s
                enableLocationIfAllowed()
            }
        }
        setContent { AppTheme { MapScreen(mapView) } }

        if (!hasLocationPermission()) {
            askLocation.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked by hasLocationPermission()
    private fun enableLocationIfAllowed() {
        val m = map ?: return
        val s = style ?: return
        if (!hasLocationPermission()) return
        m.locationComponent.apply {
            activateLocationComponent(LocationComponentActivationOptions.builder(this@MainActivity, s).build())
            isLocationComponentEnabled = true
            cameraMode = CameraMode.TRACKING
            renderMode = RenderMode.COMPASS
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }

    companion object {
        private val BEER_SHEVA = LatLng(31.2622, 34.8013)

        /** OpenFreeMap: free, no key, allowed in apps. Dark map style is a phase-1 item. */
        const val MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"

        /** Transitous usage policy: link to its data sources somewhere visible. */
        const val TRANSITOUS_SOURCES = "https://transitous.org/sources/"
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(), content = content)
}

@Composable
private fun MapScreen(mapView: MapView) {
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        AttributionChip(Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(8.dp))
    }
}

@Composable
private fun AttributionChip(modifier: Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(
        modifier = modifier.clickable {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MainActivity.TRANSITOUS_SOURCES)))
        },
        shape = MaterialTheme.shapes.small,
        tonalElevation = 2.dp,
    ) {
        Text(
            stringResource(R.string.attribution),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
