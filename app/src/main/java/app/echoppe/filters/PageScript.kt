package app.echoppe.filters

/**
 * The script injected at the start of every page of a tab: a style sheet hiding what the lists name
 * (ads, app banners, cookie banners), then the cookie refusal when it is on. Generic selectors go in
 * groups (one invalid selector only loses its group); the site's own rules go one by one, each checked
 * against the page's host.
 */
object PageScript {
    private const val GROUP = 20
    private const val HIDE = "{display:none!important}"

    fun build(cosmetics: List<SiteCosmetics>, cookieRefusal: String?): String? {
        val generic = cosmetics.flatMap { it.generic }.distinct()
        val own = cosmetics.flatMap { it.own }
        if (generic.isEmpty() && own.isEmpty() && cookieRefusal == null) return null
        val css = StringBuilder()
        generic.chunked(GROUP).forEach { css.append(it.joinToString(",")).append(HIDE) }
        val rules = own.joinToString(",", "[", "]") { r ->
            "[${quote(r.host)},${quote(r.selector + HIDE)},${r.notHosts.joinToString(",", "[", "]") { quote(it) }}]"
        }
        return """
            (function () {
              var h = location.hostname, css = ${quote(css.toString())}, own = $rules;
              function on(d) { return h === d || h.slice(-d.length - 1) === '.' + d; }
              for (var i = 0; i < own.length; i++) if (on(own[i][0]) && !own[i][2].some(on)) css += own[i][1];
              if (!css) return;
              var s = document.createElement('style');
              s.textContent = css;
              // At document start the page may not even have its <html> element yet.
              function add() { var root = document.head || document.documentElement; if (root) root.appendChild(s); return !!root; }
              if (!add()) new MutationObserver(function (m, o) { if (add()) o.disconnect(); }).observe(document, { childList: true, subtree: true });
            })();
        """.trimIndent() + (cookieRefusal?.let { "\n$it" } ?: "")
    }

    fun quote(s: String): String {
        val sb = StringBuilder(s.length + 2).append('"')
        for (c in s) when {
            c == '"' -> sb.append("\\\"")
            c == '\\' -> sb.append("\\\\")
            c == '\n' -> sb.append("\\n")
            c == '\r' -> sb.append("\\r")
            c == ' ' -> sb.append("\\u2028")
            c == ' ' -> sb.append("\\u2029")
            c < ' ' -> sb.append("\\u%04x".format(c.code))
            c == '<' -> sb.append("\\u003c")
            else -> sb.append(c)
        }
        return sb.append('"').toString()
    }
}
