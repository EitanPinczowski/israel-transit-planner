package il.transit.planner.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import il.transit.planner.R
import il.transit.planner.ui.AppMode
import kotlinx.coroutines.launch

/** One page of the tour: a mode on the top bar, or (mode null) saved places and reminders. */
internal data class TourPage(val mode: AppMode?, val title: Int, val what: Int, val whenToUse: Int, val extra: List<Int> = emptyList())

/** The modes in top-bar order, then saved places and reminders (Phase 9 C6). */
internal val TOUR_PAGES: List<TourPage> = AppMode.entries.map { m ->
    when (m) {
        AppMode.TRIP -> TourPage(m, modeTabLabel(m), R.string.tour_trip_what, R.string.tour_trip_when)
        AppMode.BETTER_START -> TourPage(m, modeTabLabel(m), R.string.tour_better_start_what, R.string.tour_better_start_when)
        AppMode.DROP_OFF -> TourPage(m, modeTabLabel(m), R.string.tour_drop_off_what, R.string.tour_drop_off_when)
        AppMode.PICK_UP -> TourPage(m, modeTabLabel(m), R.string.tour_pick_up_what, R.string.tour_pick_up_when)
        AppMode.PARK_RIDE -> TourPage(m, modeTabLabel(m), R.string.tour_park_ride_what, R.string.tour_park_ride_when)
    }
} + TourPage(
    null, R.string.tour_saved_title, R.string.tour_saved_what, R.string.tour_saved_when,
    extra = listOf(R.string.tour_saved_tile, R.string.tour_saved_last_trip),
)

internal const val TOUR_TAG = "welcome_tour"

/**
 * The first-run tour over the whole screen: one page per mode, swiped or stepped with
 * "Next", skippable at any page. [onFinish] runs on Skip, Done and Back; the caller marks the
 * tour seen. The pager follows the layout direction, so in Hebrew the next page comes from
 * the left, like turning a Hebrew book's page.
 */
@Composable
internal fun WelcomeTour(onFinish: () -> Unit, startPage: Int = 0) {
    if (LocalOnBackPressedDispatcherOwner.current != null) BackHandler(onBack = onFinish)
    val pager = rememberPagerState(initialPage = startPage) { TOUR_PAGES.size }
    WelcomeTourContent(pager, onFinish)
}

/** The tour without [BackHandler], so screenshot tests can draw any page. */
@Composable
internal fun WelcomeTourContent(pager: PagerState, onFinish: () -> Unit) {
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == TOUR_PAGES.lastIndex
    Surface(Modifier.fillMaxSize().testTag(TOUR_TAG), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!last) TextButton(onClick = onFinish) { Text(stringResource(R.string.tour_skip)) }
                else Spacer(Modifier.height(48.dp))
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { i -> TourPageView(TOUR_PAGES[i]) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PageDots(pager.currentPage, TOUR_PAGES.size, Modifier.weight(1f))
                Button(onClick = { if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                    Text(stringResource(if (last) R.string.tour_done else R.string.tour_next))
                }
            }
        }
    }
}

@Composable
private fun TourPageView(p: TourPage) {
    // Scrolls rather than clips: big text on a small phone in landscape.
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 480.dp).padding(horizontal = 8.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(96.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val tint = MaterialTheme.colorScheme.onSecondaryContainer
                if (p.mode != null) Icon(painterResource(modeIcon(p.mode)), null, Modifier.size(48.dp), tint = tint)
                else Icon(Icons.Filled.Star, null, Modifier.size(48.dp), tint = tint)
            }
            Text(stringResource(p.title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(stringResource(p.what), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Text(
                stringResource(p.whenToUse),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            p.extra.forEach { r ->
                Text(
                    "• " + stringResource(r),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PageDots(current: Int, count: Int, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.tour_page, current + 1, count)
    Row(
        modifier.semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val on = i == current
            Box(
                Modifier
                    .clearAndSetSemantics {}
                    .size(if (on) 10.dp else 8.dp)
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
        }
    }
}
