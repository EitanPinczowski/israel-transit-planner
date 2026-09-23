package il.transit.core.plan

import il.transit.core.api.Endpoint
import il.transit.core.api.Itinerary
import il.transit.core.api.PlanRequest
import il.transit.core.api.TransitApi
import il.transit.core.user.UserSettings
import java.time.Instant

enum class TimeMode { NOW, DEPART_AT, ARRIVE_BY }

data class TripQuery(
    val from: Endpoint,
    val to: Endpoint,
    val timeMode: TimeMode = TimeMode.NOW,
    /** Ignored for [TimeMode.NOW]. */
    val time: Instant? = null,
    val settings: UserSettings = UserSettings(),
    val language: String = "he",
)

data class TripResult(
    /** Transit options, earliest arrival first (latest departure first for arrive-by). */
    val itineraries: List<Itinerary>,
    /** Walking all the way, when MOTIS offers it and it is not absurdly long. */
    val walkOnly: Itinerary?,
)

/** The ordinary A→B search: one `plan` request. */
class TripPlanner(private val api: TransitApi) {
    suspend fun plan(q: TripQuery, now: Instant = Instant.now()): TripResult {
        val time = if (q.timeMode == TimeMode.NOW) now else requireNotNull(q.time) { "time needed for ${q.timeMode}" }
        val resp = api.plan(
            PlanRequest(
                from = q.from,
                to = q.to,
                time = time,
                arriveBy = q.timeMode == TimeMode.ARRIVE_BY,
                preferences = q.settings.preferences(),
                language = q.language,
            ),
        )
        val sorted = if (q.timeMode == TimeMode.ARRIVE_BY) {
            resp.itineraries.sortedWith(compareByDescending<Itinerary> { it.start }.thenBy { it.transfers })
        } else {
            resp.itineraries.sortedWith(compareBy<Itinerary> { it.end }.thenBy { it.transfers })
        }
        val walk = resp.direct.minByOrNull { it.duration }?.takeIf { it.duration <= MAX_WALK_ONLY_SEC }
        return TripResult(sorted, walk)
    }

    companion object {
        const val MAX_WALK_ONLY_SEC = 45 * 60
    }
}
