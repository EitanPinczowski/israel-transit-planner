package il.transit.planner.ui

import il.transit.core.geo.LatLon
import il.transit.core.plan.ChainPlanner
import il.transit.core.plan.ChainStop
import il.transit.core.plan.TimeMode
import java.time.Instant

/**
 * What survives Android killing the app in the background (Phase 10 D11): the search, the tab,
 * the chosen option and the open sheet. Small values only, as `SavedStateHandle` keeps them
 * (the Activity's saved bundle); the options themselves come back from the cache entry named by
 * [cacheKey] (the Trip cache, or since Phase 11 R1 the car tabs' and errands' one), so restoring
 * costs no request.
 *
 * Plain Kotlin on purpose: [write] and [read] take the handle's set/get, so the round trip is a
 * JVM test (`SavedUiTest`).
 */
data class SavedUi(
    val mode: AppMode = AppMode.TRIP,
    val from: PlaceRef = PlaceRef.MyLocation,
    val to: PlaceRef? = null,
    val driverTo: PlaceRef? = null,
    val timeMode: TimeMode = TimeMode.NOW,
    val time: Instant? = null,
    val selected: Int = 0,
    /** The selected option's times (`MainViewModel.optionId`): the cache drops options already gone, so an index alone could point at another one. */
    val optionId: String? = null,
    /** The cache entry of the shown options (`PlanCache.key`, its tab first: [keyPrefix]); null = nothing to bring back. */
    val cacheKey: String? = null,
    val sheet: Sheet? = null,
    /** Errands' stops on the way (R1): inputs, like the places; at most [ChainPlanner.MAX_STOPS]. */
    val stops: List<ChainStop> = emptyList(),
    /** The car tabs' sliders (R1), so restored options sit under the limit they were found with. */
    val maxDriveMin: Int = UiState().maxDriveMin,
    val maxDetourMin: Int = UiState().maxDetourMin,
    val maxPickUpDriveMin: Int = UiState().maxPickUpDriveMin,
    val maxParkRideMin: Int = ParkRideUi().maxDriveMin,
) {
    /** The sheet over the results: a ride of the selected option, or a stop's departures. */
    sealed interface Sheet {
        data class Ride(val legIndex: Int) : Sheet
        data class Stop(val stopId: String, val name: String) : Sheet
    }

    /** Every key is written, null included, so a value cleared since the last save is cleared there too. */
    fun write(set: (String, Any?) -> Unit) {
        set(MODE, mode.name)
        writePlace(set, FROM, from)
        writePlace(set, TO, to)
        writePlace(set, DRIVER_TO, driverTo)
        set(TIME_MODE, timeMode.name)
        set(TIME, time?.toEpochMilli())
        set(SELECTED, selected)
        set(OPTION, optionId)
        set(CACHE_KEY, cacheKey)
        set(RIDE_LEG, (sheet as? Sheet.Ride)?.legIndex)
        set(STOP_ID, (sheet as? Sheet.Stop)?.stopId)
        set(STOP_NAME, (sheet as? Sheet.Stop)?.name)
        for (i in 0 until ChainPlanner.MAX_STOPS) {
            val c = stops.getOrNull(i)
            writePlace(set, "$ERRAND$i", c?.let { PlaceRef.Point(it.name, it.at) })
            set("$ERRAND$i.stay", c?.stayMin)
        }
        set(MAX_DRIVE, maxDriveMin)
        set(MAX_DETOUR, maxDetourMin)
        set(MAX_PICK_UP, maxPickUpDriveMin)
        set(MAX_PARK_RIDE, maxParkRideMin)
    }

    companion object {
        private const val P = "d11."
        private const val MODE = P + "mode"
        private const val FROM = P + "from"
        private const val TO = P + "to"
        private const val DRIVER_TO = P + "driverTo"
        private const val TIME_MODE = P + "timeMode"
        private const val TIME = P + "time"
        private const val SELECTED = P + "selected"
        private const val OPTION = P + "option"
        private const val CACHE_KEY = P + "cacheKey"
        private const val RIDE_LEG = P + "rideLeg"
        private const val STOP_ID = P + "stopId"
        private const val STOP_NAME = P + "stopName"
        private const val ERRAND = "r1.errand"
        private const val MAX_DRIVE = "r1.maxDrive"
        private const val MAX_DETOUR = "r1.maxDetour"
        private const val MAX_PICK_UP = "r1.maxPickUp"
        private const val MAX_PARK_RIDE = "r1.maxParkRide"

        /** `kind`: "me" (my location), "pt" (a point), absent = no place. */
        private fun writePlace(set: (String, Any?) -> Unit, key: String, ref: PlaceRef?) {
            val p = ref as? PlaceRef.Point
            set("$key.kind", when (ref) { null -> null; PlaceRef.MyLocation -> "me"; is PlaceRef.Point -> "pt" })
            set("$key.name", p?.name)
            set("$key.lat", p?.at?.lat)
            set("$key.lon", p?.at?.lon)
        }

        private fun readPlace(get: (String) -> Any?, key: String): PlaceRef? = when (get("$key.kind")) {
            "me" -> PlaceRef.MyLocation
            "pt" -> {
                val lat = get("$key.lat") as? Double
                val lon = get("$key.lon") as? Double
                if (lat != null && lon != null) PlaceRef.Point(get("$key.name") as? String, LatLon(lat, lon)) else null
            }
            else -> null
        }

        /** Null when nothing was saved (a fresh start): the app opens as usual. */
        fun read(get: (String) -> Any?): SavedUi? {
            val mode = (get(MODE) as? String)?.let { m -> AppMode.entries.firstOrNull { it.name == m } } ?: return null
            val stopId = get(STOP_ID) as? String
            return SavedUi(
                mode = mode,
                from = readPlace(get, FROM) ?: PlaceRef.MyLocation,
                to = readPlace(get, TO),
                driverTo = readPlace(get, DRIVER_TO),
                timeMode = (get(TIME_MODE) as? String)?.let { t -> TimeMode.entries.firstOrNull { it.name == t } } ?: TimeMode.NOW,
                time = (get(TIME) as? Long)?.let(Instant::ofEpochMilli),
                selected = (get(SELECTED) as? Int)?.coerceAtLeast(0) ?: 0,
                optionId = get(OPTION) as? String,
                cacheKey = get(CACHE_KEY) as? String,
                sheet = (get(RIDE_LEG) as? Int)?.let { Sheet.Ride(it) }
                    ?: stopId?.let { Sheet.Stop(it, get(STOP_NAME) as? String ?: "") },
                stops = (0 until ChainPlanner.MAX_STOPS).mapNotNull { i ->
                    (readPlace(get, "$ERRAND$i") as? PlaceRef.Point)?.let { p -> ChainStop(p.at, p.name, get("$ERRAND$i.stay") as? Int ?: 0) }
                },
                maxDriveMin = get(MAX_DRIVE) as? Int ?: SavedUi().maxDriveMin,
                maxDetourMin = get(MAX_DETOUR) as? Int ?: SavedUi().maxDetourMin,
                maxPickUpDriveMin = get(MAX_PICK_UP) as? Int ?: SavedUi().maxPickUpDriveMin,
                maxParkRideMin = get(MAX_PARK_RIDE) as? Int ?: SavedUi().maxParkRideMin,
            )
        }

        /**
         * The start of the cache key that may name what [s] shows: "TRIP-" for Trip options,
         * "ERRANDS-" for an errand chain, the tab's name and "-" for a car tab (R1); null when
         * nothing is shown. A key left over from another tab or search never matches.
         */
        fun keyPrefix(s: UiState): String? = when (s.mode) {
            AppMode.TRIP -> when {
                s.chain != null -> "ERRANDS-"
                s.results != null -> "TRIP-"
                else -> null
            }
            AppMode.BETTER_START -> "BETTER_START-".takeIf { s.betterStart != null }
            AppMode.DROP_OFF -> "DROP_OFF-".takeIf { s.dropOff != null }
            AppMode.PICK_UP -> "PICK_UP-".takeIf { s.pickUp != null }
            AppMode.PARK_RIDE -> "PARK_RIDE-".takeIf { s.parkRide.result != null }
        }

        /**
         * The part of [s] worth keeping. [cacheKey] is the cache entry the shown options were saved
         * under, kept only when it is this tab's ([keyPrefix]); [rideLeg] is the open trip sheet's leg
         * in the selected option.
         */
        fun of(s: UiState, cacheKey: String?, rideLeg: Int?, optionId: String?): SavedUi = SavedUi(
            mode = s.mode,
            from = s.from,
            to = s.to,
            driverTo = s.driverTo,
            timeMode = s.timeMode,
            time = s.time,
            selected = s.selected,
            optionId = optionId,
            cacheKey = cacheKey?.takeIf { k -> keyPrefix(s)?.let(k::startsWith) == true },
            sheet = rideLeg?.let { Sheet.Ride(it) } ?: s.stopSheet?.let { Sheet.Stop(it.stopId, it.name) },
            stops = s.chainStops,
            maxDriveMin = s.maxDriveMin,
            maxDetourMin = s.maxDetourMin,
            maxPickUpDriveMin = s.maxPickUpDriveMin,
            maxParkRideMin = s.parkRide.maxDriveMin,
        )
    }
}
