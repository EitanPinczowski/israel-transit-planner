package il.transit.core.present

import il.transit.core.api.Leg

/**
 * One colour per leg of an itinerary, shared by the chip in the list, the line on the map
 * and its stop dots (Phase 9 C6). Most Israeli MOT lines send no route colour, so every bus
 * fell back to the same blue and a two-bus trip looked like one long bus.
 *
 * Rules, all pure so the answer is the same on every recomposition:
 * - street legs keep their kind's colour (walks stay grey, the map draws them dashed);
 * - a transit leg keeps its operator (or kind) colour when no other transit leg of the trip
 *   has that colour and it reads on both maps;
 * - otherwise it takes the next [COLORS] entry, in leg order, skipping entries close to a
 *   colour the trip already shows (a kept default-blue bus, the car's purple).
 */
object LegPalette {
    /**
     * Okabe–Ito based, darkened where needed so each is ≥ 3:1 against both map backgrounds
     * (the styles' `background` layer; parks and water can be a little closer). Every chip
     * also carries its line number, so colour is never the only cue.
     * Order: blue and orange first, the pair that stays apart for every common kind of colour
     * blindness (most clashes are two buses); orange, green and magenta can look alike to
     * red-green colour blindness, which the line number on each chip covers.
     */
    val COLORS: List<String> = listOf("#0072B2", "#D55E00", "#009E73", "#C2185B", "#8E5CC4", "#AD7A00")

    /** OpenFreeMap `liberty` and `dark` background colours (the map styles' `background` layer). */
    const val MAP_LIGHT = "#F8F4F0"
    const val MAP_DARK = "#0C0C0C"

    /** Below this against either map, an operator colour vanishes (white or yellow lines on the light map). */
    const val MIN_OPERATOR_CONTRAST = 1.5

    /** Palette entries closer than this (RGB distance) to a colour already shown are skipped. */
    private const val TOO_CLOSE = 100.0

    /** Colours for [legs], one per leg, same order. */
    fun colors(legs: List<Leg>): List<String> {
        val base = legs.map(::legColor)
        val transit = legs.indices.filter { legs[it].isTransit }
        val counts = transit.groupingBy { base[it] }.eachCount()
        val replace = transit.filter { i -> counts.getValue(base[i]) > 1 || !readsOnMaps(base[i]) }.toSet()
        if (replace.isEmpty()) return base
        val shown = legs.indices.filter { it !in replace && legs[it].mode != il.transit.core.api.StreetModes.WALK }
            .map { base[it] }.toMutableList()
        shown += defaultColor(LegKind.CAR) // the drop-off ride before the itinerary
        val free = COLORS.filter { c -> shown.none { distance(c, it) < TOO_CLOSE } }.ifEmpty { COLORS }
        var next = 0
        return legs.indices.map { i -> if (i in replace) free[next++ % free.size] else base[i] }
    }

    /**
     * [leg]'s colour inside [legs] (the trip sheet, the vehicle dot), or null when the leg is
     * not one of them. Colours are per itinerary, not per line: the same line can differ in
     * two itineraries.
     */
    fun colorOf(legs: List<Leg>, leg: Leg): String? = legs.indexOf(leg).takeIf { it >= 0 }?.let { colors(legs)[it] }

    /** WCAG contrast ratio between two "#RRGGBB" colours, 1..21. */
    fun contrast(a: String, b: String): Double {
        val (lo, hi) = listOf(luminance(a), luminance(b)).sorted()
        return (hi + 0.05) / (lo + 0.05)
    }

    fun readsOnMaps(c: String): Boolean =
        contrast(c, MAP_LIGHT) >= MIN_OPERATOR_CONTRAST && contrast(c, MAP_DARK) >= MIN_OPERATOR_CONTRAST

    private fun luminance(hex: String): Double {
        val rgb = hex.removePrefix("#").toLong(16)
        fun channel(shift: Int): Double {
            val v = ((rgb shr shift) and 0xFF) / 255.0
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun distance(a: String, b: String): Double {
        val x = a.removePrefix("#").toLong(16)
        val y = b.removePrefix("#").toLong(16)
        fun d(shift: Int) = (((x shr shift) and 0xFF) - ((y shr shift) and 0xFF)).toDouble()
        return Math.sqrt(d(16) * d(16) + d(8) * d(8) + d(0) * d(0))
    }
}
