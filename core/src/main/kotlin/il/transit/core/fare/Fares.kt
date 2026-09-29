package il.transit.core.fare

import il.transit.core.api.Itinerary
import il.transit.core.api.Leg
import il.transit.core.geo.Geo
import il.transit.core.present.LegKind
import il.transit.core.present.legKind
import kotlinx.serialization.Serializable
import java.time.Duration

/**
 * Israeli single-ride fares ("צדק תחבורתי", in force since 25 Apr 2025), in agorot.
 *
 * Source: the price list HopOn publishes for its Rav-Pass payment app, [SOURCE_URL]
 * (Last-Modified [CHECKED]). It agrees with the ₪8 yellow fare and the 323/464/684
 * monthly caps reported elsewhere. bus.gov.il/FaresDistance is the official page; the app
 * links to it because this is an estimate.
 *
 * Rules, as that page states them:
 * - Bus, light rail, Metronit, Carmelit, cable car: priced per ride by the straight-line
 *   distance from the boarding stop to the alighting stop, in the bands [BAND_KM].
 * - Israel Railways has its own column. The railway really prices by station pair, so a
 *   band is an approximation.
 * - A yellow ride (≤ 15 km, not train) includes transfers to other yellow rides for
 *   [TRANSFER_WINDOW] from its validation; bus → light rail pays the difference. A ride over
 *   15 km pays a full single fare and gets no transfer. The train never joins the window.
 * - A day's total never exceeds the daily cap of the longest band ridden (the with-train
 *   column when any ride is a train).
 * - Discounts apply the same percentage to single fares and caps.
 *
 * When prices change: update the columns and [CHECKED]; `FareTest` pins the table.
 */
object FareTable {
    const val SOURCE_URL = "https://s3-eu-west-1.amazonaws.com/static.hopon.co.il/mot/ravPassPrices.html"
    const val OFFICIAL_URL = "https://bus.gov.il/FaresDistance"
    const val CHECKED = "2026-08-04"

    /** Upper limits (inclusive) of yellow, green, light blue, blue, purple; beyond is grey. */
    val BAND_KM = listOf(15.0, 40.0, 75.0, 120.0, 225.0)
    const val YELLOW = 0

    val BUS = listOf(800, 1450, 1900, 1900, 3050, 8732)
    /** Light rail has no price beyond green; [price] falls back to [BUS]. */
    val LIGHT_RAIL = listOf(800, 1450)
    /** No train fare beyond purple; [price] falls back to the last one. */
    val TRAIN = listOf(1150, 2100, 2700, 3050, 5250)

    val DAILY_CAP_BUS = listOf(1750, 2900, 3750, 3750, 6050, 7950)
    val DAILY_CAP_WITH_TRAIN = listOf(2300, 3250, 4200, 4700, 8050)

    val TRANSFER_WINDOW: Duration = Duration.ofMinutes(90)

    fun band(km: Double): Int = BAND_KM.indexOfFirst { km <= it }.let { if (it < 0) BAND_KM.size else it }

    fun price(kind: FareKind, band: Int): Int = when (kind) {
        FareKind.TRAIN -> TRAIN.getOrElse(band) { TRAIN.last() }
        FareKind.LIGHT_RAIL -> LIGHT_RAIL.getOrElse(band) { BUS[band] }
        FareKind.BUS -> BUS[band]
    }

    fun dailyCap(band: Int, withTrain: Boolean): Int =
        if (withTrain) DAILY_CAP_WITH_TRAIN.getOrElse(band) { DAILY_CAP_WITH_TRAIN.last() } else DAILY_CAP_BUS[band]
}

enum class FareKind { BUS, LIGHT_RAIL, TRAIN }

/** Who is paying; the percentage applies to single fares and caps alike. */
@Serializable
enum class FareProfile(val percentPaid: Int) { REGULAR(100), HALF(50), FREE(0) }

data class FareEstimate(
    val agorot: Int,
    /** The daily cap cut the sum of single fares. */
    val capped: Boolean,
    val hasTrain: Boolean,
)

object FareEstimator {
    /** Null when the itinerary has no transit leg (walking all the way costs nothing to show). */
    fun estimate(it: Itinerary, profile: FareProfile = FareProfile.REGULAR): FareEstimate? {
        val rides = rides(it.legs)
        if (rides.isEmpty()) return null

        var sum = 0
        var windowStart: java.time.Instant? = null
        var windowPaid = 0
        var maxBand = 0
        var hasTrain = false
        for (r in rides) {
            val band = FareTable.band(Geo.distanceM(r.first().from.latLon, r.last().to.latLon) / 1000.0)
            val kind = fareKind(r.first())
            val price = FareTable.price(kind, band)
            maxBand = maxOf(maxBand, band)
            val start = r.first().start
            when {
                kind == FareKind.TRAIN -> { hasTrain = true; sum += price }
                band != FareTable.YELLOW -> sum += price
                windowStart != null && start < windowStart.plus(FareTable.TRANSFER_WINDOW) -> {
                    // Inside the free-transfer window: pay only what a dearer mode adds.
                    sum += (price - windowPaid).coerceAtLeast(0)
                    windowPaid = maxOf(windowPaid, price)
                }
                else -> { windowStart = start; windowPaid = price; sum += price }
            }
        }
        val cap = FareTable.dailyCap(maxBand, hasTrain)
        val total = minOf(sum, cap)
        return FareEstimate(
            agorot = (total * profile.percentPaid + 50) / 100,
            capped = sum > cap,
            hasTrain = hasTrain,
        )
    }

    private fun fareKind(leg: Leg): FareKind = when (legKind(leg.mode)) {
        LegKind.TRAIN -> FareKind.TRAIN
        LegKind.LIGHT_RAIL -> FareKind.LIGHT_RAIL
        else -> FareKind.BUS
    }

    /** Transit legs, with consecutive legs of the same vehicle trip merged into one ride. */
    private fun rides(legs: List<Leg>): List<List<Leg>> {
        val out = mutableListOf<MutableList<Leg>>()
        for (leg in legs.filter { it.isTransit }) {
            val prev = out.lastOrNull()?.last()
            if (prev != null && leg.tripId != null && leg.tripId == prev.tripId) out.last().add(leg)
            else out.add(mutableListOf(leg))
        }
        return out
    }
}
