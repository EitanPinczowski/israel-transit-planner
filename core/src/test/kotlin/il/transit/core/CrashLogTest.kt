package il.transit.core

import il.transit.core.diag.CrashLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class CrashLogTest {
    private val zone = ZoneId.of("Asia/Jerusalem")
    private val t0 = Instant.parse("2026-10-04T07:15:03Z")

    private fun entry(msg: String, at: Instant = t0) = CrashLog.format(
        IllegalStateException(msg), at, zone, appVersion = "0.7.0", androidVersion = "14 (SDK 34)", thread = "main",
    )

    @Test fun formatHasTimeVersionsThreadAndTrace() {
        val e = entry("boom")
        val lines = e.lines()
        assertEquals("===== crash 2026-10-04 10:15:03 +03:00 =====", lines[0])
        assertEquals("app: 0.7.0", lines[1])
        assertEquals("android: 14 (SDK 34)", lines[2])
        assertEquals("thread: main", lines[3])
        assertEquals("java.lang.IllegalStateException: boom", lines[4])
        assertTrue(lines[5].trimStart().startsWith("at il.transit.core.CrashLogTest"))
    }

    @Test fun causeIsKept() {
        val e = CrashLog.format(RuntimeException("outer", IllegalArgumentException("inner")), t0, zone, "1", "2", "main")
        assertTrue(e.contains("Caused by: java.lang.IllegalArgumentException: inner"))
    }

    @Test fun longTraceIsCut() {
        fun deep(n: Int): Nothing = if (n == 0) throw StackOverflowError("deep") else deep(n - 1)
        val err = runCatching { deep(2000) }.exceptionOrNull()!!
        val e = CrashLog.format(err, t0, zone, "1", "2", "main")
        assertTrue(e.length < CrashLog.MAX_TRACE_CHARS + 500)
        assertTrue(e.trimEnd().endsWith("… (cut)"))
    }

    @Test fun keepsOnlyTheLastFive() {
        var text: String? = null
        for (i in 1..7) text = CrashLog.append(text, entry("crash $i", t0.plusSeconds(i * 60L)))
        val list = CrashLog.entries(text)
        assertEquals(CrashLog.MAX_ENTRIES, list.size)
        assertTrue(list.first().contains("crash 3"))
        assertTrue(list.last().contains("crash 7"))
        // Round trip: re-splitting the written file gives the same entries.
        assertEquals(list, CrashLog.entries(list.joinToString("\n")))
    }

    @Test fun emptyAndJunk() {
        assertEquals(emptyList<String>(), CrashLog.entries(null))
        assertEquals(emptyList<String>(), CrashLog.entries("  \n"))
        assertEquals(emptyList<String>(), CrashLog.entries("half-written garbage"))
        assertNull(CrashLog.report(null, "0.7.0"))
        // A torn line before the first header is dropped, the entry after it survives.
        assertEquals(1, CrashLog.entries("garbage\n" + entry("ok")).size)
    }

    @Test fun reportIsNewestFirst() {
        val text = CrashLog.append(CrashLog.append(null, entry("older")), entry("newer", t0.plusSeconds(60)))
        val r = CrashLog.report(text, "0.7.0")!!
        assertTrue(r.startsWith("Israel Transit Planner 0.7.0 — crash log (2 crashes, newest first)"))
        assertTrue(r.indexOf("newer") < r.indexOf("older"))
    }
}
