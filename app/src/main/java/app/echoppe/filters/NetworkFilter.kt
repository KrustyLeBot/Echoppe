package app.echoppe.filters

/**
 * One network rule of an Adblock Plus / EasyList style list: `||ads.example.com^$third-party`,
 * `/banner/ad-*.gif`, `@@||cdn.example.com^$script,domain=shop.fr`, `/regex/`.
 */
class NetworkRule(
    val pattern: String,
    val exception: Boolean,
    val important: Boolean,
    private val matchCase: Boolean,
    /** null: any; true: third-party only; false: first-party only. */
    private val thirdParty: Boolean?,
    private val types: Int,
    include: List<String>,
    exclude: List<String>,
    /** `$document`: the whole page is exempted (exception rules only). */
    val document: Boolean,
    val elemHide: Boolean,
    val genericHide: Boolean,
) {
    private var regex: Regex? = null
    // Some rules list hundreds of domains: looked up by walking the page host's parents, not scanned.
    private val include = DomainSet(include)
    private val exclude = DomainSet(exclude)

    fun matches(url: String, lowerUrl: String, pageHost: String, type: Int, isThirdParty: Boolean): Boolean {
        if (types and type == 0) return false
        if (thirdParty != null && thirdParty != isThirdParty) return false
        if (!domainOk(pageHost)) return false
        return matchesUrl(url, lowerUrl)
    }

    fun domainOk(pageHost: String): Boolean {
        if (exclude.contains(pageHost)) return false
        return include.isEmpty || include.contains(pageHost)
    }

    fun matchesUrl(url: String, lowerUrl: String): Boolean {
        val r = regex ?: compile().also { regex = it }
        return r.containsMatchIn(if (matchCase) url else lowerUrl)
    }

    private fun compile(): Regex {
        if (pattern.length > 2 && pattern.startsWith('/') && pattern.endsWith('/')) {
            return Regex(pattern.substring(1, pattern.length - 1), if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE))
        }
        val p = if (matchCase) pattern else pattern.lowercase()
        val sb = StringBuilder()
        var i = 0
        var end = p.length
        if (p.startsWith("||")) {
            sb.append("^[a-z][a-z0-9+.-]*://(?:[^/?#]*\\.)?")
            i = 2
        } else if (p.startsWith('|')) {
            sb.append('^')
            i = 1
        }
        val anchoredEnd = end > i && p[end - 1] == '|'
        if (anchoredEnd) end--
        while (i < end) {
            when (val c = p[i]) {
                '*' -> sb.append(".*")
                '^' -> sb.append("(?:[^\\w.%-]|$)")
                else -> if (c.isLetterOrDigit() || c == '_') sb.append(c) else sb.append('\\').append(c)
            }
            i++
        }
        if (anchoredEnd) sb.append('$')
        return Regex(sb.toString())
    }

    /**
     * Literal runs that any matching URL contains as a whole token. A run qualifies when both its
     * ends are explicit boundaries in the pattern (not a wildcard, not the pattern's unanchored edge),
     * so it cannot be part of a longer word in the URL.
     */
    fun tokens(): List<String> {
        if (pattern.length > 2 && pattern.startsWith('/') && pattern.endsWith('/')) return emptyList()
        val p = pattern.lowercase()
        val out = ArrayList<String>(4)
        var i = 0
        while (i < p.length) {
            if (!isTokenChar(p[i])) { i++; continue }
            val start = i
            while (i < p.length && isTokenChar(p[i])) i++
            val leftOk = start > 0 && p[start - 1] != '*'
            val rightOk = i < p.length && p[i] != '*'
            if (leftOk && rightOk && i - start >= 2) out += p.substring(start, i)
        }
        return out
    }

    companion object {
        fun isTokenChar(c: Char) = c in 'a'..'z' || c in '0'..'9' || c == '%'

        // Rules whose action is not "block" (rewriting, CSP, redirects) are not supported and left out.
        private val UNSUPPORTED = setOf(
            "csp", "rewrite", "redirect", "redirect-rule", "removeparam", "queryprune", "replace", "header",
            "method", "permissions", "webrtc", "genericblock", "sitekey", "urltransform", "uritransform", "to", "denyallow",
            "cname", "badfilter", "empty", "mp4", "inline-script", "inline-font",
        )

        /** Parses one list line; null for comments, cosmetic rules and anything not supported. */
        fun parse(line: String): NetworkRule? {
            var text = line.trim()
            if (text.isEmpty() || text.startsWith('!') || text.startsWith('[')) return null
            if (text.contains("##") || text.contains("#@#") || text.contains("#?#") || text.contains("#$#") || text.contains("#%#")) return null
            val exception = text.startsWith("@@")
            if (exception) text = text.substring(2)

            var options = ""
            val isRegex = text.startsWith('/') && text.lastIndexOf('/') > 0 &&
                (text.endsWith('/') || text.substring(text.lastIndexOf('/') + 1).startsWith('$'))
            val dollar = if (isRegex) text.lastIndexOf("/$").let { if (it < 0) -1 else it + 1 } else text.lastIndexOf('$')
            if (dollar >= 0) {
                options = text.substring(dollar + 1)
                text = text.substring(0, dollar)
            }
            if (text.isEmpty() || text == "*") text = "*"

            var important = false
            var matchCase = false
            var thirdParty: Boolean? = null
            var include = 0
            var exclude = 0
            var popupOnly = false
            var sawType = false
            val domains = ArrayList<String>()
            val notDomains = ArrayList<String>()
            var document = false
            var elemHide = false
            var genericHide = false
            if (options.isNotEmpty()) {
                for (raw in options.split(',')) {
                    val opt = raw.trim().lowercase()
                    val neg = opt.startsWith('~')
                    val name = opt.removePrefix("~").substringBefore('=')
                    when {
                        name in UNSUPPORTED -> return null
                        name == "third-party" || name == "3p" -> thirdParty = !neg
                        name == "first-party" || name == "1p" -> thirdParty = neg
                        name == "important" -> important = true
                        name == "match-case" -> matchCase = true
                        name == "domain" || name == "from" -> raw.substringAfter('=').split('|').forEach {
                            val d = it.trim().lowercase()
                            if (d.startsWith('~')) notDomains += d.substring(1) else if (d.isNotEmpty()) domains += d
                        }
                        name == "document" || name == "doc" -> document = !neg
                        name == "elemhide" || name == "ehide" -> elemHide = true
                        name == "generichide" || name == "ghide" -> genericHide = true
                        name == "popup" || name == "popunder" -> { sawType = true; popupOnly = true }
                        name == "all" -> { include = RequestType.ALL; sawType = true }
                        name in RequestType.BY_NAME -> {
                            val bit = RequestType.BY_NAME.getValue(name)
                            if (neg) exclude = exclude or bit else { include = include or bit; sawType = true }
                        }
                        else -> return null
                    }
                }
            }
            // Pop-up rules act on new windows, which never open here.
            if (popupOnly && include == 0) return null
            val pageOnly = document || elemHide || genericHide
            if (pageOnly && !exception) return null
            var types = if (include != 0) include else RequestType.ALL
            types = types and exclude.inv()
            if (sawType && types == 0 && !pageOnly) return null
            return NetworkRule(
                pattern = text,
                exception = exception,
                important = important,
                matchCase = matchCase,
                thirdParty = thirdParty,
                types = if (pageOnly && include == 0) RequestType.ALL else types,
                include = domains,
                exclude = notDomains,
                document = document,
                elemHide = elemHide,
                genericHide = genericHide,
            )
        }
    }
}

