@file:OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)

package il.transit.planner.ui.screens

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import il.transit.planner.R
import il.transit.planner.ui.AppMode
import il.transit.planner.ui.MainActions
import il.transit.planner.ui.UiState
import kotlinx.coroutines.delay

/**
 * The text field the tapped row becomes ([PlaceRow] shows it in place of its value while that
 * row is being edited). It takes focus, so the keyboard opens; the keyboard's action picks the
 * first match. Null when nothing is typed (and in screenshots of the resting card).
 */
val LocalPlaceEditor = compositionLocalOf<(@Composable (Modifier) -> Unit)?> { null }

/** A short window (landscape, a 640 dp phone): while typing, the card keeps only the place rows. */
val LocalCompactEditing = compositionLocalOf { false }

@Composable
internal fun PlaceEditor(state: UiState, vm: MainActions, modifier: Modifier) {
    val field = state.editing ?: return
    val focus = remember { FocusRequester() }
    var hadFocus by remember(field) { mutableStateOf(false) }
    OutlinedTextField(
        value = state.query,
        onValueChange = vm::onQuery,
        modifier = modifier.focusRequester(focus)
            .onFocusChanged { f -> if (f.isFocused) hadFocus = true else if (hadFocus) vm.cancelEditing() }
            // Back reaches neither the screen's BackHandler (a focused field takes it) nor the
            // app at all when the keyboard is up (the keyboard takes it to hide itself). This
            // hook sees it before both, keyboard or not: one Back closes the search.
            // Known issue: not on Android 8 (ROADMAP Phase 9 close-out, J4 on API 26).
            .onPreInterceptKeyBeforeSoftKeyboard { e -> backClosesSearch(e, vm) }
            .onPreviewKeyEvent { e -> backClosesSearch(e, vm) },
        placeholder = { Text(stringResource(R.string.search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { state.suggestions.firstOrNull()?.let(vm::pick) }),
        trailingIcon = {
            IconButton(onClick = vm::cancelEditing) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
            }
        },
    )
    // No focus target in a screenshot test: nothing to open there.
    LaunchedEffect(field) { runCatching { focus.requestFocus() } }
    CancelWhenKeyboardCloses(vm)
}

/**
 * Back with the keyboard up only closes the keyboard (the keyboard takes that Back before the
 * app sees it), and a focused field may take a Back itself to drop its focus. Typing without
 * a keyboard or focus is a dead end, so either one ends the search: one Back is enough.
 *
 * Armed only once THIS field's keyboard has come up: moving from one field straight to the
 * next, the last field's keyboard may still be on screen (or sliding away) when this one
 * starts, and that keyboard leaving is not a Back.
 */
@Composable
private fun CancelWhenKeyboardCloses(vm: MainActions) {
    val visible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var seenHidden by remember { mutableStateOf(!visible) }
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        when {
            !visible && armed -> {
                // A hide that is undone at once (the keyboard swapping fields) is not a Back.
                delay(KEYBOARD_SETTLE_MS)
                vm.cancelEditing()
            }
            !visible -> seenHidden = true
            seenHidden -> armed = true
        }
    }
}

private const val KEYBOARD_SETTLE_MS = 200L

private fun backClosesSearch(e: KeyEvent, vm: MainActions): Boolean {
    if (e.key != Key.Back) return false
    if (e.type == KeyEventType.KeyUp) vm.cancelEditing()
    return true
}

/** The mode chips' labels: shorter than the names used elsewhere, so the five fit in two lines. */
internal fun modeTabLabel(mode: AppMode): Int = when (mode) {
    AppMode.TRIP -> R.string.tab_trip
    AppMode.BETTER_START -> R.string.tab_better_start
    AppMode.DROP_OFF -> R.string.tab_drop_off
    AppMode.PICK_UP -> R.string.tab_pick_up
    AppMode.PARK_RIDE -> R.string.tab_park_ride
}
