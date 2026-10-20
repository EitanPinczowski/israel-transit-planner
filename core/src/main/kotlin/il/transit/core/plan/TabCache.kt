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
 * A saved result as it may be shown again at [now]: options whose first boarding left more than
 * [TripPlanner.DEPARTED_GRACE_SEC] ago are dropped (Phase 11 E7's rule), and so is a baseline that
 * left. An errand chain is one trip: any of its legs gone and it is gone. Null when nothing is left.
 */
fun TabResult.stillAhead(now: Instant): TabResult? {
    val cutoff = now.minusSeconds(TripPlanner.DEPARTED_GRACE_SEC)
    fun ahead(i: Itinerary?): Boolean = i != null && (i.firstTransitLeg?.let { parseTime(it.startTime) >= cutoff } ?: true)
    fun <T> List<Option<T>>.left(itinerary: (T) -> Itinerary) = filter { ahead(itinerary(it.payload)) }
    fun Itinerary?.kept() = this?.takeIf { ahead(it) }
    val next: TabResult = when (this) {
        is TabResult.BetterStart -> TabResult.BetterStart(result.copy(baseline = result.baseline.kept(), options = result.options.left { it.itinerary }))
        is TabResult.DropOff -> TabResult.DropOff(result.copy(options = result.options.left { it.transit }))
        is TabResult.PickUp -> TabResult.PickUp(result.copy(baseline = result.baseline.kept(), options = result.options.left { it.itinerary }))
        is TabResult.ParkRide -> copy(result = result.copy(baseline = result.baseline.kept(), options = result.options.left { it.itinerary }))
        is TabResult.Errands -> return takeIf { chain.legs.isNotEmpty() && chain.legs.all(::ahead) }
    }
    val empty = when (next) {
        is TabResult.BetterStart -> next.result.options.isEmpty() && next.result.baseline == null
        is TabResult.DropOff -> next.result.options.isEmpty()
        is TabResult.PickUp -> next.result.options.isEmpty() && next.result.baseline == null
        is TabResult.ParkRide -> next.result.options.isEmpty() && next.result.baseline == null
        is TabResult.Errands -> false
    }
    return next.takeIf { !empty }
}

/** An [Instant] as its ISO-8601 text: lossless, and readable in the saved file. */
object InstantIso : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("il.transit.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
