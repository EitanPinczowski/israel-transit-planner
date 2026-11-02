package il.transit.core

import il.transit.core.api.MotisJson
import il.transit.core.api.Place
import il.transit.core.api.PlanResponse
import il.transit.core.present.AccessibleBadge
import il.transit.core.present.DepartureRow
import il.transit.core.present.LegChip
import il.transit.core.present.LegKind
import il.transit.core.present.Phrase
import il.transit.core.present.Speech
import il.transit.core.present.SpeechKey
import il.transit.core.present.Transfer
import il.transit.core.present.TransferWarning
import il.transit.core.present.accessibleBadge
import il.transit.core.present.tightTransfers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Phase 11 E5: what TalkBack reads, rendered through the app's own `strings_a11y.xml` in English and
 * Hebrew (the same files the phone uses), so the word order of each language is what is tested.
 */
class SpeechTest {
    /** One language's `a11y_*` strings and plurals, read from the Android resources. */
    private class Words(folder: String, private val hebrew: Boolean) {
        val strings = mutableMapOf<String, String>()
        val plurals = mutableMapOf<String, Map<String, String>>()

        init {
            val f = File("../android/app/src/main/res/$folder/strings_a11y.xml")
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
            val root = doc.documentElement
            val nodes = root.childNodes
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as? org.w3c.dom.Element ?: continue
                val name = el.getAttribute("name")
                when (el.tagName) {
                    "string" -> strings[name] = unescape(el.textContent)
                    "plurals" -> {
                        val items = el.getElementsByTagName("item")
                        plurals[name] = (0 until items.length).associate {
                            val item = items.item(it) as org.w3c.dom.Element
                            item.getAttribute("quantity") to unescape(item.textContent)
                        }
                    }
                }
            }
        }

        private fun unescape(s: String) = s.replace("\\'", "'").replace("\\\"", "\"")

