package il.transit.core.geo

import il.transit.core.api.Place

/**
 * Every Israel Railways and light-rail station, so "let me off on the way" never has to
 * download the stops of a 100-km corridor (Transitous ignores `map/stops?modes=`).
 *
 * The list lives in [RailStationsData], generated from the Ministry of Transport GTFS by
 * `python tools/gen_rail_stations.py`. Re-run it when a station or line opens. While it is
 * empty, DropOffPlanner falls back to one `map/stops` request.
 */
object RailStations {
    val ALL: List<Place> get() = RailStationsData.ALL
}
