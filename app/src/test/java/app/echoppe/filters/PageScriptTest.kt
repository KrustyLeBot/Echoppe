package app.echoppe.filters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageScriptTest {
    @Test fun nothingToDo() = assertNull(PageScript.build(emptyList(), null))

    @Test fun cssAndCookieScript() {
        val site = SiteCosmetics(listOf(".ad", "#pub"), listOf(SiteCosmetics.Rule("amazon.fr", "#nav-app-banner-container", emptyList())))
        val js = PageScript.build(listOf(site), "/*cookies*/")!!
        assertTrue(js.contains(".ad,#pub{display:none!important}"))
        assertTrue(js.contains("[\"amazon.fr\",\"#nav-app-banner-container{display:none!important}\",[]]"))
        assertTrue(js.endsWith("/*cookies*/"))
    }

    @Test fun quotingIsSafeInsideAScript() {
        assertEquals("\"a\\\"b\\\\c\\u003c/script>\\n\"", PageScript.quote("a\"b\\c</script>\n"))
    }
}
