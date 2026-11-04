package app.echoppe.filters

/** Host helpers for "same site" decisions: third-party requests, last-page restore, cosmetic scopes. */
object Domains {
    // Second-level public suffixes that matter for shops; enough to tell amazon.co.uk from bbc.co.uk.
    private val SECOND_LEVEL = setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk", "com.au", "net.au", "org.au", "co.nz", "co.jp", "ne.jp", "or.jp",
        "com.br", "com.mx", "com.ar", "com.tr", "com.cn", "com.hk", "com.tw", "com.sg", "co.in", "co.kr", "co.za",
        "com.be", "gouv.fr", "asso.fr", "com.pl", "co.il", "com.ua",
    )

    /** "www.amazon.co.uk" → "amazon.co.uk"; "m.leroymerlin.fr" → "leroymerlin.fr". */
    fun registrable(host: String): String {
        val h = host.lowercase().trimEnd('.')
        val labels = h.split('.')
        if (labels.size <= 2) return h
        val lastTwo = labels.takeLast(2).joinToString(".")
        val keep = if (lastTwo in SECOND_LEVEL) 3 else 2
        return labels.takeLast(keep).joinToString(".")
    }

    fun sameSite(a: String, b: String) = registrable(a) == registrable(b)

    /** [host] is [domain] or one of its subdomains. */
    fun within(host: String, domain: String) = host == domain || host.endsWith(".$domain")

    /**
     * A filter's domain entry: a plain domain, or an "entity" like `amazon.*` that stands for every
     * public suffix (amazon.fr, amazon.co.uk…).
     */
    fun matches(host: String, entry: String): Boolean {
        if (!entry.endsWith(".*")) return within(host, entry)
        val name = entry.removeSuffix(".*")
        val reg = registrable(host)
        val suffix = reg.substringAfter('.', "")
        if (suffix.isEmpty()) return false
        val base = host.removeSuffix(".$suffix")
        return base == name || base.endsWith(".$name")
    }
}
