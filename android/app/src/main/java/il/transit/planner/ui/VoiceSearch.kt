package il.transit.planner.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import il.transit.planner.R

/**
 * Voice search (Phase 10 D6): the phone's own speech service (`ACTION_RECOGNIZE_SPEECH`) in the app
 * language, and the words go into the search box as if typed. 0 requests of ours. The speech goes to
 * that service (Google's on most phones), which the first tap says once.
 */
object VoiceSearch {
    private const val PREFS = "ways_in"
    private const val TOLD = "voice_told"

    /** Some app handles the intent (API 30+ sees it only through the manifest's `<queries>`). */
    fun available(context: Context): Boolean =
        runCatching { context.packageManager.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty() }.getOrDefault(false)

    /** "he-IL" for a Hebrew app ("iw" on old phones), else "en-US". */
    fun tag(language: String): String = if (language == "he" || language == "iw") "he-IL" else "en-US"

    /** True the first time only: the privacy line is shown once. */
    fun firstUse(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(TOLD, false)) return false
        prefs.edit().putBoolean(TOLD, true).apply()
        return true
    }
}

/** Screenshots: whether the mic shows (no package manager answers there). Null = ask the phone. */
val LocalVoiceAvailable = staticCompositionLocalOf<Boolean?> { null }

/**
 * Starts listening; null when no app on the phone can (the mic is hidden then). [onText] gets
 * what was heard.
 */
@Composable
fun rememberVoiceSearch(onText: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val available = LocalVoiceAvailable.current ?: remember { VoiceSearch.available(context) }
    if (!available) return null
    val language = LocalConfiguration.current.locales[0].language
    // No result registry (a screenshot test): the mic shows and does nothing.
    if (LocalActivityResultRegistryOwner.current == null) return {}
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val heard = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        if (r.resultCode == Activity.RESULT_OK && !heard.isNullOrEmpty()) onText(heard)
    }
    return {
        if (VoiceSearch.firstUse(context)) Toast.makeText(context, R.string.voice_privacy, Toast.LENGTH_LONG).show()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, VoiceSearch.tag(language))
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        // The service may vanish between the check and the tap: nothing happens then.
        runCatching { launcher.launch(intent) }
    }
}