/** Domains of a `domain=` option: plain ones hashed, entities (`amazon.*`) checked one by one. */
private class DomainSet(entries: List<String>) {
    private val plain = entries.filterNot { it.endsWith(".*") }.toHashSet()
    private val entities = entries.filter { it.endsWith(".*") }

    val isEmpty = plain.isEmpty() && entities.isEmpty()

    fun contains(host: String): Boolean {
        if (plain.isNotEmpty()) {
            var h = host
            while (true) {
                if (h in plain) return true
                val dot = h.indexOf('.')
                if (dot < 0) break
                h = h.substring(dot + 1)
            }
        }
        return entities.any { Domains.matches(host, it) }
    }
}

/** What the network rules say about one request. */
enum class Verdict { BLOCK, ALLOW, NONE }

/**
 * The network rules of a set of lists, indexed by token so a request only checks the few rules that
 * could match it. Plain `||host^` rules, most of every list, go to a host set.
 */
class NetworkFilter(rules: Iterable<NetworkRule>, plainHosts: Set<String>) {
    private val hosts = HashSet(plainHosts)
    private val blocks = TokenIndex()
    private val exceptions = TokenIndex()
    private val pageRules = ArrayList<NetworkRule>()

    val size: Int

    init {
        val list = rules.toList()
        // Each rule is filed under its rarest token, so a common one ("com", "js") does not drag in thousands.
        val candidates = list.map { it.tokens() }
        val frequency = HashMap<String, Int>()
        candidates.forEach { tokens -> tokens.forEach { frequency.merge(it, 1, Int::plus) } }
        list.forEachIndexed { i, r ->
            val token = candidates[i].filter { it !in COMMON }.minWithOrNull(compareBy<String> { frequency.getValue(it) }.thenByDescending { it.length })
            when {
                r.document || r.elemHide || r.genericHide -> pageRules += r
                r.exception -> exceptions.add(r, token)
                else -> blocks.add(r, token)
            }
        }
        size = hosts.size + list.size
    }

