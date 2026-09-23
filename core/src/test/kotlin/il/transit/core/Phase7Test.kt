package il.transit.core

import il.transit.core.update.UpdateCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    @Test fun `versions compare numerically, not as text`() {
        assertTrue(UpdateCheck.isNewer("0.9.1", "v0.10.0"))
        assertTrue(UpdateCheck.isNewer("0.1.0", "0.1.1"))
        assertTrue(UpdateCheck.isNewer("0.1", "0.1.1"))
        assertFalse(UpdateCheck.isNewer("0.2.0", "v0.1.9"))
        assertFalse(UpdateCheck.isNewer("0.2.0", "0.2.0"))
        assertFalse(UpdateCheck.isNewer("0.0.0-dev", "v9.9.9")) // local builds never nag
        assertFalse(UpdateCheck.isNewer("0.1.0", "banana"))
    }

    @Test fun `parses the GitHub answer and finds the APK asset`() {
        val body = """
            {"tag_name":"v0.2.0","html_url":"https://github.com/x/y/releases/tag/v0.2.0","draft":false,"prerelease":false,
             "assets":[{"name":"notes.txt","browser_download_url":"https://e/notes.txt"},
                       {"name":"israel-transit-planner-v0.2.0.apk","browser_download_url":"https://e/app.apk"}],
             "somethingNew":1}
        """.trimIndent()
        val r = UpdateCheck.parseLatest(body)!!
        assertEquals("0.2.0", r.version)
        assertEquals("https://e/app.apk", r.apkUrl)
        assertEquals("https://github.com/x/y/releases/tag/v0.2.0", r.pageUrl)
    }

    @Test fun `no apk asset, pre-releases and garbage`() {
        assertNull(UpdateCheck.parseLatest("""{"tag_name":"v1.0.0","html_url":"u","assets":[]}""")!!.apkUrl)
        assertNull(UpdateCheck.parseLatest("""{"tag_name":"v1.0.0","prerelease":true}"""))
        assertNull(UpdateCheck.parseLatest("""{"message":"Not Found"}"""))
        assertNull(UpdateCheck.parseLatest("<html>"))
    }

    @Test fun `checks at most once a day`() {
        assertTrue(UpdateCheck.shouldCheck(null, NOON))
        assertFalse(UpdateCheck.shouldCheck(NOON.minusSeconds(3600), NOON))
        assertTrue(UpdateCheck.shouldCheck(NOON.minusSeconds(86_400), NOON))
    }
}
