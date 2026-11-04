package app.echoppe.filters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CosmeticsTest {
    private fun cosmetics(vararg lines: String) = FilterSet.parse(listOf(lines.joinToString("\n"))).cosmetics

    @Test fun amazonAppBanner() {
        val c = cosmetics("amazon.ca,amazon.fr,amazon.de###nav-app-banner-container", "##.ad-box")
        val site = c.forSite("www.amazon.fr", genericHide = false)
        assertEquals(listOf(".ad-box"), site.generic)
        assertEquals(listOf("amazon.fr" to "#nav-app-banner-container"), site.own.map { it.host to it.selector })
        assertTrue(c.forSite("www.fnac.com", false).own.isEmpty())
    }

    @Test fun entitiesMatchEveryTld() {
        val c = cosmetics("amazon.*##.smart-banner")
        assertEquals(listOf("amazon.co.uk"), c.forSite("www.amazon.co.uk", false).own.map { it.host })
    }

    @Test fun subdomainRulesAreKeptForTheScriptToCheck() {
        val c = cosmetics("smile.shop.fr##.promo", "other.fr##.promo2")
        assertEquals(listOf("smile.shop.fr"), c.forSite("www.shop.fr", false).own.map { it.host })
    }

    @Test fun exceptionsRemoveRules() {
        val c = cosmetics("##.ad", "##.banner", "shop.fr#@#.ad", "#@#.banner", "shop.fr,~m.shop.fr##.x")
        val site = c.forSite("shop.fr", false)
        assertTrue(site.generic.isEmpty())
        assertEquals(listOf("m.shop.fr"), site.own.single().notHosts)
        assertEquals(listOf(".ad"), c.forSite("other.fr", false).generic)
    }

    @Test fun genericHideKeepsOnlyTheSiteRules() {
        val c = cosmetics("##.ad", "shop.fr##.promo")
        val site = c.forSite("shop.fr", genericHide = true)
        assertTrue(site.generic.isEmpty())
        assertEquals(1, site.own.size)
    }

    @Test fun extendedSyntaxIsLeftOut() {
        val c = cosmetics("shop.fr##div:has-text(Pub)", "shop.fr##+js(abort-on-property-read, x)", "shop.fr#?#.a:-abp-has(.b)", "##.ok")
        val site = c.forSite("shop.fr", false)
        assertEquals(listOf(".ok"), site.generic)
        assertTrue(site.own.isEmpty())
    }
}
