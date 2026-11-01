package il.transit.core

import il.transit.core.present.SharedPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 11 T6: what another app can hand `MainActivity` (its SEND / VIEW filters are exported). The
 * parser runs on the main thread and its Text answers cost one geocode, so hostile input must end
 * fast and, when it is not a place, as [SharedPlace.Unreadable] (no request).
 */
class SharedInputSecurityTest {
    @Test fun a_megabyte_of_text_is_parsed_quickly() {
        val big = "שלום ".repeat(200_000) // 1 MB, one line: before the cap, minutes of work on the main thread
        val street = "ברחוב הרצל 15 בתל אביב ".repeat(45_000)
        for (t in listOf(big, street, "a".repeat(1_000_000), "\n".repeat(1_000_000))) {
            val start = System.nanoTime()
            SharedPlace.parse(t)
            val ms = (System.nanoTime() - start) / 1_000_000
            assertTrue("${t.length} chars took $ms ms", ms < 2_000)
        }
        assertTrue(SharedPlace.MAX_SHARED in 1_000..4_000)
    }

    @Test fun broken_coordinates_are_unreadable() {
        for (s in listOf("geo:NaN,NaN", "geo:999,999", "geo:-999,34.8", "geo:32.1,999", "geo:Infinity,Infinity", "geo:", "geo:,")) {
            assertEquals(s, SharedPlace.Unreadable, SharedPlace.parse(s))
        }
        assertEquals(SharedPlace.OutsideIsrael, SharedPlace.parse("geo:0.5,0.5"))
    }

    @Test fun look_alike_map_hosts_are_not_map_links() {
        for (host in listOf("maps.google.com.evil.example", "www.google.com.evil.example", "google.evil.example", "waze.com.evil.example", "evilwaze.com")) {
            assertEquals(host, null, SharedPlace.link("https://$host/maps?q=32.0839,34.7983"))
            assertEquals(host, SharedPlace.Unreadable, SharedPlace.parse("https://$host/maps?q=32.0839,34.7983"))
            assertEquals(host, SharedPlace.Unreadable, SharedPlace.parse("https://$host/maps?q=Dizengoff"))
        }
        // Google's own country domains still are.
        for (host in listOf("maps.google.com", "www.google.co.il", "www.google.com.au", "www.google.de", "waze.com", "www.waze.com")) {
            val path = if (host.contains("waze")) "/ul?ll=32.0839,34.7983" else "/maps?q=32.0839,34.7983"
            assertTrue(host, SharedPlace.parse("https://$host$path") is SharedPlace.Point)
        }
    }

    @Test fun bidi_overrides_never_reach_the_screen() {
        val rlo = '‮'
        val p = SharedPlace.parse("geo:0,0?q=32.0839,34.7983(Home${rlo}gpj.exe)")
        assertTrue(p is SharedPlace.Point)
        assertEquals("Homegpj.exe", (p as SharedPlace.Point).label)
        val t = SharedPlace.parse("geo:0,0?q=${rlo}דיזנגוף 50 תל אביב")
        assertTrue(t is SharedPlace.Text)
        assertFalse((t as SharedPlace.Text).query.any { it in '‪'..'‮' || it in '⁦'..'⁩' })
    }

    @Test fun markup_and_sql_stay_plain_text_of_bounded_length() {
        for (q in listOf("'; DROP TABLE places;--", "<script>alert(1)</script>", "<img src=x onerror=alert(1)>")) {
            val p = SharedPlace.parse("geo:0,0?q=" + java.net.URLEncoder.encode(q, "UTF-8"))
            assertEquals(q, SharedPlace.Text(q), p)
        }
        val long = SharedPlace.parse("geo:0,0?q=" + "x".repeat(10_000))
        assertTrue(long is SharedPlace.Unreadable || (long as SharedPlace.Text).query.length <= SharedPlace.MAX_QUERY)
    }
}
