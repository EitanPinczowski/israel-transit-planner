package il.transit.core.geo

import il.transit.core.api.Itinerary
import il.transit.core.present.LegKind
import il.transit.core.present.LegPalette
import il.transit.core.present.legKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Map ↔ list highlighting (Phase 10 D11). The selected option is drawn by [MapData.itinerary] at
 * full colour; every other option comes from here, drawn thin and faded under it. Each line
 * carries its `option` index, so a tap on it selects that card. Walks are left out: grey dashes
 * from five options would only hide the streets.
 */
object OptionsMap {
    /** The rides of every option but [selected], each in its own [LegPalette] colour. */
    fun others(options: List<Itinerary>, selected: Int): String {
        val features = buildList {
            for ((i, it) in options.withIndex()) {
                if (i == selected) continue
                val colors = LegPalette.colors(it.legs)
                for ((li, leg) in it.legs.withIndex()) {
                    if (legKind(leg.mode) == LegKind.WALK) continue
                    val pts = leg.legGeometry?.let { g -> Geo.decodePolyline(g.points, g.precision) }
                        ?.takeIf { p -> p.size >= 2 }
                        ?: listOf(leg.from.latLon, leg.to.latLon)
                    add(
                        buildJsonObject {
                            put("type", "Feature")
                            putJsonObject("geometry") {
                                put("type", "LineString")
                                put("coordinates", buildJsonArray { pts.forEach { p -> add(buildJsonArray { add(p.lon); add(p.lat) }) } })
                            }
                            putJsonObject("properties") {
                                put("option", i)
                                put("color", colors[li])
                            }
                        },
                    )
                }
            }
        }
        return buildJsonObject {
            put("type", "FeatureCollection")
            put("features", JsonArray(features))
        }.toString()
    }
}
