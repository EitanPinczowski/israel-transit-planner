package il.transit.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.util.Random

/**
 * A seeded mutator for recorded JSON answers (Phase 11 T1). Deterministic: the same [seed] and text give
 * the same mutants, and a failure names the seed, the fixture and the mutant's number so it can be replayed:
 * `Fuzz(seed).mutants(text, n)[i]`.
 *
 * Mutations: cut at a random byte; drop a key or an element; null a value; a number as a string, or the
 * other way; an array for an object and back; an empty array; huge numbers; right-to-left marks and emoji in
 * a name; a bad time (offset out of range, no offset, a day that does not exist).
 */
class Fuzz(val seed: Long) {
    private val rnd = Random(seed)

    private sealed interface Step {
        data class Key(val k: String) : Step
        data class Index(val i: Int) : Step
    }

    fun mutants(text: String, n: Int): List<String> {
        val root = Json.parseToJsonElement(text)
        val paths = ArrayList<List<Step>>().also { collect(root, emptyList(), it) }
        return List(n) {
            if (rnd.nextInt(10) == 0) cutAtByte(text) else {
                var tree = root
                repeat(1 + rnd.nextInt(2)) { tree = mutateOnce(tree, paths) }
                tree.toString()
            }
        }
    }

    private fun cutAtByte(text: String): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val at = rnd.nextInt(bytes.size + 1)
        // A cut inside a multi-byte character is part of the test: it must not crash the parser either.
        return String(bytes, 0, at, Charsets.UTF_8)
    }

    private fun collect(e: JsonElement, at: List<Step>, out: MutableList<List<Step>>) {
        out += at
        when (e) {
            is JsonObject -> e.forEach { (k, v) -> collect(v, at + Step.Key(k), out) }
            is JsonArray -> e.forEachIndexed { i, v -> collect(v, at + Step.Index(i), out) }
            else -> Unit
        }
    }

    private fun get(e: JsonElement, path: List<Step>): JsonElement? = path.fold(e as JsonElement?) { cur, s ->
        when (s) {
            is Step.Key -> (cur as? JsonObject)?.get(s.k)
            is Step.Index -> (cur as? JsonArray)?.getOrNull(s.i)
        }
    }

    /** Rebuilds only the spine down to [path]; [f] returns the replacement, or null to remove the node. */
    private fun replace(e: JsonElement, path: List<Step>, f: (JsonElement) -> JsonElement?): JsonElement {
        if (path.isEmpty()) return f(e) ?: e
        val s = path.first()
        val rest = path.drop(1)
        return when {
            s is Step.Key && e is JsonObject && e.containsKey(s.k) -> {
                val child = e.getValue(s.k)
                if (rest.isEmpty()) {
                    val r = f(child)
                    JsonObject(if (r == null) e - s.k else e + (s.k to r))
                } else JsonObject(e + (s.k to replace(child, rest, f)))
            }
            s is Step.Index && e is JsonArray && s.i < e.size -> {
                val child = e[s.i]
                if (rest.isEmpty()) {
                    val r = f(child)
                    JsonArray(if (r == null) e.filterIndexed { i, _ -> i != s.i } else e.mapIndexed { i, v -> if (i == s.i) r else v })
                } else JsonArray(e.mapIndexed { i, v -> if (i == s.i) replace(v, rest, f) else v })
            }
            else -> e
        }
    }

    private fun mutateOnce(tree: JsonElement, paths: List<List<Step>>): JsonElement {
        repeat(20) {
            val path = paths[rnd.nextInt(paths.size)]
            val node = get(tree, path) ?: return@repeat
            val op = rnd.nextInt(9)
            val replacement: (JsonElement) -> JsonElement? = when (op) {
                0 -> { _ -> null }                                            // drop the key / element
                1 -> { _ -> JsonNull }                                        // null a value
                2 -> { e -> JsonPrimitive(if (e is JsonPrimitive) e.content else e.toString().take(40)) } // a number as a string
                3 -> { e -> if (e is JsonObject) JsonArray(listOf(e)) else if (e is JsonArray) JsonObject(e.withIndex().associate { it.index.toString() to it.value }) else JsonArray(listOf(e)) }
                4 -> { _ -> JsonArray(emptyList()) }                          // empty array
                5 -> { e -> if (e is JsonPrimitive && !e.isString && e.doubleOrNull != null) hugeNumber() else null }
                6 -> { e -> if (e is JsonPrimitive && e.isString) JsonPrimitive(rtlAndEmoji() + e.content) else null }
                7 -> { e -> if (e is JsonPrimitive && e.isString && looksLikeTime(e.content)) JsonPrimitive(badTime()) else null }
                else -> { e -> if (e is JsonObject && e.isNotEmpty()) JsonObject(e - e.keys.elementAt(rnd.nextInt(e.size))) else null }
            }
            // An operation that does not fit this node (it returned null for a non-removal) is skipped.
            if (op in 5..7) {
                val r = replacement(node) ?: return@repeat
                return replace(tree, path) { r }
            }
            return replace(tree, path, replacement)
        }
        return tree
    }

    private fun hugeNumber(): JsonElement = when (rnd.nextInt(6)) {
        0 -> JsonPrimitive(Long.MAX_VALUE)
        1 -> JsonPrimitive(Long.MIN_VALUE)
        2 -> JsonPrimitive(1e308)
        3 -> JsonPrimitive(-1e308)
        4 -> JsonPrimitive(Int.MAX_VALUE.toLong() + 1)
        else -> JsonPrimitive(0)
    }

    private fun rtlAndEmoji(): String = listOf("‮", "‏", "‫עברית‬", "🚌🚆", "\uD83D", "a\u0000b", "\n\t").random(rnd)

    private fun looksLikeTime(s: String) = s.length >= 16 && s[4] == '-' && s.contains('T')

    private fun badTime(): String = listOf(
        "2026-10-05T08:00:00+25:00", "2026-10-05T08:00:00", "2026-13-45T08:00:00Z", "", "not a time", "2026-10-05T24:61:00Z",
        "+999999999-01-01T00:00:00Z", "2026-10-05T08:00:00.1234567891Z",
    ).random(rnd)

    private fun <T> List<T>.random(r: Random): T = this[r.nextInt(size)]
}
