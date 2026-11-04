package app.echoppe.data

import app.echoppe.filters.Category
import java.net.URI
import java.security.SecureRandom

/**
 * A shopping site the user added. [id] names the site's own WebView profile (cookies, storage,
 * cache), so it never changes: renaming or moving the site keeps its logins.
 */
data class Site(
    val id: String,
    val name: String,
    val url: String,
    val desktop: Boolean = false,
    val blockAds: Boolean = true,
    val blockTrackers: Boolean = true,
    val rejectCookies: Boolean = true,
) {
    val host: String get() = SiteUrl.host(url) ?: url

    fun blocks(category: Category): Boolean = when (category) {
        Category.ADS -> blockAds
        Category.TRACKERS -> blockTrackers
        Category.COOKIES -> rejectCookies
    }

    companion object {
        private val random = SecureRandom()
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

        fun newId(): String = String(CharArray(10) { ALPHABET[random.nextInt(ALPHABET.length)] })
    }
}

/** What the user types in the address field, made into a site address. */
object SiteUrl {
    /** "amazon.fr", "www.amazon.fr/" or "http://…" become an https URL; null when it is not a web address. */
    fun normalize(input: String): String? {
        val text = input.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        val withScheme = when {
            text.startsWith("https://", ignoreCase = true) -> text
            text.startsWith("http://", ignoreCase = true) -> "https://" + text.substring(7)
            text.contains("://") -> return null
            else -> "https://$text"
        }
        val uri = try { URI(withScheme) } catch (e: Exception) { return null }
        val host = uri.host?.lowercase() ?: return null
        if (!host.contains('.') || host.startsWith('.') || host.endsWith('.')) return null
        val path = uri.rawPath.orEmpty().ifEmpty { "/" }
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        val port = if (uri.port > 0) ":${uri.port}" else ""
        return "https://$host$port$path$query"
    }

    fun host(url: String): String? = try { URI(url).host?.lowercase() } catch (e: Exception) { null }

    /** Default tab name: "www.leroymerlin.fr" gives "Leroymerlin". */
    fun defaultName(url: String): String {
        val host = host(url) ?: return url
        val labels = host.split('.').filter { it.isNotEmpty() }
        val main = when {
            labels.size >= 3 && labels[labels.size - 2] in SECOND_LEVEL -> labels[labels.size - 3]
            labels.size >= 2 -> labels[labels.size - 2]
            else -> labels.firstOrNull() ?: host
        }
        return main.replaceFirstChar { it.uppercase() }
    }

    private val SECOND_LEVEL = setOf("co", "com", "org", "net", "gouv", "ac")
}
