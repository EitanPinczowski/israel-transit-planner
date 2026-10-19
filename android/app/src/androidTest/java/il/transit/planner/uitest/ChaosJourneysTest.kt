package il.transit.planner.uitest

import android.util.Log
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import il.transit.core.remind.ProblemKind
import il.transit.core.remind.ReminderHealth
import il.transit.core.user.UserSettings
import il.transit.planner.UiTestApp.Companion.BGU
import il.transit.planner.UiTestApp.Companion.TEL_AVIV
import il.transit.planner.remind.ReminderCheck
import il.transit.planner.ui.Field
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Phase 11 T7, the nightly chaos steps (`tools/ui_chaos_run.sh`): the phone is made hostile by the
 * shell (storage full, clock moved, app parked in a standby bucket) and these tests are the app's
 * side. Each logs under [TAG] what it saw and fails only on a broken promise.
 */
@RunWith(AndroidJUnit4::class)
class ChaosJourneysTest {
    @get:Rule val compose = createEmptyComposeRule()

    @get:Rule val permissions = Permissions.grantAll()

    private fun say(msg: String) = Log.i(TAG, msg)

    /** The standby bucket the shell put the app in: E1's check must say so, not stay silent. */
    @Test fun check_reports_the_restriction() = AppDriver(compose).use { d ->
        val state = ReminderCheck.state(d.app, UserSettings())
        val kinds = ReminderHealth.check(state).map { it.kind }
        say("standby: bucket=${state.standbyBucket} problems=$kinds")
        val bucket = state.standbyBucket ?: return@use
        if (bucket >= RESTRICTED_BUCKET) {
            assertTrue(
                "the app is in the restricted bucket ($bucket) and the check says $kinds",
                ProblemKind.STANDBY_RESTRICTED in kinds || ProblemKind.BACKGROUND_RESTRICTED in kinds,
            )
        }
    }

    /**
     * The phone's clock is wrong (the shell moved it by +3 h or −1 day) while the app's own clock
     * is fixed: search, set a reminder, open the check. No crash, and the reminder's leave time is
     * a time, not a negative countdown. What the screens show is logged for the report.
     */
    @Test fun clock_skew() = AppDriver(compose).launch().use { d ->
        d.place(Field.FROM, BGU)
        d.place(Field.TO, TEL_AVIV)
        d.awaitSearch()
        assertTrue("no results under a skewed clock", d.vm.state.value.options.isNotEmpty())
        var armed = false
        d.onMain { armed = it.remindSelected() }
        val r = d.vm.state.value.reminder
        say("skew: phone now=${Instant.now()} armed=$armed leaveAt=${r?.leaveAt} offline=${d.vm.state.value.offlineSince}")
        if (r != null) assertTrue("leave time before the epoch", r.leaveAt.epochSecond > 0)
        val result = ReminderCheck.result(d.app, Instant.now())
        say("skew: test reminder reads $result")
        d.onMain { it.cancelReminder() }
    }

    /**
     * Storage nearly full (the shell filled /data to < 50 MB free): saving a place and a search
     * (which writes the plan cache) must not crash, and the saved places file must still decode,
     * with the places it had before.
     */
    @Test fun low_storage() = AppDriver(compose).launch().use { d ->
        val before = runBlocking { d.app.store.places.first() }
        d.place(Field.FROM, BGU)
        d.place(Field.TO, TEL_AVIV)
        d.awaitSearch()
        assertTrue("no results with a full disk", d.vm.state.value.options.isNotEmpty())
        d.onMain { it.savePlace("Chaos", TEL_AVIV.latLon) }
        android.os.SystemClock.sleep(3_000)
        compose.waitForIdle()
        val after = runBlocking { d.app.store.places.first() }
        say("storage: places before=${before.size} after=${after.size}")
        assertTrue("the old places no longer decode: $before -> $after", before.all { b -> after.any { it.name == b.name } })
        assertEquals("a half-written file", true, after.isNotEmpty())
    }

    private companion object {
        const val TAG = "ChaosJourney"

        /** `UsageStatsManager.STANDBY_BUCKET_RESTRICTED`. */
        const val RESTRICTED_BUCKET = 45
    }
}
