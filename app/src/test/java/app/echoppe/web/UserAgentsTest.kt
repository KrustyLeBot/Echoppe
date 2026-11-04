package app.echoppe.web

import org.junit.Assert.assertEquals
import org.junit.Test

class UserAgentsTest {
    private val webView = "Mozilla/5.0 (Linux; Android 14; Pixel 8; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/131.0.6778.135 Mobile Safari/537.36"

    @Test fun mobileLooksLikeChrome() = assertEquals(
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.135 Mobile Safari/537.36",
        UserAgents.mobile(webView),
    )

    @Test fun desktopKeepsTheChromeVersion() = assertEquals(
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.135 Safari/537.36",
        UserAgents.desktop(webView),
    )
}
