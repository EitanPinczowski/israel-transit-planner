package il.transit.planner.uitest

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import il.transit.planner.MainActivity
import il.transit.planner.UiTestApp
import il.transit.planner.ui.UiTags
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
}
