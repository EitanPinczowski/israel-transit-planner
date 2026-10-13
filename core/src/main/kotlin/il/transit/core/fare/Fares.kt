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
 * (read on [CHECKED]; its Last-Modified was 2026-08-04). It agrees with the ₪8 yellow fare and the 323/464/684
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
 * - A month never costs more than the monthly pass ("חופשי חודשי") that covers it: [MONTHLY_BUS]
 *   (₪315 nationwide up to purple) or, with any train, [MONTHLY_WITH_TRAIN] by band. The
 *   page's cheaper zone-1 periphery (₪139) and Eilat passes are left out: the app can't tell
 *   the zone, so the advice may miss a cheaper local pass.
 * - Discounts are per [FareProfile]: one percentage for single fares and the daily cap, one for
 *   the monthly cap / pass (see the profile note there).
 *
 * When prices change: update the columns and [CHECKED]; `FareTest` pins the table.
 */
object FareTable {
    const val SOURCE_URL = "https://s3-eu-west-1.amazonaws.com/static.hopon.co.il/mot/ravPassPrices.html"
    const val OFFICIAL_URL = "https://bus.gov.il/FaresDistance"
    const val CHECKED = "2026-10-06"

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

    /** Monthly pass by the longest band covered, buses + light rail only. */
    val MONTHLY_BUS = listOf(31500, 31500, 31500, 31500, 31500, 68400)
    /** Monthly pass with Israel Railways (includes all buses); beyond purple is the last one. */
    val MONTHLY_WITH_TRAIN = listOf(32300, 32300, 46400, 68400, 68400)

    val TRANSFER_WINDOW: Duration = Duration.ofMinutes(90)

    fun band(km: Double): Int = BAND_KM.indexOfFirst { km <= it }.let { if (it < 0) BAND_KM.size else it }

    fun price(kind: FareKind, band: Int): Int = when (kind) {
        FareKind.TRAIN -> TRAIN.getOrElse(band) { TRAIN.last() }
        FareKind.LIGHT_RAIL -> LIGHT_RAIL.getOrElse(band) { BUS[band] }
        FareKind.BUS -> BUS[band]
    }

    fun dailyCap(band: Int, withTrain: Boolean): Int =
        if (withTrain) DAILY_CAP_WITH_TRAIN.getOrElse(band) { DAILY_CAP_WITH_TRAIN.last() } else DAILY_CAP_BUS[band]

    fun monthlyPass(band: Int, withTrain: Boolean): Int =
        if (withTrain) MONTHLY_WITH_TRAIN.getOrElse(band) { MONTHLY_WITH_TRAIN.last() } else MONTHLY_BUS[band]
}

enum class FareKind { BUS, LIGHT_RAIL, TRAIN }

/**
 * Who is paying: the share of the full price paid (100 = no discount) on single rides and the
 * daily cap, and on the monthly cap / pass ("חופשי חודשי").
 *
 * Source for every number: the Rav-Pass discount profiles page, [PROFILES_URL], which
 * [FareTable.SOURCE_URL] links to ("מידע נוסף על אחוזי הנחה לזכאים"), checked on [CHECKED]
 * (its Last-Modified was 2025-05-12). The price list itself says the single fare, the daily cap
 * and the monthly cap are all computed with the profile's percentage, so where the profiles page
 * gives no daily figure the single-ride one is used for the daily cap.
 *
 * - [SENIOR]: "אזרחים ותיקים בגילאי 67 ומעלה (זהב-קו)": 100% off single, daily and monthly.
 *   Women aged 62 to 66 get 50% off everything, which is [HALF].
 * - [STUDENT]: "סטודנטים": 33% off a single ride; the page lists no monthly discount, so the
 *   monthly pass is full price.
 * - [YOUTH]: "נוער" (under 18, or up to 19 in year 12): 50% off single and monthly. The price
 *   list's own example agrees: ₪4 instead of ₪8, the national pass ₪157.5 instead of ₪315.
 * - [SOLDIER]: owner decision 2026-10-13: soldiers in mandatory service ride free (buses, light
 *   rail and trains; the IDF's arrangement through the ZUZU app). The profiles page covers only
 *   discharged soldiers (100% for a year after discharge), which is [FREE].
 * - [HALF] ("50%, other": disability, National Insurance, women 62–66) and [FREE] ("free, other":
 *   discharged soldiers, blind, past and present MKs): the page's other 50% and 100% rows.
 *
 * Settings stored before Phase 10 hold REGULAR, HALF or FREE; those names and prices are kept.
 */
@Serializable
enum class FareProfile(val singlePaid: Int, val dailyPaid: Int, val monthlyPaid: Int) {
    REGULAR(100, 100, 100),
    SENIOR(0, 0, 0),
    STUDENT(67, 67, 100),
    YOUTH(50, 50, 50),
    SOLDIER(0, 0, 0),
    HALF(50, 50, 50),
    FREE(0, 0, 0),
    ;

    /** Nothing to pay at all: no prices worth showing as savings and no pass advice. */
    val free: Boolean get() = singlePaid == 0 && dailyPaid == 0 && monthlyPaid == 0

    companion object {
        const val PROFILES_URL = "https://s3-eu-west-1.amazonaws.com/static.hopon.co.il/mot/profiles.html"
        const val CHECKED = "2026-10-13"

        /** [agorot] × [percent] / 100, rounded to the nearest agora. */
        fun share(agorot: Int, percent: Int): Int = (agorot * percent + 50) / 100
    }
}

data class FareEstimate(
    val agorot: Int,
    /** The daily cap cut the sum of single fares. */
    val capped: Boolean,
    val hasTrain: Boolean,
    /** The longest band ridden, for the daily and monthly caps across trips. */
    val maxBand: Int = FareTable.YELLOW,
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
        // In hundredths of an agora, so the cap compares before rounding.
        val single = sum * profile.singlePaid
        val cap = FareTable.dailyCap(maxBand, hasTrain) * profile.dailyPaid
        return FareEstimate(
            agorot = (minOf(single, cap) + 50) / 100,
            capped = single > cap,
            hasTrain = hasTrain,
            maxBand = maxBand,
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
