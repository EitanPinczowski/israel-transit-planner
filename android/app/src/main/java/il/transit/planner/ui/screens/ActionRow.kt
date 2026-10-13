package il.transit.planner.ui.screens

import android.content.Intent
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import il.transit.core.features.NavLinks
import il.transit.core.present.betterStartRow
import il.transit.core.present.dropOffRow
import il.transit.core.present.pickUpRow
import il.transit.core.present.tightTransfers
import il.transit.planner.R
import il.transit.planner.ui.AppMode
import il.transit.planner.ui.MainViewModel
import il.transit.planner.ui.ScreenActions
import il.transit.planner.ui.UiState
import il.transit.planner.ui.UiTags

/** Never smaller than this: Material's touch target, checked by the layout audit (R8). */
internal val ACTION_MIN = 48.dp

private class RowAction(val label: Int, val icon: ImageVector, val primary: Boolean, val onClick: () -> Unit)

/**
 * The selected option's main actions in one row at the bottom of the results sheet, where the
 * thumb is (Phase 10 D11): Remind me (Trip tab) or Send to driver (car tabs), then Start trip, the
 * filled one. Each button is at least [ACTION_MIN] high and shares the width, so the row never
 * scrolls or wraps; in Hebrew the order mirrors with the layout, Start trip at the far left.
 */
@Composable
internal fun ActionRow(state: UiState, actions: ScreenActions) {
    val itin = state.selectedItinerary ?: return
    if (state.loading || state.error != null) return
    val view = LocalView.current
    val context = LocalContext.current
    val canTrack = itin.firstTransitLeg != null && state.offlineSince == null
    val share = sendToDriverText(state)
    val items = buildList {
        if (state.mode == AppMode.TRIP && state.reminder == null && canTrack) {
            add(RowAction(R.string.action_remind, Icons.Default.Notifications, primary = false, onClick = actions.remind))
        }
        if (share != null) {
            add(RowAction(R.string.send_to_driver, Icons.AutoMirrored.Filled.Send, primary = false) {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, share)
                context.startActivity(Intent.createChooser(send, null))
            })
        }
        if (!state.riding && canTrack) {
            add(RowAction(R.string.action_start_trip, Icons.Default.PlayArrow, primary = true) {
                Haptics.confirm(view)
                actions.startRide()
            })
        }
    }
    if (items.isEmpty()) return
    // IntrinsicSize.Min: a label that wraps to two lines (font ×2) makes both buttons taller, never one.
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = 8.dp, end = 8.dp).testTag(UiTags.ACTIONS),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (a in items) {
            val m = Modifier.weight(1f).fillMaxHeight().heightIn(min = ACTION_MIN)
            val pad = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            val content: @Composable () -> Unit = {
                Icon(a.icon, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(a.label), textAlign = TextAlign.Center, maxLines = 2)
            }
            if (a.primary) Button(a.onClick, m, contentPadding = pad) { content() }
            else FilledTonalButton(a.onClick, m, contentPadding = pad) { content() }
        }
    }
}

/** The text "Send to driver" shares for the selected car + transit option; null in the other tabs. */
@Composable
private fun sendToDriverText(state: UiState): String? {
    val fare = state.settings.fareProfile
    return when (state.mode) {
        AppMode.BETTER_START -> state.betterStart?.let { r ->
            val o = r.options.getOrNull(state.selected) ?: return null
            val row = remember(o, r.baseline, fare) { betterStartRow(o.payload, r.baseline, fare) }
            stringResource(R.string.share_dropoff, row.stop, NavLinks.waze(o.payload.dropOffAt), NavLinks.googleMaps(o.payload.dropOffAt))
        }
        AppMode.DROP_OFF -> state.dropOff?.let { r ->
            val o = r.options.getOrNull(state.selected) ?: return null
            val row = remember(o, fare) { dropOffRow(o.payload, fare) }
            // Locals, not row.stop/row.stopAt: no smart casts across modules.
            val stop = row.stop
            val at = row.stopAt
            if (stop != null && at != null) stringResource(R.string.share_dropoff, stop, NavLinks.waze(at), NavLinks.googleMaps(at)) else null
        }
        AppMode.PICK_UP -> state.pickUp?.let { r ->
            val o = r.options.getOrNull(state.selected) ?: return null
            val row = remember(o, r.baseline, fare) { pickUpRow(o, r.baseline, fare) }
            stringResource(R.string.share_pickup, row.stop, row.pickUpTime, row.driverLeaves, NavLinks.waze(row.stopAt), NavLinks.googleMaps(row.stopAt))
        }
        else -> null
    }
}

/**
 * A tick when the selected option carries a transfer warning (D11): once per option, when it is
 * chosen or first shown with the warning, not on every refresh of the same bus.
 */
@Composable
internal fun TransferWarningTick(state: UiState) {
    val itin = state.selectedItinerary
    val warns = remember(itin, state.settings.transferBufferMin) { itin != null && tightTransfers(itin, state.settings.transferBufferMin).isNotEmpty() }
    val view = LocalView.current
    LaunchedEffect(MainViewModel.optionId(itin), warns) { if (warns) Haptics.warn(view) }
}

/**
 * The list state of a results list, scrolled to the selected card when its route is tapped on the
 * map (D11, [UiState.mapPick]). [before]: how many items the list shows above its options.
 * [fitSelected]: when new results show and the items above leave no room for the whole selected
 * card (a 360 dp phone), the list starts at that card; the items above are one scroll up.
 */
@Composable
internal fun rememberOptionsListState(state: UiState, before: Int, fitSelected: Boolean = false): LazyListState {
    val list = rememberLazyListState()
    LaunchedEffect(state.mapPick) { if (state.mapPick > 0) list.animateScrollToItem(before + state.selected) }
    if (fitSelected) {
        // Once per search (a quiet refresh keeps loading false), not on every 2-minute refresh.
        LaunchedEffect(state.loading, state.results != null || state.chain != null) {
            if (state.loading || (state.results == null && state.chain == null)) return@LaunchedEffect
            val target = before + state.selected
            val info = snapshotFlow { list.layoutInfo }.first { it.totalItemsCount > target && it.viewportEndOffset > 0 }
            val card = info.visibleItemsInfo.firstOrNull { it.index == target }
            if (card == null || card.offset + card.size > info.viewportEndOffset) list.scrollToItem(target)
        }
    }
    return list
}

/**
 * Haptic ticks (D11): pin drop, Start trip, a transfer warning. Through [View.performHapticFeedback],
 * which does nothing when the phone's touch feedback is off; screenshot tests (edit mode) skip it.
 */
object Haptics {
    fun pin(view: View) = tick(view, HapticFeedbackConstants.LONG_PRESS)

    fun confirm(view: View) = tick(view, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)

    fun warn(view: View) = tick(view, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)

    private fun tick(view: View, kind: Int) {
        if (!view.isInEditMode) view.performHapticFeedback(kind)
    }
}
