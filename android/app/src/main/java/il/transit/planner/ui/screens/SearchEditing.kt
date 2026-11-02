@file:OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)

package il.transit.planner.ui.screens

import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import il.transit.core.search.StopCode
import il.transit.planner.R
import il.transit.planner.ui.AppMode
import il.transit.planner.ui.MainActions
import il.transit.planner.ui.UiState
import il.transit.planner.ui.rememberVoiceSearch
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
    // Voice (Phase 10 D6): what was heard is typed into the box and searched like typing.
    val voice = rememberVoiceSearch(vm::onQuery)
    // A stop number no stop on the phone carries (D6): say so under the box; the list offers the map.
    val missingStop = StopCode.of(state.query)?.takeIf { state.places.notFound }
    val user = rememberUserPresent()
    OutlinedTextField(
        value = state.query,
        onValueChange = vm::onQuery,
        modifier = modifier.focusRequester(focus)
            .onFocusChanged { f ->
                if (f.isFocused) {
                    hadFocus = true
                } else if (hadFocus && user()) {
                    vm.cancelEditing()
                }
            }
            // Back reaches neither the screen's BackHandler (a focused field takes it) nor the
            // app at all when the keyboard is up (the keyboard takes it to hide itself). This
            // hook sees it before both, keyboard or not: one Back closes the search.
            .onPreInterceptKeyBeforeSoftKeyboard { e -> backClosesSearch(e, vm) }
            .onPreviewKeyEvent { e -> backClosesSearch(e, vm) },
        placeholder = { Text(stringResource(R.string.search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { state.suggestions.firstOrNull()?.let(vm::pick) }),
        // The mic while the box is empty, ✕ once something is typed: two icons would leave a small
        // phone's field (~200 dp beside its label) almost no room to type. Back, or tapping away,
        // still closes an empty search.
        trailingIcon = {
            if (voice != null && state.query.isEmpty()) {
                IconButton(onClick = voice) {
                    Icon(painterResource(R.drawable.ic_mic), contentDescription = stringResource(R.string.voice_search))
                }
            } else {
                IconButton(onClick = vm::cancelEditing) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                }
            }
        },
        supportingText = if (missingStop != null) {
            { Text(stringResource(R.string.stop_code_missing, missingStop)) }
        } else {
            null
        },
    )
    // No focus target in a screenshot test: nothing to open there.
    LaunchedEffect(field) { runCatching { focus.requestFocus() } }
    CancelWhenKeyboardCloses(vm, user)
}

/**
 * Whether a lost focus or a closed keyboard is the user's doing (tapping away, Back) and should
 * close the search: only while the screen is resumed. Rotating the phone, a theme or language
 * switch, or leaving the app pauses the Activity first; its field then loses focus and its
 * keyboard goes as the old window is torn down, and that must not clear the typed text (Phase 11
 * F8, T9's rotation finding). The ViewModel keeps the search across the re-creation, and
 * `SavedUi` across process death.
 */
@Composable
private fun rememberUserPresent(): () -> Boolean {
    val owner = LocalContext.current.lifecycleOwner()
    return remember(owner) { { owner == null || owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) } }
}

private tailrec fun Context.lifecycleOwner(): LifecycleOwner? = when (this) {
    is LifecycleOwner -> this
    is ContextWrapper -> baseContext.lifecycleOwner()
    else -> null
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
private fun CancelWhenKeyboardCloses(vm: MainActions, user: () -> Boolean) {
    val visible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var seenHidden by remember { mutableStateOf(!visible) }
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        when {
            !visible && armed -> {
                // A hide that is undone at once (the keyboard swapping fields) is not a Back.
                delay(KEYBOARD_SETTLE_MS)
                if (user()) vm.cancelEditing()
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

/** The mode tabs' names (ModeTabs.kt): shorter than the names used elsewhere, so they fit under the icons. */
internal fun modeTabLabel(mode: AppMode): Int = when (mode) {
    AppMode.TRIP -> R.string.tab_trip
    AppMode.BETTER_START -> R.string.tab_better_start
    AppMode.DROP_OFF -> R.string.tab_drop_off
    AppMode.PICK_UP -> R.string.tab_pick_up
    AppMode.PARK_RIDE -> R.string.tab_park_ride
}
