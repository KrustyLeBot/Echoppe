package app.echoppe.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class TrackersTest {
    private val trackers = Trackers.parse("# comment\n\ngoogle-analytics.com\ncriteo.com\nConnect.Facebook.net\namazon-adsystem.com\n")

    @Test fun matchesDomainAndSubdomains() {
        assertEquals("google-analytics.com", trackers.match("google-analytics.com", "amazon.fr"))
        assertEquals("google-analytics.com", trackers.match("www.google-analytics.com", "amazon.fr"))
        assertEquals("criteo.com", trackers.match("static.criteo.com", "cdiscount.com"))
        assertEquals("connect.facebook.net", trackers.match("connect.facebook.net", "x.fr"))
    }

    @Test fun lookalikesAreNotMatched() {
        assertNull(trackers.match("notcriteo.com", "x.fr"))
        assertNull(trackers.match("facebook.net", "x.fr"))
        assertNull(trackers.match("google.com", "x.fr"))
    }

    @Test fun aSiteIsNeverBlockedOnItsOwnDomain() {
        assertNull(trackers.match("static.criteo.com", "www.criteo.com"))
    }

    @Test fun commentsAndBlanksAreIgnored() = assertEquals(4, trackers.size)

    @Test fun shippedListParsesAndSparesWhatCheckoutsNeed() {
        val list = Trackers.parse(File("src/main/assets/trackers.txt").readText())
        assert(list.size > 100)
        for (host in listOf("www.paypal.com", "js.stripe.com", "checkoutshopper-live.adyen.com", "www.google.com", "www.gstatic.com",
            "cdn.cookielaw.org", "sdk.privacy-center.org", "x.klarnacdn.net", "d1.cloudfront.net", "m.media-amazon.com")) {
            assertNull(host, list.match(host, "shop.fr"))
        }
        assertEquals("googletagmanager.com", list.match("www.googletagmanager.com", "shop.fr"))
    }
}
