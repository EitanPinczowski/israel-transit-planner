package il.transit.planner.ui

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import il.transit.core.user.Appearance
import java.util.Locale

/**
 * Settings → Appearance on the phone (Phase 9 C6). `UserSettings.language` / `.theme` are what
 * the user picked; this applies them.
 *
 * Language: on Android 13+ the framework's per-app locale ([LocaleManager], the same call
 * `AppCompatDelegate.setApplicationLocales` makes there): it persists, recreates the Activity
 * and feeds the phone's own per-app language page (`res/xml/locales_config.xml`). Below 13
 * (down to API 26) the Activity's base context is wrapped with the stored locale, as
 * AppCompat's back-port does, without moving to AppCompatActivity and its themes.
 *
 * Both choices are mirrored in plain SharedPreferences because they are needed before the
 * DataStore settings have loaded: the language in `attachBaseContext`, the theme for the
 * first frame and the map style (no light flash before a forced dark theme).
 */
object AppLocale {
    private const val PREFS = "appearance"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_THEME = "theme"

    /** The app's language now ("he", "en"), or null when it follows the phone. */
    fun current(context: Context): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val list = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (list.isEmpty) null else Appearance.normalizeLanguage(list[0].toLanguageTag())
        } else {
            Appearance.normalizeLanguage(prefs(context).getString(KEY_LANGUAGE, null))
        }

    /** Switches the app to [language] (null = the phone's) at once; nothing when already there. */
    fun apply(activity: Activity, language: String?) {
        if (current(activity) == language) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                language?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
        } else {
            prefs(activity).edit().putString(KEY_LANGUAGE, language).commit()
            activity.recreate()
        }
    }

    /** For `Activity.attachBaseContext` below Android 13: the stored language, else [base] as is. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = Appearance.normalizeLanguage(prefs(base).getString(KEY_LANGUAGE, null))
        val locale = tag?.let { Locale.forLanguageTag(it) } ?: phoneLocale()
        // Date and day names (Locale.getDefault()) follow the app too, and go back with "System".
        Locale.setDefault(locale)
        if (tag == null) return base
        val config = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(config)
    }

    /** What to send Transitous: the app's language, else the phone's. */
    fun transitLanguage(context: Context): String = Appearance.transitLanguage(current(context), phoneLocale().language)

    /** The last theme picked ("SYSTEM" until one is), for before the settings load. */
    fun theme(context: Context): String = prefs(context).getString(KEY_THEME, null) ?: Appearance.SYSTEM

    fun saveTheme(context: Context, theme: String) {
        if (theme(context) != theme) prefs(context).edit().putString(KEY_THEME, theme).apply()
    }

    private fun phoneLocale(): Locale = Resources.getSystem().configuration.locales[0]

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
