package il.transit.core.user

/**
 * "Home" is one of the saved places, chosen by name ([UserSettings.homePlace]). The night
 * refresh plans from it, and the Quick Settings tile and the evening alerts plan to it.
 */
object Home {
    /** Names offered as Home when none is set yet. */
    private val NAMES = setOf("home", "בית")

    /** The Home place, or null when none is set or the place is gone. */
    fun of(settings: UserSettings, places: List<SavedPlace>): SavedPlace? =
        settings.homePlace?.let { name -> places.firstOrNull { it.name == name } }

    /** A place called "Home" or "בית" to offer as Home, once: null after the user answered. */
    fun suggestion(settings: UserSettings, places: List<SavedPlace>): SavedPlace? =
        if (of(settings, places) != null || settings.homeOffered) null
        else places.firstOrNull { it.name.trim().lowercase() in NAMES }

    /** Settings after [deleted] was removed: Home is cleared when it was that place. */
    fun afterDelete(settings: UserSettings, deleted: SavedPlace): UserSettings =
        if (settings.homePlace == deleted.name) settings.copy(homePlace = null) else settings

    /** Settings after a place was renamed: Home follows it. */
    fun afterRename(settings: UserSettings, oldName: String, newName: String): UserSettings =
        if (settings.homePlace == oldName) settings.copy(homePlace = newName) else settings
}
