@file:OptIn(ExperimentalLayoutApi::class)

package il.transit.planner.ui.screens

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import il.transit.planner.R
import il.transit.planner.ui.AppMode
import il.transit.planner.ui.Field
import il.transit.planner.ui.MainActions
import il.transit.planner.ui.UiState
import kotlinx.coroutines.delay

/**
 * The search card while a place is being typed: only the field being edited, so the
 * suggestions get the room above the keyboard even on a 640 dp phone or in landscape.
 * The field takes focus (the keyboard opens) and the keyboard's action picks the first match.
 */
@Composable
internal fun EditingSearch(state: UiState, vm: MainActions) {
    val field = state.editing ?: return
    val focus = remember { FocusRequester() }
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQuery,
            modifier = Modifier.fillMaxWidth().padding(8.dp).focusRequester(focus),
            label = { Text(stringResource(fieldLabel(field, state.mode))) },
            placeholder = { Text(stringResource(R.string.search_hint)) },
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
    }
    // No focus target in a screenshot test: nothing to open there.
    LaunchedEffect(field) { runCatching { focus.requestFocus() } }
    CancelWhenKeyboardCloses(vm)
}

/**
 * Back with the keyboard up only closes the keyboard (the keyboard takes that Back before the
 * app sees it; Android 8 does so even where newer versions don't). Editing without a keyboard
 * is a dead end, so the keyboard closing also closes the search: one Back is enough.
 */
@Composable
private fun CancelWhenKeyboardCloses(vm: MainActions) {
    // Where the keyboard is going, not where it is: moving from one field straight to the next,
    // the last field's keyboard is still sliding away when this one asks for it.
    val visible = WindowInsets.imeAnimationTarget.getBottom(LocalDensity.current) > 0
    var wasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            wasVisible = true
        } else if (wasVisible) {
            // A hide that is undone at once (the keyboard swapping fields) is not a Back.
            delay(KEYBOARD_SETTLE_MS)
            vm.cancelEditing()
        }
    }
}

private const val KEYBOARD_SETTLE_MS = 200L

/** The label of the row being edited, as the search card shows it. */
internal fun fieldLabel(field: Field, mode: AppMode): Int = when (field) {
    Field.FROM -> R.string.from
    Field.DRIVER_TO -> R.string.driver_to
    Field.STOP -> R.string.stop_on_the_way
    Field.TO -> when (mode) {
        AppMode.DROP_OFF -> R.string.me_to
        AppMode.PICK_UP -> R.string.driver_at
        else -> R.string.to
    }
}
