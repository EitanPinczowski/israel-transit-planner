package il.transit.core

import il.transit.core.present.PayApps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 11 E4: the owner's payment apps (decision 1), with package names checked on Google Play. */
class PayAppsTest {
    @Test fun `the list is the owner's, in the owner's order, with the confirmed package names`() {
        assertEquals(
            listOf("com.pcentra.ravkavonlinemobile", "co.hopon.client", "com.tranzmate"),
            PayApps.ALL.map { it.packageName },
        )
        assertEquals(listOf("Rav-Kav Online", "Rav-Pass by HopOn", "Moovit"), PayApps.ALL.map { it.nameEn })
        // ANY WAY by Isracard ended its service in 2022: not offered.
        assertNull(PayApps.byPackage("com.isracard.payments"))
        PayApps.ALL.forEach { assertTrue("checked date for ${it.nameEn}", Regex("""\d{4}-\d{2}-\d{2}""").matches(it.checkedOn)) }
    }

    @Test fun `names in both languages - Hebrew for he and iw, English otherwise`() {
        assertEquals(listOf("רב-קו אונליין", "הופאון רב-פס", "מוביט"), PayApps.ALL.map { it.name("he") })
        assertEquals("מוביט", PayApps.ALL[2].name("iw"))
        assertEquals("Moovit", PayApps.ALL[2].name("en"))
        assertEquals("Moovit", PayApps.ALL[2].name("ar")) // any other language: English
        PayApps.ALL.forEach { a ->
            assertTrue(a.nameHe.any { it in 'א'..'ת' })
            assertTrue(a.nameEn.all { it.code < 128 })
        }
    }

    @Test fun `store links - the Play Store app first, the web page as the fallback`() {
        val moovit = PayApps.byPackage("com.tranzmate")!!
        assertEquals("market://details?id=com.tranzmate", moovit.marketUri)
        assertEquals("https://play.google.com/store/apps/details?id=com.tranzmate", moovit.webStoreUrl)
        PayApps.ALL.forEach { assertTrue(it.webStoreUrl.endsWith("?id=" + it.packageName)) }
    }

    @Test fun `the chooser puts installed apps first, each group in the owner's order`() {
        assertEquals(PayApps.ALL, PayApps.chooserOrder(emptySet()))
        assertEquals(PayApps.ALL, PayApps.chooserOrder(PayApps.ALL.map { it.packageName }.toSet()))
        assertEquals(
            listOf("com.tranzmate", "com.pcentra.ravkavonlinemobile", "co.hopon.client"),
            PayApps.chooserOrder(setOf("com.tranzmate", "com.whatsapp")).map { it.packageName },
        )
        assertEquals(
            listOf("co.hopon.client", "com.tranzmate", "com.pcentra.ravkavonlinemobile"),
            PayApps.chooserOrder(setOf("com.tranzmate", "co.hopon.client")).map { it.packageName },
        )
    }

    @Test fun `my payment app - None by default, an app no longer on the list is None`() {
        assertNull(PayApps.chosen(null))
        assertNull(PayApps.chosen("com.isracard.payments"))
        assertEquals("Moovit", PayApps.chosen("com.tranzmate")!!.nameEn)
    }
}
