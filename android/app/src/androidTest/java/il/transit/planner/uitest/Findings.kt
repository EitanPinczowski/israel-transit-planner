package il.transit.planner.uitest

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * UX expectations that are not met yet, recorded instead of failing (report first: the
 * owner picks what to fix; then the expectation becomes a plain assert). Written to
 * `journeys.json` in the run's output folder, next to the layout audit.
 */
object Findings {
    private val list = mutableListOf<JSONObject>()

    @Synchronized fun expect(id: String, ok: Boolean, problem: String) {
        if (ok) return
        list += JSONObject().put("rule", id).put("where", "journey").put("detail", problem)
        flush()
    }

    @Synchronized private fun flush() {
        val o = JSONObject()
            .put("state", "journeys").put("profile", Run.profile).put("locale", Run.locale).put("theme", Run.theme)
            .put("sdk", AppDriver.sdk).put("violations", JSONArray(list))
        File(Run.outDir, "journeys.json").writeText(o.toString(2))
    }
}
