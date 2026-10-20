package il.transit.core.plan

import il.transit.core.api.Itinerary
import il.transit.core.api.parseTime
import il.transit.core.features.BetterStartResult
import il.transit.core.features.DropOffResult
import il.transit.core.features.Option
import il.transit.core.features.ParkRideResult
import il.transit.core.features.PickUpResult
import il.transit.core.geo.LatLon
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant

/**
 * What a car tab or an errand chain showed, kept so it comes back after Android kills the app in
 * the background (Phase 11 R1). Never an offline answer: the car tabs' whole point is live
 * combinations, so only the restore reads it, under the key the app's saved state names.
 */
@Serializable
sealed interface TabResult {
    @Serializable
    @SerialName("betterStart")
    data class BetterStart(val result: BetterStartResult) : TabResult

    @Serializable
    @SerialName("dropOff")
    data class DropOff(val result: DropOffResult) : TabResult

    @Serializable
    @SerialName("pickUp")
    data class PickUp(val result: PickUpResult) : TabResult

    /** [origin]: where the drive started, for drawing the car part. */
    @Serializable
    @SerialName("parkRide")
    data class ParkRide(val result: ParkRideResult, val origin: LatLon?) : TabResult

    @Serializable
    @SerialName("errands")
    data class Errands(val chain: ChainResult) : TabResult
}

/** On-disk form of the car tabs' and errands' cache. */
val TabCacheJson: CacheJson<TabResult> = CacheJson(TabResult.serializer())

/**
 * A saved result as it may be shown again at [now]: an option is dropped when its first boarding
 * left more than [TripPlanner.DEPARTED_GRACE_SEC] ago (Phase 11 E7's rule), a walk-only one when
 * its walk started that long ago, and a car option when its driver had to leave that long ago
 * (`leaveAt`, `driverLeavesAt`). A baseline is dropped by the same rule. An errand chain is one
 * trip: any of its legs gone (a failed chain's planned legs included) and it is gone. Null when
 * nothing is left.
 */
fun TabResult.stillAhead(now: Instant): TabResult? {
    val cutoff = now.minusSeconds(TripPlanner.DEPARTED_GRACE_SEC)
    fun ahead(i: Itinerary?): Boolean = i != null && parseTime(i.firstTransitLeg?.startTime ?: i.startTime) >= cutoff
    fun <T> List<Option<T>>.left(keep: (T) -> Boolean) = filter { keep(it.payload) }
    fun Itinerary?.kept() = this?.takeIf { ahead(it) }
    return when (this) {
        is TabResult.BetterStart ->
            result
                .copy(baseline = result.baseline.kept(), options = result.options.left { ahead(it.itinerary) && it.leaveAt >= cutoff })
                .let { r -> TabResult.BetterStart(r).takeIf { r.options.isNotEmpty() || r.baseline != null } }
        is TabResult.DropOff ->
            result
                .copy(options = result.options.left { ahead(it.transit) })
                .let { r -> TabResult.DropOff(r).takeIf { r.options.isNotEmpty() } }
        is TabResult.PickUp ->
            result
                .copy(baseline = result.baseline.kept(), options = result.options.left { ahead(it.itinerary) && it.driverLeavesAt >= cutoff })
                .let { r -> TabResult.PickUp(r).takeIf { r.options.isNotEmpty() || r.baseline != null } }
        is TabResult.ParkRide ->
            result
                .copy(baseline = result.baseline.kept(), options = result.options.left { ahead(it.itinerary) && it.leaveAt >= cutoff })
                .let { r -> copy(result = r).takeIf { r.options.isNotEmpty() || r.baseline != null } }
        is TabResult.Errands -> takeIf { chain.legs.isNotEmpty() && chain.legs.all(::ahead) }
    }
}

/** An [Instant] as its ISO-8601 text: lossless, and readable in the saved file. */
object InstantIso : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("il.transit.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
