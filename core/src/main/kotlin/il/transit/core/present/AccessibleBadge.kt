package il.transit.core.present

import il.transit.core.api.Leg

/**
 * The accessible-bus badge (Phase 11 E3, item 8). MOT's GTFS sets `wheelchair_accessible` on every
 * bus trip, and the same route and operator carried the same value throughout the sample
 * (`plan_access_*`, 2026-10-20, skill `transitous-api`). Every rail leg reads `NOT_ACCESSIBLE`, a
 * default rather than a fact, so rail and every other mode get nothing.
 */
enum class AccessibleBadge {
    /** A small wheelchair badge with the words "Accessible bus" (never the icon alone). */
    ACCESSIBLE,

    /** An amber line: "This bus may not be wheelchair accessible". */
    MAY_NOT_BE,
}

/** Only with the Accessible-routes setting on (owner decision 7); missing or `UNKNOWN` → null. */
fun accessibleBadge(leg: Leg, accessibleRoutes: Boolean): AccessibleBadge? {
    if (!accessibleRoutes || legKind(leg.mode) != LegKind.BUS) return null
    return when (leg.wheelchairAccessible) {
        "ACCESSIBLE" -> AccessibleBadge.ACCESSIBLE
        "NOT_ACCESSIBLE" -> AccessibleBadge.MAY_NOT_BE
        else -> null
    }
}
