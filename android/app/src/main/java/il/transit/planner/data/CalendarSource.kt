package il.transit.planner.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Instances
import androidx.core.content.ContextCompat
import il.transit.core.plan.CalendarEvent
import il.transit.core.plan.CalendarSuggest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/** What "From my calendar" reads: the permission, and the next day's events. A fake in tests. */
interface CalendarReader {
    fun hasPermission(): Boolean

    /** Event instances that start in the next [CalendarSuggest.WINDOW]; empty without the permission. */
    suspend fun upcoming(now: Instant = Instant.now()): List<CalendarEvent>
}

/**
 * Reads the next day's calendar events from the phone's own calendar provider. Nothing here
 * touches the network: titles stay on the phone. Which events to offer is decided in core
 * ([CalendarSuggest.pick]).
 */
class CalendarSource(private val context: Context) : CalendarReader {
    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    override suspend fun upcoming(now: Instant): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now.toEpochMilli())
            ContentUris.appendId(it, now.plus(CalendarSuggest.WINDOW).toEpochMilli())
        }.build()
        val projection = arrayOf(
            Instances.TITLE,
            Instances.EVENT_LOCATION,
            Instances.BEGIN,
            Instances.END,
            Instances.ALL_DAY,
            Instances.SELF_ATTENDEE_STATUS,
        )
        val events = mutableListOf<CalendarEvent>()
        runCatching {
            context.contentResolver.query(
                uri, projection,
                "${Instances.EVENT_LOCATION} IS NOT NULL AND ${Instances.EVENT_LOCATION} != ''",
                null, "${Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    events += CalendarEvent(
                        title = c.getString(0).orEmpty(),
                        location = c.getString(1).orEmpty(),
                        begin = Instant.ofEpochMilli(c.getLong(2)),
                        end = Instant.ofEpochMilli(c.getLong(3)),
                        allDay = c.getInt(4) == 1,
                        declined = c.getInt(5) == Attendees.ATTENDEE_STATUS_DECLINED,
                    )
                }
            }
        }
        events
    }
}
