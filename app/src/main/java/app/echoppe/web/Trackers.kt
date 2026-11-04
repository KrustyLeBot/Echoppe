package app.echoppe.web

/**
 * Third-party tracker domains (analytics, ad networks, social pixels, session replay), one per line in
 * `assets/trackers.txt`. A request to one of them, or to any subdomain, is answered with an empty
 * response. Anti-fraud, payment, captcha and consent services are left out on purpose: blocking them
 * breaks checkouts and logins.
 */
class Trackers(domains: Collection<String>) {
    private val set = domains.map { it.trim().lowercase() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toHashSet()

    val size: Int get() = set.size

    /** The listed domain [host] belongs to, or null. A site is never blocked on its own domain. */
    fun match(host: String, siteHost: String): String? {
        var h = host.lowercase().trimEnd('.')
        while (true) {
            if (h in set) return if (siteHost == h || siteHost.endsWith(".$h")) null else h
            val dot = h.indexOf('.')
            if (dot < 0) return null
            h = h.substring(dot + 1)
        }
    }

    companion object {
        fun parse(text: String) = Trackers(text.lines())
    }
}
