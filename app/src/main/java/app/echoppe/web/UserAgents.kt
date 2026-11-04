package app.echoppe.web

/**
 * User agents built from the WebView's own. The default one says "WebView" (`; wv)` and
 * `Version/4.0`), which sites use to push their app harder; without those markers it reads as
 * Chrome on Android, the most common browser, so it singles nobody out.
 */
object UserAgents {
    fun mobile(default: String): String = default
        .replace("; wv)", ")")
        .replace(Regex("Version/\\S+ "), "")

    /** Chrome on desktop Linux, same Chrome version: for sites whose mobile version is worse. */
    fun desktop(default: String): String {
        val chrome = Regex("Chrome/(\\S+)").find(default)?.groupValues?.get(1) ?: "131.0.0.0"
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Safari/537.36"
    }
}
