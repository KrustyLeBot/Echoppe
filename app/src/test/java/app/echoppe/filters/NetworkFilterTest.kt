package app.echoppe.filters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkFilterTest {
    private fun set(vararg lines: String) = FilterSet.parse(listOf(lines.joinToString("\n"))).network

    private fun NetworkFilter.verdict(url: String, page: String = "shop.fr", type: Int = RequestType.UNKNOWN) =
        match(url, java.net.URI(url).host, page, type)

    @Test fun plainHostBlocksItAndSubdomains() {
        val f = set("||ads.example.com^")
        assertEquals(Verdict.BLOCK, f.verdict("https://ads.example.com/x.js"))
        assertEquals(Verdict.BLOCK, f.verdict("https://eu.ads.example.com/x.js"))
        assertEquals(Verdict.NONE, f.verdict("https://myads.example.com/x.js"))
        assertEquals(Verdict.NONE, f.verdict("https://example.com/ads.example.com"))
    }

    @Test fun pathPatternsWithWildcardsAndSeparators() {
        val f = set("/banner/ad-*.gif", "&ad_type=", "|https://track.", "||cdn.shop.fr/pixel^")
        assertEquals(Verdict.BLOCK, f.verdict("https://cdn.x.com/banner/ad-300x250.gif"))
        assertEquals(Verdict.NONE, f.verdict("https://cdn.x.com/banner/logo.gif"))
        assertEquals(Verdict.BLOCK, f.verdict("https://x.com/p?a=1&ad_type=video"))
        assertEquals(Verdict.BLOCK, f.verdict("https://track.example.net/t"))
        assertEquals(Verdict.NONE, f.verdict("https://x.com/?u=https://track.example.net"))
        assertEquals(Verdict.BLOCK, f.verdict("https://cdn.shop.fr/pixel?x=1"))
        assertEquals(Verdict.NONE, f.verdict("https://cdn.shop.fr/pixels.png"))
    }

    @Test fun thirdPartyOption() {
        val f = set("||cdn.tracker.net^\$third-party")
        assertEquals(Verdict.BLOCK, f.verdict("https://cdn.tracker.net/a.js", page = "shop.fr"))
        assertEquals(Verdict.NONE, f.verdict("https://cdn.tracker.net/a.js", page = "www.tracker.net"))
    }

    @Test fun domainOption() {
        val f = set("/sponsored/\$domain=shop.fr|~blog.shop.fr")
        assertEquals(Verdict.BLOCK, f.verdict("https://img.x.com/sponsored/1.png", page = "www.shop.fr"))
        assertEquals(Verdict.NONE, f.verdict("https://img.x.com/sponsored/1.png", page = "blog.shop.fr"))
        assertEquals(Verdict.NONE, f.verdict("https://img.x.com/sponsored/1.png", page = "other.fr"))
    }

    @Test fun typeOptions() {
        val f = set("||x.com/widget^\$script", "||y.com^\$image")
        assertEquals(Verdict.BLOCK, f.verdict("https://x.com/widget/v1.js", type = RequestType.SCRIPT))
        assertEquals(Verdict.NONE, f.verdict("https://x.com/widget/v1.js", type = RequestType.IMAGE))
        // A fetch without hints may be a script.
        assertEquals(Verdict.BLOCK, f.verdict("https://x.com/widget?a", type = RequestType.UNKNOWN))
        assertEquals(Verdict.NONE, f.verdict("https://y.com/a", type = RequestType.UNKNOWN))
        assertEquals(Verdict.BLOCK, f.verdict("https://y.com/a.png", type = RequestType.IMAGE))
    }

    @Test fun exceptionsAndImportant() {
        val f = set("||ads.net^", "@@||ads.net/needed.js", "||evil.net^\$important", "@@||evil.net^")
        assertEquals(Verdict.ALLOW, f.verdict("https://ads.net/needed.js"))
        assertEquals(Verdict.BLOCK, f.verdict("https://ads.net/other.js"))
        assertEquals(Verdict.BLOCK, f.verdict("https://evil.net/x"))
    }

    @Test fun regexRules() {
        val f = set("/^https:\\/\\/[a-z]{8}\\.cloudfront\\.net\\/ad/")
        assertEquals(Verdict.BLOCK, f.verdict("https://abcdefgh.cloudfront.net/ad/1"))
        assertEquals(Verdict.NONE, f.verdict("https://abc.cloudfront.net/ad/1"))
    }

    @Test fun pageLevelExceptions() {
        val f = set("@@||bank.fr^\$document", "@@||shop.fr^\$generichide", "@@||news.fr^\$elemhide")
        assertTrue(f.pageExempt("https://www.bank.fr/pay", "www.bank.fr"))
        assertFalse(f.pageExempt("https://shop.fr/", "shop.fr"))
        assertTrue(f.noGenericHide("https://shop.fr/", "shop.fr"))
        assertTrue(f.noElemHide("https://news.fr/a", "news.fr"))
        assertFalse(f.noElemHide("https://shop.fr/", "shop.fr"))
    }

    @Test fun unsupportedRulesAreLeftOut() {
        assertNull(NetworkRule.parse("||x.com^\$csp=script-src 'none'"))
        assertNull(NetworkRule.parse("||x.com/a.js\$redirect=noopjs"))
        assertNull(NetworkRule.parse("||x.com^\$popup"))
        assertNull(NetworkRule.parse("! comment"))
        assertNull(NetworkRule.parse("[Adblock Plus 2.0]"))
        assertNull(NetworkRule.parse("||x.com^\$document"))
    }

    @Test fun tokens() {
        assertEquals(listOf("ads", "example", "com"), NetworkRule.parse("||ads.example.com^")!!.tokens())
        assertEquals(emptyList<String>(), NetworkRule.parse("ads")!!.tokens())
        assertEquals(listOf("banner", "ad"), NetworkRule.parse("/banner/ad-*.gif")!!.tokens())
    }

    @Test fun pingRulesDoNotCatchUnknownRequests() {
        val f = set("*\$ping,third-party")
        assertEquals(Verdict.NONE, f.verdict("https://www.paypal.com/sdk/js?client-id=x"))
    }
}
