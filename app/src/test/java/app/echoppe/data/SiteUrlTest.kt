package app.echoppe.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiteUrlTest {
    @Test fun bareDomainGetsHttps() = assertEquals("https://amazon.fr/", SiteUrl.normalize("amazon.fr"))

    @Test fun httpBecomesHttps() = assertEquals("https://www.leroymerlin.fr/", SiteUrl.normalize("http://www.leroymerlin.fr"))

    @Test fun keepsPathQueryAndPort() =
        assertEquals("https://shop.example.com:8443/fr/cat?id=3", SiteUrl.normalize("  https://Shop.Example.com:8443/fr/cat?id=3 "))

    @Test fun rejectsNonWeb() {
        assertNull(SiteUrl.normalize(""))
        assertNull(SiteUrl.normalize("amazon"))
        assertNull(SiteUrl.normalize("ftp://amazon.fr"))
        assertNull(SiteUrl.normalize("amazon fr"))
        assertNull(SiteUrl.normalize("intent://amazon.fr"))
    }

    @Test fun defaultNames() {
        assertEquals("Leroymerlin", SiteUrl.defaultName("https://www.leroymerlin.fr/"))
        assertEquals("Amazon", SiteUrl.defaultName("https://amazon.co.uk/"))
        assertEquals("Decathlon", SiteUrl.defaultName("https://decathlon.fr/"))
    }

    @Test fun idsAreRandom() {
        val ids = List(100) { Site.newId() }.toSet()
        assertEquals(100, ids.size)
        assert(ids.all { it.length == 10 && it.all { c -> c.isLowerCase() || c.isDigit() } })
    }
}
