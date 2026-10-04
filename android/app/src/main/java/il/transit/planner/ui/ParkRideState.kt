package il.transit.planner.ui

import il.transit.core.api.BudgetedTransitApi
import il.transit.core.api.TransitApi
import il.transit.core.features.ParkRideOption
import il.transit.core.features.ParkRidePlanner
import il.transit.core.features.ParkRideQuery
import il.transit.core.features.ParkRideResult
import il.transit.core.geo.LatLon
import il.transit.core.plan.TimeMode
import java.time.Instant

/**
 * Everything the park & ride tab keeps, in one [UiState] field. [parked] outlives the
 * results: after "way back to my car" the Trip tab shows the plan to the station, and the
 * drive home from there comes from it.
 */
data class ParkRideUi(
    /** Longest drive to the station, traffic included (slider: 5–40, default 20). */
    val maxDriveMin: Int = 20,
    val result: ParkRideResult? = null,
    /** Where the drive started, for drawing the car part. */
    val origin: LatLon? = null,
    val parked: ParkRideOption? = null,
) {
    fun cleared() = copy(result = null)

    fun selected(index: Int): ParkRideOption? = result?.options?.getOrNull(index)?.payload

    /** The car part to draw before the selected train trip. */
    fun carPath(index: Int): List<LatLon> = origin?.let { o -> selected(index)?.carPath(o) }.orEmpty()

    companion object {
        const val MIN_DRIVE = 5
        const val MAX_DRIVE = 40
    }
}

/** One park & ride search, under its own request budget. */
suspend fun planParkRide(api: TransitApi, s: UiState, from: LatLon, to: LatLon, language: String): ParkRideResult =
    ParkRidePlanner(BudgetedTransitApi(api, ParkRidePlanner.BUDGET), s.settings.traffic()).plan(
        ParkRideQuery(
            origin = from,
            dest = to,
            departAt = s.time?.takeIf { s.timeMode == TimeMode.DEPART_AT } ?: Instant.now(),
            maxDriveMin = s.parkRide.maxDriveMin,
            preferences = s.settings.preferences(),
            language = language,
        ),
    )