    /** For tests and diagnostics: the rule that blocks [url], or null. */
    fun blockingRule(url: String, host: String, pageHost: String, type: Int): String? {
        val lower = url.lowercase()
        val third = !Domains.sameSite(host, pageHost)
        if (hostListed(host)) return "||host^"
        return blocks.find(tokens(lower)) { it.matches(url, lower, pageHost, type, third) }?.pattern
    }

    /** For diagnostics: how many rules every request has to try. */
    val untokenedCount: Int get() = blocks.untokenedCount + exceptions.untokenedCount

    fun match(url: String, host: String, pageHost: String, type: Int): Verdict {
        val lower = url.lowercase()
        val third = !Domains.sameSite(host, pageHost)
        val tokens = tokens(lower)
        var blocked: NetworkRule? = null
        if (hostListed(host)) blocked = HOST_RULE
        if (blocked == null) blocked = blocks.find(tokens) { it.matches(url, lower, pageHost, type, third) }
        if (blocked == null) return Verdict.NONE
        if (blocked.important) return Verdict.BLOCK
        val allowed = exceptions.find(tokens) { it.matches(url, lower, pageHost, type, third) }
        return if (allowed != null) Verdict.ALLOW else Verdict.BLOCK
    }

    /** `@@…$document`: the page and everything it loads are left alone. */
    fun pageExempt(pageUrl: String, pageHost: String) = pageRule(pageUrl, pageHost) { it.document }

    /** `@@…$elemhide`: no element hiding on this page. */
    fun noElemHide(pageUrl: String, pageHost: String) = pageRule(pageUrl, pageHost) { it.elemHide || it.document }

    /** `@@…$generichide`: only the site's own hiding rules on this page. */
    fun noGenericHide(pageUrl: String, pageHost: String) = pageRule(pageUrl, pageHost) { it.genericHide }

    private fun pageRule(pageUrl: String, pageHost: String, kind: (NetworkRule) -> Boolean): Boolean {
        val lower = pageUrl.lowercase()
        return pageRules.any { kind(it) && it.domainOk(pageHost) && it.matchesUrl(pageUrl, lower) }
    }

    private fun hostListed(host: String): Boolean {
        var h = host.lowercase()
        while (true) {
            if (h in hosts) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
        }
    }

    private class TokenIndex {
        private val byToken = HashMap<String, MutableList<NetworkRule>>()
        private val untokened = ArrayList<NetworkRule>()
        val untokenedCount get() = untokened.size

        fun add(r: NetworkRule, token: String?) {
            if (token == null) untokened += r else byToken.getOrPut(token) { ArrayList(2) } += r
        }

        inline fun find(tokens: Collection<String>, test: (NetworkRule) -> Boolean): NetworkRule? {
            for (t in tokens) byToken[t]?.let { list -> for (r in list) if (test(r)) return r }
            for (r in untokened) if (test(r)) return r
            return null
        }
    }

    companion object {
        // In nearly every URL: useless to tell rules apart.
        private val COMMON = setOf("http", "https", "www", "com", "net", "org", "fr", "js", "html", "php", "cdn", "static", "api", "img", "images")
        private val HOST_RULE = NetworkRule("", false, false, false, null, RequestType.ALL, emptyList(), emptyList(), false, false, false)
        /** `||host^` with no option: blocked for every request to that host and its subdomains. */
        fun plainHost(line: String): String? {
            val t = line.trim()
            if (!t.startsWith("||")) return null
            val end = if (t.endsWith("^|")) t.length - 2 else if (t.endsWith('^')) t.length - 1 else return null
            if (end <= 2) return null
            for (i in 2 until end) {
                val c = t[i]
                if (!(c in 'a'..'z' || c in '0'..'9' || c == '.' || c == '-' || c in 'A'..'Z')) return null
            }
            if (t[2] == '.' || t[2] == '-' || t[end - 1] == '.' || t[end - 1] == '-') return null
            return t.substring(2, end).lowercase()
        }

        fun tokens(lowerUrl: String): Set<String> {
            val out = HashSet<String>()
            var i = 0
            while (i < lowerUrl.length) {
                if (!NetworkRule.isTokenChar(lowerUrl[i])) { i++; continue }
                val start = i
                while (i < lowerUrl.length && NetworkRule.isTokenChar(lowerUrl[i])) i++
                if (i - start >= 2) out += lowerUrl.substring(start, i)
            }
            return out
        }
    }
}
