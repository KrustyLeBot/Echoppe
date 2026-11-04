package app.echoppe.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SitesFileTest {
    private val a = Site("a1", "Amazon", "https://amazon.fr/")
    private val b = Site("b2", "Leroy Merlin", "https://www.leroymerlin.fr/", desktop = true, blockTrackers = false)

    @Test fun roundTrip() = assertEquals(listOf(a, b), SitesFile.decode(SitesFile.encode(listOf(a, b))))

    @Test fun missingOptionalsTakeDefaults() {
        val sites = SitesFile.decode("""{"format_version":1,"sites":[{"id":"x","name":"X","url":"https://x.fr/","extra":1}]}""")
        assertEquals(listOf(Site("x", "X", "https://x.fr/")), sites)
    }

    @Test fun emptyOrMissingList() {
        assertEquals(emptyList<Site>(), SitesFile.decode("""{"format_version":1}"""))
        assertEquals(emptyList<Site>(), SitesFile.decode("""{"sites":[]}"""))
    }

    @Test fun duplicateIdsKeepTheFirst() {
        val text = """{"sites":[{"id":"a1","name":"A","url":"https://a.fr/"},{"id":"a1","name":"B","url":"https://b.fr/"}]}"""
        assertEquals(listOf("A"), SitesFile.decode(text).map { it.name })
    }

    @Test fun newerFormatIsRefused() {
        assertThrows(IllegalStateException::class.java) { SitesFile.decode("""{"format_version":2,"sites":[]}""") }
    }

    @Test fun brokenJsonThrows() {
        assertThrows(Exception::class.java) { SitesFile.decode("""{"sites":[""") }
    }

}
