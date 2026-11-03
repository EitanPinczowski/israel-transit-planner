package il.transit.planner.uitest

import android.content.res.Configuration
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import il.transit.planner.MainActivity
import il.transit.planner.UiTestApp
import il.transit.planner.ui.AppLocale
import il.transit.planner.ui.UiTags
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.maps.MapView

/** The test build starts, shows the search card, and has not called Transitous. */
@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val permissions = Permissions.grantAll()

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as UiTestApp

    @Before fun reset() = runBlocking { app.reset() }

    @Test fun launches_with_the_search_card_and_no_network() {
        val calls = app.replay.calls.get()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            compose.onNodeWithTag(UiTags.TOP).assertExists()
            compose.onNodeWithTag(UiTags.BOTTOM).assertExists()
        }
        // The replay is the only TransitApi in this build; nothing was searched yet.
        assertEquals(calls, app.replay.calls.get())
    }

    /**
     * MapLibre's default location renderer (a GeoJSON source fed one mutable Feature) aborted the
     * process with jni::PendingJavaException; the app draws the dot with the native indicator
     * layer instead. Needs the OpenFreeMap style, so it waits for the location component.
     */
    @Test fun location_dot_is_not_drawn_from_geojson() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var fromGeoJson: Boolean? = null
            val deadline = System.currentTimeMillis() + 30_000
            while (fromGeoJson == null && System.currentTimeMillis() < deadline) {
                scenario.onActivity { fromGeoJson = it.locationFromGeoJson() }
                if (fromGeoJson == null) Thread.sleep(250)
            }
            assumeTrue("map style never loaded (no network to OpenFreeMap)", fromGeoJson != null)
            assertFalse(fromGeoJson!!)
        }
    }

    /**
     * F9 (owner's v0.10.0 bug bash on a Pixel): a long-press filled the pin panel but drew nothing,
     * because the map had no pin layer at all. A finger held on the map, in the part the panels leave
     * free, must leave a rendered pin feature at that point, still uncovered once the pin panel opens;
     * and it must still be drawn after the theme flips (the other map style) and the language flips.
     */
    @Test fun long_press_drops_a_visible_pin(): Unit = AppDriver(compose, checkA11y = false).launch().use { d ->
        val nightBefore = d.night()
        try {
            d.awaitController()
            val (x, y) = d.freeMapPoint()
            d.longPressMap(x, y)
            d.awaitPinDrawn("after a long-press at ($x, $y)")
            // The pin card opened (and the search card resized): the pin must not be under either; the map
            // moves it into view when it would be (a small phone's pin card covers the lower half, F9).
            d.awaitPinUncovered()
            d.awaitPinDrawn("after the pin was kept in view")

            // The other theme: a new map style, so a new controller; the pin is pushed again.
            d.shell("cmd uimode night ${if (nightBefore) "no" else "yes"}")
            d.afterConfigChange()
            d.awaitPinDrawn("after the theme flipped (night=${!nightBefore})")

            // The other language (Hebrew <-> English): the Activity is recreated, the pin stays.
            var lang: String? = null
            d.inst.runOnMainSync {
                val now = d.activity.resources.configuration
                lang = if (now.locales[0].language in setOf("iw", "he")) "en" else "iw"
            }
            d.inst.runOnMainSync { AppLocale.apply(d.activity, lang) }
            d.afterConfigChange()
            d.awaitPinDrawn("after the language flipped to $lang")
        } finally {
            d.inst.runOnMainSync { AppLocale.apply(d.activity, null) }
            d.shell("cmd uimode night ${if (nightBefore) "yes" else "no"}")
        }
    }

    private fun AppDriver.night(): Boolean {
        var n = false
        inst.runOnMainSync { n = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES }
        return n
    }

    /** Until the map style has loaded (the uitest build's local blank style) and the controller exists. */
    private fun AppDriver.awaitController() {
        val until = SystemClock.uptimeMillis() + 20_000
        var ready = false
        while (!ready && SystemClock.uptimeMillis() < until) {
            inst.runOnMainSync { ready = activity.mapReadyForTest() }
            if (!ready) SystemClock.sleep(200)
        }
        assertTrue("the map style never loaded", ready)
    }

    private fun AppDriver.afterConfigChange() {
        SystemClock.sleep(1_500)
        refreshActivity()
        compose.waitForIdle()
        awaitController()
    }

    /** Until the open pin's point is in the map between the panels (the camera may ease it there). */
    private fun AppDriver.awaitPinUncovered() {
        val until = SystemClock.uptimeMillis() + 5_000
        var last = ""
        while (SystemClock.uptimeMillis() < until) {
            compose.waitForIdle()
            val bottom = compose.onNode(hasTestTag(UiTags.BOTTOM)).fetchSemanticsNode().boundsInWindow
            val top = compose.onNode(hasTestTag(UiTags.TOP)).fetchSemanticsNode().boundsInWindow
            var p: android.graphics.PointF? = null
            inst.runOnMainSync { p = activity.pinInWindow() }
            val at = checkNotNull(p) { "no pin on the map" }
            val covered = (at.y >= bottom.top && at.x in bottom.left..bottom.right) || (at.y <= top.bottom && at.x in top.left..top.right)
            if (!covered) return
            last = "the pin at (${at.x.toInt()}, ${at.y.toInt()}) is under a panel (top $top, bottom $bottom)"
            SystemClock.sleep(200)
        }
        throw AssertionError(last)
    }

    /** Until the open pin is rendered at its point; fails with [what] if it never is. */
    private fun AppDriver.awaitPinDrawn(what: String) {
        val until = SystemClock.uptimeMillis() + 10_000
        var drawn: Boolean? = null
        while (drawn != true && SystemClock.uptimeMillis() < until) {
            // The test rule drives Compose's frames: without this the pin effect never runs (F9, API 26-35).
            compose.waitForIdle()
            inst.runOnMainSync { drawn = activity.pinOnMap() }
            if (drawn != true) SystemClock.sleep(200)
        }
        assertNotNull("$what: no pin was set (the long-press did not reach the app)", drawn)
        var report = ""
        if (drawn != true) inst.runOnMainSync { report = activity.pinReport() }
        assertTrue("$what: the pin is set but not drawn on the map: $report", drawn == true)
    }

    /**
     * A point of the window the panels leave free: a third of the way down the band between the
     * search card and the bottom sheet (phones), or the middle of the map beside a side column.
     */
    private fun AppDriver.freeMapPoint(): Pair<Int, Int> {
        val top = compose.onNode(hasTestTag(UiTags.TOP)).fetchSemanticsNode().boundsInWindow
        val bottom = compose.onNode(hasTestTag(UiTags.BOTTOM)).fetchSemanticsNode().boundsInWindow
        var w = 0
        inst.runOnMainSync { w = activity.window.decorView.width }
        return if (top.width < w * 0.6f) {
            // A side column (left in English, right in Hebrew): the middle of the rest.
            val x = if (top.left > w / 2f) top.left / 2f else (top.right + w) / 2f
            x.toInt() to ((top.top + bottom.top) / 2).toInt()
        } else {
            ((top.left + top.right) / 2).toInt() to (top.bottom + (bottom.top - top.bottom) / 3).toInt()
        }
    }

    /** A finger held 1.2 s at window point ([x], [y]), sent to the MapView itself (injected input does not reach the API 35 image, F6). */
    private fun AppDriver.longPressMap(x: Int, y: Int) {
        var map: MapView? = null
        fun walk(v: View) {
            if (v is MapView) map = v
            if (v is ViewGroup) for (c in 0 until v.childCount) walk(v.getChildAt(c))
        }
        inst.runOnMainSync { walk(activity.window.decorView) }
        val v = checkNotNull(map) { "no MapView on screen" }
        val at = IntArray(2)
        inst.runOnMainSync { v.getLocationInWindow(at) }
        val mx = (x - at[0]).toFloat()
        val my = (y - at[1]).toFloat()
        val down = SystemClock.uptimeMillis()
        inst.runOnMainSync { v.dispatchTouchEvent(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, mx, my, 0)) }
        SystemClock.sleep(1_200)
        inst.runOnMainSync { v.dispatchTouchEvent(MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, mx, my, 0)) }
    }
}
