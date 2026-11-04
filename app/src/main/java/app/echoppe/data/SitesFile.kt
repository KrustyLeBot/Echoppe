package app.echoppe.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * `sites.json`: `{"format_version": 1, "sites": [{"id", "name", "url", "desktop", "block_ads", "block_trackers", "reject_cookies"}]}`,
 * in tab order. Unknown fields are ignored, missing optional ones take their defaults.
 */
object SitesFile {
    const val NAME = "sites.json"
    const val FORMAT_VERSION = 1

    fun encode(sites: List<Site>): String =
        JSONObject().put("format_version", FORMAT_VERSION).put("sites", toJson(sites)).toString(2)

    /** The sites part alone, to tell whether a write would change anything. */
    fun toJson(sites: List<Site>): JSONArray = JSONArray().also { arr ->
        sites.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("url", it.url)
                    .put("desktop", it.desktop)
                    .put("block_ads", it.blockAds)
                    .put("block_trackers", it.blockTrackers)
                    .put("reject_cookies", it.rejectCookies)
            )
        }
    }

    fun decode(text: String): List<Site> {
        val root = JSONObject(text)
        val version = root.optInt("format_version", 1)
        if (version > FORMAT_VERSION) throw IllegalStateException("format_version $version : mets l'application à jour")
        val arr = root.optJSONArray("sites") ?: return emptyList()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Site(
                id = o.getString("id"),
                name = o.getString("name"),
                url = o.getString("url"),
                desktop = o.optBoolean("desktop", false),
                blockAds = o.optBoolean("block_ads", true),
                blockTrackers = o.optBoolean("block_trackers", true),
                rejectCookies = o.optBoolean("reject_cookies", true),
            )
        }.distinctBy { it.id }
    }

    fun merge(theirs: List<Site>, mine: List<Site>): List<Site> = theirs + mine.filter { m -> theirs.none { it.id == m.id } }
}
