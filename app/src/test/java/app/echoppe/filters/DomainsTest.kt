package app.echoppe.filters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainsTest {
    @Test fun registrable() {
        assertEquals("amazon.fr", Domains.registrable("www.amazon.fr"))
        assertEquals("amazon.co.uk", Domains.registrable("smile.amazon.co.uk"))
        assertEquals("fnac.com", Domains.registrable("fnac.com"))
    }

    @Test fun sameSite() {
        assertTrue(Domains.sameSite("m.media-amazon.com", "images.media-amazon.com"))
        assertFalse(Domains.sameSite("www.amazon.fr", "m.media-amazon.com"))
    }

    @Test fun entities() {
        assertTrue(Domains.matches("www.amazon.fr", "amazon.*"))
        assertTrue(Domains.matches("www.amazon.co.uk", "amazon.*"))
        assertFalse(Domains.matches("www.notamazon.fr", "amazon.*"))
    }

    @Test fun requestTypes() {
        assertEquals(RequestType.SCRIPT, RequestType.infer("https://x.com/a/b.js?v=1", "*/*"))
        assertEquals(RequestType.IMAGE, RequestType.infer("https://x.com/p", "image/avif,image/webp,*/*"))
        assertEquals(RequestType.STYLESHEET, RequestType.infer("https://x.com/s", "text/css,*/*;q=0.1"))
        assertEquals(RequestType.UNKNOWN, RequestType.infer("https://x.com/api/v1/items", "*/*"))
    }
}