        fun say(p: Phrase): String {
            val name = "a11y_" + p.key.name.lowercase(Locale.ROOT)
            val template = if (p.key.plural) {
                val forms = plurals.getValue(name)
                val n = p.count!!
                val q = when {
                    n == 1 -> "one"
                    hebrew && n == 2 && "two" in forms -> "two"
                    else -> "other"
                }
                forms[q] ?: forms.getValue("other")
            } else {
                strings.getValue(name)
            }
            val args: List<Any> = p.args + listOfNotNull(p.count.takeIf { p.key.plural })
            return String.format(Locale.ROOT, template, *args.toTypedArray()).replace("⁨", "").replace("⁩", "")
        }
    }

    private val en = Words("values", hebrew = false)
    private val he = Words("values-iw", hebrew = true)

    private fun plan(name: String) =
        MotisJson.decodeFromString(PlanResponse.serializer(), javaClass.getResource("/fixtures/$name.json")!!.readText())

    private fun stop(name: String) = Place(name = name, lat = 31.25, lon = 34.79)

    private fun at(iso: String) = Instant.parse(iso)

    @Test
    fun `every key has a string in both languages`() {
        for (k in SpeechKey.entries) {
            val name = "a11y_" + k.name.lowercase(Locale.ROOT)
            for (w in listOf(en, he)) {
                if (k.plural) assertTrue("$name plurals", name in w.plurals) else assertTrue("$name string", name in w.strings)
            }
        }
    }

    @Test
    fun `a bus leg says line, times as times, stops and where to get off`() {
        // 08:10–08:34 Israel time (UTC+3 in October).
        val bus = leg("BUS", stop("רגר/אורן"), stop("Rager/Oren"), at("2026-10-05T05:10:00Z"), at("2026-10-05T05:34:00Z"))
            .copy(routeShortName = "47", intermediateStops = (1..11).map { stop("s$it") })
        assertEquals("Bus 47, 8:10 to 8:34, 12 stops, get off at Rager/Oren", Speech.leg(bus).render(en::say))
        assertEquals("אוטובוס קו 47, מ־8:10 עד 8:34, 12 תחנות, ירידה בRager/Oren", Speech.leg(bus).render(he::say))
    }

    @Test
    fun `one stop and two stops take the plural forms of each language`() {
        val one = leg("BUS", stop("a"), stop("b"), at("2026-10-05T05:10:00Z"), at("2026-10-05T05:12:00Z")).copy(routeShortName = "5א")
        assertEquals("Bus 5א, 8:10 to 8:12, 1 stop, get off at b", Speech.leg(one).render(en::say))
        assertEquals("אוטובוס קו 5א, מ־8:10 עד 8:12, תחנה אחת, ירידה בb", Speech.leg(one).render(he::say))
        val two = one.copy(intermediateStops = listOf(stop("m")))
        assertTrue(Speech.leg(two).render(he::say).contains("2 תחנות"))
    }

    @Test
    fun `a walk and a drive say minutes and where they end`() {
        val walk = leg("WALK", stop("a"), stop("Central station"), at("2026-10-05T05:00:00Z"), at("2026-10-05T05:07:00Z"))
        assertEquals("Walk 7 minutes, to Central station", Speech.leg(walk).render(en::say))
        assertEquals("הליכה של 7 דקות, אל Central station", Speech.leg(walk).render(he::say))
        val oneMin = walk.copy(endTime = "2026-10-05T05:01:00Z")
        assertEquals("הליכה של דקה אחת, אל Central station", Speech.leg(oneMin).render(he::say))
        val drive = leg("CAR", stop("a"), stop("b"), at("2026-10-05T05:00:00Z"), at("2026-10-05T05:12:00Z"))
        assertEquals("Drive 12 minutes, to b", Speech.leg(drive).render(en::say))
    }

    @Test
    fun `past midnight the hour is 0, never 24 and never digit by digit`() {
        val night = leg("BUS", stop("a"), stop("b"), at("2026-10-05T20:50:00Z"), at("2026-10-05T21:20:00Z")).copy(routeShortName = "N1")
        assertEquals("Bus N1, 23:50 to 0:20 the next day, 1 stop, get off at b", Speech.leg(night).render(en::say))
        assertEquals("אוטובוס קו N1, מ־23:50 עד 0:20 למחרת, תחנה אחת, ירידה בb", Speech.leg(night).render(he::say))
        assertEquals("0:05", Speech.time(at("2026-10-05T21:05:00Z")))
        // Winter time (UTC+2) after 25 Oct.
        assertEquals("8:10", Speech.time(at("2026-11-02T06:10:00Z")))
    }

    @Test
    fun `a recorded train says its number and town (E3), light rail its line`() {
        val t404 = plan("plan_pages_earlier").itineraries[1].legs[1]
        val said = Speech.leg(t404).render(en::say)
        assertTrue(said, said.startsWith("Train 404 to כרמיאל, "))
        assertTrue(said, said.contains(", 4 stops, get off at "))
        val heSaid = Speech.leg(t404).render(he::say)
        assertTrue(heSaid, heSaid.startsWith("רכבת 404 לכרמיאל, מ־"))
        val tram = leg("TRAM", stop("a"), stop("b"), at("2026-10-05T05:00:00Z"), at("2026-10-05T05:10:00Z")).copy(routeShortName = "R")
        assertEquals("Light rail R, 8:00 to 8:10, 1 stop, get off at b", Speech.leg(tram).render(en::say))
        val train = leg("RAIL", stop("a"), stop("b"), at("2026-10-05T05:00:00Z"), at("2026-10-05T05:10:00Z"))
        assertEquals("Train, 8:00 to 8:10, 1 stop, get off at b", Speech.leg(train).render(en::say))
        assertEquals("רכבת, מ־8:00 עד 8:10, תחנה אחת, ירידה בb", Speech.leg(train).render(he::say))
    }

    @Test
    fun `the accessible-bus badge is said in words right after the line (E3)`() {
        val bus = plan("plan_access_telaviv").itineraries.flatMap { it.legs }.first { it.mode == "BUS" && it.wheelchairAccessible == "ACCESSIBLE" }
        val badge = accessibleBadge(bus, accessibleRoutes = true)
        assertEquals(AccessibleBadge.ACCESSIBLE, badge)
        val said = Speech.leg(bus, badge).render(en::say)
        assertTrue(said, Regex("^Bus [^,]+, accessible bus, \\d+:\\d\\d to ").containsMatchIn(said))
        val maybe = Speech.leg(bus, AccessibleBadge.MAY_NOT_BE).render(he::say)
        assertTrue(maybe, maybe.contains(", ייתכן שאינו נגיש לכיסא גלגלים, מ־"))
        // Setting off: no badge, nothing said.
        assertEquals(null, accessibleBadge(bus, accessibleRoutes = false))
    }

    @Test
    fun `an option says leave, arrive, minutes and transfers`() {
        val o = Speech.option(at("2026-10-05T05:21:00Z"), at("2026-10-05T06:46:00Z"), 85, 1)
        assertEquals("leave 8:21, arrive 9:46, 85 minutes, 1 transfer", o.render(en::say))
        assertEquals("יציאה ב־8:21, הגעה ב־9:46, 85 דקות, החלפה אחת", o.render(he::say))
        val d = Speech.option(at("2026-10-05T05:21:00Z"), at("2026-10-05T05:23:00Z"), 2, 0)
        assertEquals("יציאה ב־8:21, הגעה ב־8:23, 2 דקות, בלי החלפות", d.render(he::say))
    }

    @Test
    fun `an option on another day names its weekday, an arrival after midnight says so`() {
        // A picked day (Friday) searched from Thursday: the weekday comes from the app, in its language.
        val fri = Speech.option(at("2026-10-09T20:50:00Z"), at("2026-10-09T21:20:00Z"), 30, 0, departDay = "Friday", arriveDaysLater = 1)
        assertEquals("leave Friday at 23:50, arrive 0:20 the next day, 30 minutes, no transfers", fri.render(en::say))
        val heFri = Speech.option(at("2026-10-09T20:50:00Z"), at("2026-10-09T21:20:00Z"), 30, 0, departDay = "יום שישי", arriveDaysLater = 1)
        assertEquals("יציאה ביום שישי ב־23:50, הגעה ב־0:20 למחרת, 30 דקות, בלי החלפות", heFri.render(he::say))
        val later = Speech.option(at("2026-10-09T20:50:00Z"), at("2026-10-11T05:00:00Z"), 1930, 2, arriveDaysLater = 2)
        assertEquals("leave 23:50, arrive 8:00, 2 days later, 1930 minutes, 2 transfers", later.render(en::say))
        assertTrue(later.render(he::say), later.render(he::say).contains("הגעה ב־8:00, יומיים אחרי"))
    }

    @Test
    fun `zero minutes are never said: a short walk is 1 minute, no slack is no time to spare`() {
        val walk = leg("WALK", stop("a"), stop("b"), at("2026-10-05T05:00:00Z"), at("2026-10-05T05:00:20Z"))
        assertEquals("Walk 1 minute, to b", Speech.leg(walk).render(en::say))
        assertEquals("Walk 1 minute", Speech.chip(LegChip(LegKind.WALK, null, 0, false, "#888888")).render(en::say))
        assertEquals("Drive 1 minute", Speech.chip(LegChip(LegKind.CAR, null, 0, false, "#888888")).render(en::say))
        assertEquals("warning: tight change at Lehavim, no time to spare", Speech.tightChange("Lehavim", 0).render(en::say))
        assertEquals("אזהרה: החלפה צפופה בלהבים, בלי זמן מיותר", Speech.tightChange("להבים", -2).render(he::say))
    }

    @Test
    fun `every kind of tight-change chip is said in words`() {
        val t = Transfer(fromLeg = 1, toLeg = 3, stop = "Savidor", slackSec = 150, walkMin = 4, sameStop = false, train = false)
        assertEquals("warning: tight change at Savidor, only 2 minutes", Speech.tight(t, TransferWarning.CHANGE_IN).render(en::say))
        assertEquals("warning: walk 4 minutes to the next ride, 2 minutes to spare", Speech.tight(t, TransferWarning.WALK_SPARE).render(en::say))
        assertEquals("אזהרה: הליכה של 4 דקות לנסיעה הבאה, 2 דקות מיותרות", Speech.tight(t, TransferWarning.WALK_SPARE).render(he::say))
        val none = t.copy(slackSec = 20, walkMin = 0)
        assertEquals("warning: walk 1 minute to the next ride, no time to spare", Speech.tight(none, TransferWarning.WALK_SPARE).render(en::say))
        assertEquals("warning: tight change, you may miss it", Speech.tight(t.copy(slackSec = -60), TransferWarning.TIGHT).render(en::say))
        assertEquals("אזהרה: החלפה צפופה, ייתכן שתפספסו אותה", Speech.tight(t, TransferWarning.TIGHT).render(he::say))
        // The recorded BGU → Tel Aviv answer has tight walks: each says its words in both languages.
        val warned = plan("plan_bgu_telaviv").itineraries.flatMap { tightTransfers(it, null) }
        for ((tr, w) in warned) {
            for (lang in listOf(en, he)) assertTrue(Speech.tight(tr, w).render(lang::say).isNotBlank())
        }
    }

    @Test
    fun `a chip without its leg (car features) says the line and its minutes`() {
        val c = LegChip(LegKind.BUS, "370", 25, false, "#0072B2")
        assertEquals("Bus 370, 25 minutes", Speech.chip(c).render(en::say))
        assertEquals("Walk 4 minutes", Speech.chip(c.copy(kind = LegKind.WALK, label = null, minutes = 4)).render(en::say))
        assertEquals("נסיעה ברכב של 9 דקות", Speech.chip(c.copy(kind = LegKind.CAR, label = null, minutes = 9)).render(he::say))
        assertEquals("Train, 40 minutes", Speech.chip(c.copy(kind = LegKind.TRAIN, label = null, minutes = 40)).render(en::say))
    }

    @Test
    fun `every kind has a name, a line name, the kind alone or a train number without its town`() {
        val c = LegChip(LegKind.OTHER, "F1", 12, false, "#000000")
        assertEquals("Line F1, 12 minutes", Speech.chip(c).render(en::say))
        assertEquals("Public transport, 12 minutes", Speech.chip(c.copy(label = null)).render(en::say))
        assertEquals("רכבת קלה, 12 דקות", Speech.chip(c.copy(kind = LegKind.LIGHT_RAIL, label = null)).render(he::say))
        assertEquals("Bus, 12 minutes", Speech.chip(c.copy(kind = LegKind.BUS, label = null)).render(en::say))
        val numbered = leg("RAIL", stop("a"), stop("b"), at("2026-10-05T05:00:00Z"), at("2026-10-05T05:10:00Z")).copy(headsign = "24")
        assertEquals("Train 24, 8:00 to 8:10, 1 stop, get off at b", Speech.leg(numbered).render(en::say))
        // A walk to an unnamed point says only its minutes; a departure with no headsign skips it.
        val walk = leg("WALK", stop("a"), stop(""), at("2026-10-05T05:00:00Z"), at("2026-10-05T05:02:00Z"))
        assertEquals("Walk 2 minutes", Speech.leg(walk).render(en::say))
        val r = DepartureRow("5", "", LegKind.BUS, "08:28", null, false, at("2026-10-05T05:28:00Z"))
        assertEquals("Bus 5, leaves 8:28", Speech.departure(r).render(en::say))
    }

    @Test
    fun `a departure row says line, where it goes, when it leaves and if it is late or cancelled`() {
        val r = DepartureRow("5", "Central station", LegKind.BUS, "08:28", null, false, at("2026-10-05T05:28:00Z"))
        assertEquals("Bus 5, to Central station, leaves 8:28", Speech.departure(r).render(en::say))
        assertEquals("Bus 5, to Central station, leaves 8:28, 3 minutes late", Speech.departure(r.copy(delayMin = 3)).render(en::say))
        assertEquals("אוטובוס קו 5, לCentral station, יוצא ב־8:28, בוטל", Speech.departure(r.copy(cancelled = true)).render(he::say))
        val train = DepartureRow("", "באר שבע מרכז", LegKind.TRAIN, "08:35", null, false, null)
        assertEquals("Train, to באר שבע מרכז, leaves 8:35", Speech.departure(train).render(en::say))
    }

    @Test
    fun `a tight change warns with the stop and the minutes to spare`() {
        assertEquals("warning: tight change at Lehavim, only 3 minutes", Speech.tightChange("Lehavim", 3).render(en::say))
        assertEquals("אזהרה: החלפה צפופה בלהבים, דקה אחת בלבד", Speech.tightChange("להבים", 1).render(he::say))
        assertEquals("אזהרה: החלפה צפופה בלהבים, 2 דקות בלבד", Speech.tightChange("להבים", 2).render(he::say))
    }

    @Test
    fun `every leg of every recorded Trip answer can be said in both languages`() {
        val p = plan("plan_bgu_telaviv")
        for (l in p.itineraries.flatMap { it.legs }) {
            for (w in listOf(en, he)) {
                val s = Speech.leg(l).render(w::say)
                assertTrue(s, s.isNotBlank() && "null" !in s && !Regex("\\b0\\d:").containsMatchIn(s))
            }
        }
    }
}
