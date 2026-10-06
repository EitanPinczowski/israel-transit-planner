package il.transit.planner.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import il.transit.core.user.Appearance
import il.transit.core.user.UserSettings
import il.transit.planner.R

/**
 * Settings → Appearance (Phase 9 C6): language, theme, and the welcome tour again. Saving
 * is all this does; MainActivity applies the language and theme as soon as they are stored.
 */
@Composable
internal fun AppearanceSection(s: UserSettings, set: (UserSettings) -> Unit, showTour: () -> Unit) {
    Column {
        Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Section(R.string.app_language) {
            Appearance.LANGUAGES.forEach { l ->
                val label = when (l) {
                    null -> R.string.language_system
                    "he" -> R.string.language_he
                    else -> R.string.language_en
                }
                FilterChip(s.language == l, { set(s.copy(language = l)) }, label = { Text(stringResource(label)) })
            }
        }
        Section(R.string.app_theme) {
            Appearance.THEMES.forEach { t ->
                val label = when (t) {
                    Appearance.LIGHT -> R.string.theme_light
                    Appearance.DARK -> R.string.theme_dark
                    else -> R.string.theme_system
                }
                FilterChip(s.theme == t, { set(s.copy(theme = t)) }, label = { Text(stringResource(label)) })
            }
        }
        TextButton(onClick = showTour) { Text(stringResource(R.string.tour_again)) }
    }
}
