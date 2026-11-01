package il.transit.planner.uitest

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import il.transit.core.geo.LatLon
import il.transit.core.plan.MissedIt
import il.transit.core.remind.Reminder
import il.transit.core.user.UserSettings
import il.transit.planner.MainActivity
import il.transit.planner.UiTestApp
import il.transit.planner.remind.MissedItAction
import il.transit.planner.remind.Notifications
import il.transit.planner.tile.NextTripTile
import il.transit.planner.ui.PlaceRef
import il.transit.planner.ui.Shortcuts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.test.junit4.createEmptyComposeRule

/**
 * Phase 11 T6: hostile intents to the exported `MainActivity` (its SEND and VIEW filters, and the extras
 * its own notifications, tile and shortcuts use), sent from the test as an explicit intent, which reaches
 * the Activity whatever its filters say. The app answers from the replay (0 requests to the internet);
 * `replay.calls` counts what it would have sent.
 *
 * Every case: the Activity comes up and stays up (a crash fails the run), nothing saved changes, and input
 * the app refuses sends **0** requests. Shared text that is searched (as if typed) sends at most the one
 * geocode of a typed search. `tools/ui_intent_fuzz.sh` sends the same kinds from `adb shell am`, plus the
 * spoofed boot broadcast and the tile.
 */
@RunWith(AndroidJUnit4::class)
class IntentFuzzTest {
    @get:Rule val compose = createEmptyComposeRule()

    @get:Rule val permissions = Permissions.grantAll()

    private fun main(ctx: android.content.Context) = Intent().setComponent(ComponentName(ctx, MainActivity::class.java))

    /**
     * Starts the Activity from [make]'s intent on a clean slate, waits past the typing pause, and
     * checks the promises above. [maxRequests]: 0 for refused input. Returns the driver, still open.
     */
    private fun case(name: String, maxRequests: Int = 0, check: (AppDriver) -> Unit = {}, make: (android.content.Context) -> Intent) {
        AppDriver(compose).use { d ->
            val before = d.requests()
            val places = d.places()
            val started = SystemClock.elapsedRealtime()
            d.launch(make(d.inst.targetContext))
            // Past the typing pause (350 ms) and a geocode's answer.
            SystemClock.sleep(2_000)
            compose.waitForIdle()
            val ms = SystemClock.elapsedRealtime() - started
            val sent = d.requests() - before
            Log.i(TAG, "$name -> sent=$sent cap=$maxRequests ${ms}ms to=${d.ui.to} query=${d.ui.query.take(40)}")
            assertEquals("$name: the Activity is up", Lifecycle.State.RESUMED, d.scenarioState)
            assertTrue("$name: $sent requests (cap $maxRequests)", sent <= maxRequests)
            assertEquals("$name: saved places unchanged", places, d.places())
            // A hung main thread would have kept the Activity from settling: 15 s is far past a normal start.
            assertTrue("$name: took $ms ms", ms < 15_000)
            check(d)
        }
    }

    /** Refused: no destination set, nothing searched. */
    private val refused: (AppDriver) -> Unit = { d ->
        val s = d.ui
        assertEquals(null, s.to)
        assertEquals("", s.query)
    }

    private fun send(text: String?, type: String = "text/plain"): (android.content.Context) -> Intent = { c ->
        main(c)
            .setAction(Intent.ACTION_SEND)
            .setType(type)
            .apply { if (text != null) putExtra(Intent.EXTRA_TEXT, text) }
    }

    private fun view(uri: String): (android.content.Context) -> Intent = { c ->
        main(c).setAction(Intent.ACTION_VIEW).setData(Uri.parse(uri))
    }

    @Test fun send_without_text_or_with_the_wrong_type() {
        case("SEND no extras", check = refused, make = send(null))
        case("SEND text as an int", check = refused) { c -> send(null)(c).putExtra(Intent.EXTRA_TEXT, 42) }
        case("SEND empty", check = refused, make = send(""))
        case("SEND image/png + stream", check = refused) { c ->
            send(null, "image/png")(c).putExtra(Intent.EXTRA_STREAM, Uri.parse("content://media/external/images/media/1"))
        }
        case("no action, no data", check = refused) { c -> main(c) }
        case("unknown action with data", check = refused) { c -> main(c).setAction("il.example.EVIL").setData(Uri.parse("geo:32.08,34.79")) }
        case("VIEW without data", check = refused) { c -> main(c).setAction(Intent.ACTION_VIEW) }
    }

    /** The most text one intent carries (a binder transaction is ~1 MB): parsed in well under a second. */
    @Test fun send_huge_text() {
        val text = "שלום ".repeat(48_000) // 240 000 chars, ~480 KB in the parcel
        case("SEND 240k chars", check = refused, make = send(text))
        case("SEND 240k chars after a street", maxRequests = 1, make = send("רחוב הרצל 15 תל אביב\n" + "x".repeat(240_000)))
    }

