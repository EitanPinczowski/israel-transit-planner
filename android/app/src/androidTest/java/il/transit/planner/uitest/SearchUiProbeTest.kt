package il.transit.planner.uitest

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.util.Log
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import il.transit.core.api.GeocodeMatch
import il.transit.core.api.GuardedTransitApi
import il.transit.core.api.MotisClient
import il.transit.core.api.TransitApi
import il.transit.core.geo.LatLon
import il.transit.core.search.FallbackGeocoder
import il.transit.core.search.GuardedGeocoder
import il.transit.core.search.PhotonClient
import il.transit.planner.data.AndroidGeocoder
import il.transit.planner.ui.AppLocale
import il.transit.planner.ui.Field
import kotlinx.coroutines.delay
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * F11 probe (bug bash A4), scratch branches only: the real app UI against the LIVE services, as the release build
 * has them. Types into the search field and records what the list shows. Conditions (location, permission, locale)
 * are set by the workflow between runs; `slowMs` delays every Transitous geocode; `ime` = now | wait.
 */
@RunWith(AndroidJUnit4::class)
class SearchUiProbeTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val args = InstrumentationRegistry.getArguments()
    private val target = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun probe() {
        val cond = args.getString("cond") ?: "?"
        val slowMs = args.getString("slowMs")?.toLong() ?: 0L
        val ime = args.getString("ime") ?: "wait"
        val lm = target.getSystemService(LocationManager::class.java)
        val perm = target.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val head = "cond=$cond ime=$ime slowMs=$slowMs perm=$perm locOn=${lm.isLocationEnabled} geocoder=${Geocoder.isPresent()} " +
            "sysLocale=${android.content.res.Resources.getSystem().configuration.locales[0]} lang=${AppLocale.transitLanguage(target)}"
        val out = StringBuilder("\n### $head\n| query | editing | query in box | rows | notFound | offline | to | near |\n|---|---|---|---|---|---|---|---|\n")
        Log.i(TAG, head)
        for (q in listOf("TLV", "Rehovot railway station", "Tel Aviv Central Bus Station")) {
            val d = AppDriver(compose, checkA11y = false)
            d.app.liveApi = slow(GuardedTransitApi(MotisClient()), slowMs)
            val device = if (Geocoder.isPresent()) GuardedGeocoder(AndroidGeocoder(target)) else null
            d.app.liveBackup = FallbackGeocoder(device, GuardedGeocoder(PhotonClient()))
            val line = try {
                d.launch()
                d.onMain { it.startEditing(Field.TO) }
                d.awaitNode(hasSetTextAction(), 10_000)
                val field = compose.onAllNodes(hasSetTextAction()).onFirst()
                field.performTextInput(q)
                if (ime == "now") field.performImeAction()
                Thread.sleep(12_000)
                compose.waitForIdle()
                var near: LatLon? = null
                d.onMain { near = it.locationProvider() }
                val s = d.vm.state.value
                "| $q | ${s.editing} | ${s.query} | ${s.suggestions.take(4).joinToString(" / ") { it.name + if (it.closest) " (closest)" else "" }} | " +
                    "${s.places.notFound} | ${s.places.offline} | ${s.to} | $near |"
            } catch (e: Throwable) {
                "| $q | error ${e.javaClass.simpleName}: ${e.message?.take(160)} | | | | | | |"
            } finally {
                runCatching { d.close() }
            }
            Log.i(TAG, line)
            out.append(line).append('\n')
        }
        File(target.getExternalFilesDir(null), "address_corpus.md").appendText(out.toString())
    }

    private fun slow(api: TransitApi, ms: Long): TransitApi = if (ms <= 0) api else object : TransitApi by api {
        override suspend fun geocode(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> {
            delay(ms)
            return api.geocode(text, language, near, max)
        }
    }

    companion object {
        const val TAG = "SearchUiProbe"
    }
}
