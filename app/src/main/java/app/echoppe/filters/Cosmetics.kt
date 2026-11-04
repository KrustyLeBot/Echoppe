package app.echoppe.filters

/**
 * Element hiding rules: `##.ad-banner` (every site), `amazon.fr###nav-app-banner-container` (some
 * sites), `shop.fr#@#.ad-banner` (exception). Extended syntaxes (`#?#`, `##+js(…)`, `:has-text()`…)
 * are not supported and left out.
 */
class Cosmetics {
    private val generic = LinkedHashSet<String>()
    private val genericOff = HashSet<String>()
    private val specific = ArrayList<Specific>()
    private val exceptions = ArrayList<Specific>()

    private class Specific(val selector: String, val domains: List<String>, val notDomains: List<String>)

    val size: Int get() = generic.size + specific.size

    /** @return true when [line] was a cosmetic rule (supported or not). */
    fun add(line: String): Boolean {
        val text = line.trim()
        if (text.isEmpty() || text.startsWith('!') || text.indexOf('#') < 0) return false
        val isException = text.contains("#@#")
        val sep = if (isException) "#@#" else "##"
        val at = text.indexOf(sep)
        if (at < 0) return text.contains("#?#") || text.contains("#$#") || text.contains("#%#")
        val selector = text.substring(at + sep.length).trim()
        if (selector.isEmpty() || selector.startsWith("+js") || selector.startsWith('^') || (selector.indexOf(':') >= 0 || selector.contains("[-ext-")) && EXTENDED.containsMatchIn(selector)) return true
        val domains = ArrayList<String>()
        val notDomains = ArrayList<String>()
        text.substring(0, at).split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.forEach {
            if (it.startsWith('~')) notDomains += it.substring(1) else domains += it
        }
        when {
            isException && domains.isEmpty() -> genericOff += selector
            isException -> exceptions += Specific(selector, domains, notDomains)
            // `~site##sel` alone means "everywhere but there": kept out rather than risk hiding on the excluded site.
            domains.isEmpty() && notDomains.isEmpty() -> generic += selector
            domains.isNotEmpty() -> specific += Specific(selector, domains, notDomains)
        }
        return true
    }

    /**
     * The rules for one site's tab: generic selectors (minus the site's exceptions, none with
     * [genericHide]) and the site's own, keyed by the host they apply to.
     */
    fun forSite(siteHost: String, genericHide: Boolean): SiteCosmetics {
        val reg = Domains.registrable(siteHost)
        // A rule's domain concerns the tab when it contains the site, or is one of the site's subdomains
        // (the injected script then checks the exact host).
        fun related(d: String) = Domains.matches(siteHost, d) || Domains.matches(reg, d) || Domains.within(d, reg)
        val off = HashSet(genericOff)
        exceptions.filter { e -> e.domains.any(::related) }.forEach { off += it.selector }
        val own = ArrayList<SiteCosmetics.Rule>()
        for (s in specific) {
            if (s.selector in off) continue
            for (d in s.domains) {
                if (!related(d)) continue
                // Entities (amazon.*) become this site's own domain.
                val host = if (d.endsWith(".*")) reg else d
                own += SiteCosmetics.Rule(host, s.selector, s.notDomains)
            }
        }
        val gen = if (genericHide) emptyList() else generic.filter { it !in off }
        return SiteCosmetics(gen, own)
    }

    private companion object {
        val EXTENDED = Regex(
            ":(has-text|xpath|matches-css|matches-css-before|matches-css-after|matches-attr|matches-path|matches-media|" +
                "min-text-length|upward|remove|remove-attr|remove-class|style|watch-attr|others|if|if-not|nth-ancestor|contains|" +
                "-abp-[a-z-]+)\\(|\\[-ext-|:-abp-",
        )
    }
}

/** What one tab hides: [generic] everywhere, [own] on the hosts they name. */
class SiteCosmetics(val generic: List<String>, val own: List<Rule>) {
    class Rule(val host: String, val selector: String, val notHosts: List<String>)

    val isEmpty: Boolean get() = generic.isEmpty() && own.isEmpty()
}
