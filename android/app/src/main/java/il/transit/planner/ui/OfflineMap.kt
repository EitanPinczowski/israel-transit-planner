package il.transit.planner.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

data class OfflineState(
    val status: Status = Status.NONE,
    val percent: Int = 0,
    val sizeMb: Double = 0.0,
) {
    enum class Status { NONE, DOWNLOADING, READY, TOO_BIG, FAILED }
}

/**
 * One downloadable offline map area (MapLibre's offline database). Zoom 10–14 only:
 * OpenFreeMap vector tiles overzoom past 14, so that is every street and stop name, and it
 * keeps a city-sized area to a few hundred tiles — polite to a free, donation-run server.
 */
class OfflineMapManager(context: Context) {
    private val manager = OfflineManager.getInstance(context)
    private val _state = MutableStateFlow(OfflineState())
    val state: StateFlow<OfflineState> = _state.asStateFlow()

    init {
        withRegion { region ->
            region?.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                override fun onStatus(status: OfflineRegionStatus?) {
                    if (status != null) _state.value = status.toState(downloading = false)
                }

                override fun onError(error: String?) = Unit
            })
        }
    }

    fun download(bounds: LatLngBounds, styleUrl: String, pixelRatio: Float) {
        val latSpan = bounds.latitudeNorth - bounds.latitudeSouth
        val lonSpan = bounds.longitudeEast - bounds.longitudeWest
        if (latSpan > MAX_SPAN_DEG || lonSpan > MAX_SPAN_DEG) {
            _state.value = OfflineState(OfflineState.Status.TOO_BIG)
            return
        }
        _state.value = OfflineState(OfflineState.Status.DOWNLOADING)
        deleteAll {
            val definition = OfflineTilePyramidRegionDefinition(styleUrl, bounds, MIN_ZOOM, MAX_ZOOM, pixelRatio)
            manager.createOfflineRegion(
                definition,
                "area".toByteArray(),
                object : OfflineManager.CreateOfflineRegionCallback {
                    override fun onCreate(offlineRegion: OfflineRegion) {
                        offlineRegion.setObserver(observer(offlineRegion))
                        offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                    }

                    override fun onError(error: String) {
                        _state.value = OfflineState(OfflineState.Status.FAILED)
                    }
                },
            )
        }
    }

    fun delete() = deleteAll { _state.value = OfflineState() }

    private fun observer(region: OfflineRegion) = object : OfflineRegion.OfflineRegionObserver {
        override fun onStatusChanged(status: OfflineRegionStatus) {
            _state.value = status.toState(downloading = !status.isComplete)
            if (status.isComplete) region.setDownloadState(OfflineRegion.STATE_INACTIVE)
        }

        override fun onError(error: OfflineRegionError) {
            _state.value = _state.value.copy(status = OfflineState.Status.FAILED)
        }

        override fun mapboxTileCountLimitExceeded(limit: Long) {
            region.setDownloadState(OfflineRegion.STATE_INACTIVE)
            _state.value = OfflineState(OfflineState.Status.TOO_BIG)
        }
    }

    private fun OfflineRegionStatus.toState(downloading: Boolean): OfflineState {
        val pct = if (requiredResourceCount > 0) (completedResourceCount * 100 / requiredResourceCount).toInt() else 0
        val status = when {
            isComplete -> OfflineState.Status.READY
            downloading -> OfflineState.Status.DOWNLOADING
            else -> OfflineState.Status.NONE
        }
        return OfflineState(status, pct, completedResourceSize / 1_000_000.0)
    }

    private fun withRegion(block: (OfflineRegion?) -> Unit) {
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) = block(offlineRegions?.firstOrNull())
            override fun onError(error: String) = block(null)
        })
    }

    /** Keep one area at a time: delete every stored region, then continue. */
    private fun deleteAll(then: () -> Unit) {
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val regions = offlineRegions.orEmpty().toMutableList()
                fun next() {
                    val r = regions.removeFirstOrNull() ?: return then()
                    r.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    r.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                        override fun onDelete() = next()
                        override fun onError(error: String) = next()
                    })
                }
                next()
            }

            override fun onError(error: String) = then()
        })
    }

    companion object {
        private const val MIN_ZOOM = 10.0
        private const val MAX_ZOOM = 14.0

        /** ~55 km: a city and its surroundings, not a country. */
        private const val MAX_SPAN_DEG = 0.5
    }
}