    @Test fun view_broken_or_foreign_schemes() {
        for (uri in listOf(
            "geo:NaN,NaN", "geo:999,999", "geo:-91,200", "geo:", "geo:0,0",
            "javascript:alert(document.cookie)",
            "file:///data/data/il.transit.planner.uitest/files/crash_log.txt",
            "file:///sdcard/Download/x.html",
            "content://com.android.providers.settings/secure/android_id",
            "intent://maps.google.com/#Intent;scheme=https;end",
            "data:text/html,<script>alert(1)</script>",
            "https://maps.google.com.evil.example/maps?q=32.0839,34.7983",
            "https://maps.google.com.evil.example/maps?q=Dizengoff",
            "https://evil.example/@32.0839,34.7983",
        )) {
            case("VIEW $uri", check = refused, make = view(uri))
        }
    }

    /** Another app's private file, with a read grant flag on top: the app never opens a shared URI. */
    @Test fun content_uri_with_a_grant_flag() {
        case("VIEW content:// + grant", check = refused) { c ->
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            view("content://com.android.externalstorage.documents/document/primary%3ADownload%2Fsecret.txt")(c).addFlags(flags)
        }
        case("SEND content:// as text + grant", maxRequests = 1) { c ->
            send("content://com.android.contacts/contacts/1")(c).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Markup, SQL and bidi overrides are plain text: searched as typed (≤ 1 request), shown as sent, overrides dropped. */
    @Test fun geo_with_markup_sql_and_rtl_override() {
        for (q in listOf("'; DROP TABLE places;--", "<script>alert(1)</script>", "<b>הרצל</b> 15")) {
            case(
                "geo q=$q",
                maxRequests = 1,
                check = { d -> assertEquals(q, d.ui.query) },
                make = view("geo:0,0?q=" + Uri.encode(q)),
            )
        }
        val rlo = '\u202E'
        val label: (AppDriver) -> Unit = { d ->
            val to = d.ui.to as? PlaceRef.Point
            assertNotNull("a point at Tel Aviv", to)
            assertEquals("Homegpj.exe", to!!.name)
        }
        case("geo label with RLO", check = label, make = view("geo:0,0?q=" + Uri.encode("32.0839,34.7983(Home${rlo}gpj.exe)")))
        val plain: (AppDriver) -> Unit = { d -> assertFalse(d.ui.query.any { it in '\u202A'..'\u202E' || it in '\u2066'..'\u2069' }) }
        case("geo query with RLO", maxRequests = 1, check = plain, make = view("geo:0,0?q=" + Uri.encode("${rlo}דיזנגוף 50")))
    }

    /** The extras of our own notifications, tile and shortcuts, sent by someone else, broken. */
    @Test fun own_extras_broken() {
        val tel = LatLon(UiTestApp.TEL_AVIV.lat, UiTestApp.TEL_AVIV.lon)
        fun missed(c: android.content.Context, lat: Double, lon: Double) =
            MissedItAction.intent(c, MissedIt.Target("x", tel)).apply {
                putExtra("missed_it_lat", lat)
                putExtra("missed_it_lon", lon)
            }
        for ((lat, lon) in listOf(999.0 to 999.0, Double.NaN to 34.79, 0.0 to 0.0, 48.85 to 2.35, Double.POSITIVE_INFINITY to 34.8)) {
            case("missed it at $lat,$lon", check = refused) { c -> missed(c, lat, lon) }
        }
        case("missed it, lat as a string", check = refused) { c -> missed(c, 32.0, 34.0).putExtra("missed_it_lat", "32.08") }
        // A reminder the user set must outlive a broken "missed it" from another app.
        AppDriver(compose).use { d ->
            val at = UiTestApp.NOW.epochSecond
            val r = Reminder(
                fromLat = UiTestApp.BGU.lat,
                fromLon = UiTestApp.BGU.lon,
                toLat = tel.lat,
                toLon = tel.lon,
                settings = UserSettings(tourSeen = true),
                tripId = null,
                line = "T6",
                boardStop = "T6",
                scheduledBoardingEpoch = at + 3_900,
                leaveAtEpoch = at + 3_600,
            )
            runBlocking { d.app.store.setReminder(r) }
            d.launch(missed(d.inst.targetContext, 999.0, 999.0))
            SystemClock.sleep(1_000)
            assertEquals(r, d.reminder())
        }
        case("start ride, no reminder") { c -> main(c).putExtra(Notifications.EXTRA_START_RIDE, true) }
        case("trip home flag as a string") { c -> main(c).putExtra(NextTripTile.EXTRA_TRIP_HOME, "true") }
        case("shortcut to an unknown place", check = refused) { c -> main(c).putExtra(Shortcuts.EXTRA_PLACE, "x".repeat(100_000)) }
        case("shortcut with an unknown name", check = refused) { c -> main(c).putExtra(Shortcuts.EXTRA_SHORTCUT, "../../evil") }
    }

    private val AppDriver.ui get() = vm.state.value

    private fun AppDriver.requests() = app.replay.calls.get()

    private fun AppDriver.places() = runBlocking { app.store.places.first() }

    private fun AppDriver.reminder() = runBlocking { app.store.reminder.first() }

    companion object {
        const val TAG = "IntentFuzz"
    }
}
