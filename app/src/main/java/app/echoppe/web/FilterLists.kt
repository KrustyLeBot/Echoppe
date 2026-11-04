package app.echoppe.web

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.echoppe.data.AtomicFiles
import app.echoppe.filters.Category
import app.echoppe.filters.FilterSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A public filter list, downloaded and kept up to date by the app. */
class ListDef(val id: String, val title: String, val url: String, val category: Category)

/**
 * The public filter lists (the ones Adblock Plus and uBlock Origin use), downloaded every few days to
 * private storage and parsed in the background into one [FilterSet] per [Category]. The app's own
 * rules (`assets/echoppe-filters.txt`, `assets/trackers.txt`) are always in. Until the first parse is
 * done ([ready] false) no tab loads, so no page ever runs unfiltered.
 */
class FilterLists(private val context: Context, private val scope: CoroutineScope) {
    private val dir = File(context.filesDir, "filters")
    private val prefs = context.getSharedPreferences("filters", Context.MODE_PRIVATE)

    /** Read from WebView's network threads. */
    @Volatile
    var sets: Map<Category, FilterSet> = emptyMap()
        private set

    var ready by mutableStateOf(false)
        private set
    /** Bumped when [sets] changes, so the tabs rebuild their page scripts. */
    var version by mutableIntStateOf(0)
        private set
    var updating by mutableStateOf(false)
        private set
    var lastUpdate by mutableLongStateOf(prefs.getLong(KEY_UPDATED, 0))
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var ruleCount by mutableIntStateOf(0)
        private set

    val cookieRefusal: String by lazy { asset("cookie-refusal.js") }

    fun start() {
        scope.launch {
            // The very first start downloads the lists before any page opens (a few seconds, once).
            if (LISTS.none { File(dir, "${it.id}.txt").exists() }) download()
            rebuild()
            ready = true
            if (System.currentTimeMillis() - lastUpdate > MAX_AGE_MS) update()
        }
    }

    /** Downloads every list again; a list that fails keeps its previous copy. */
    fun update() {
        if (updating) return
        scope.launch { if (download()) rebuild() }
    }

    /** @return true when at least one list was downloaded. */
    private suspend fun download(): Boolean {
        updating = true
        val failed = withContext(Dispatchers.IO) {
            dir.mkdirs()
            LISTS.mapNotNull { def ->
                try {
                    AtomicFiles.write(File(dir, "${def.id}.txt"), fetch(def.url))
                    null
                } catch (e: Exception) {
                    def.title
                }
            }
        }
        error = if (failed.isEmpty()) null else "Échec du téléchargement : ${failed.joinToString(", ")}"
        val any = failed.size < LISTS.size
        if (any) {
            lastUpdate = System.currentTimeMillis()
            prefs.edit().putLong(KEY_UPDATED, lastUpdate).apply()
        }
        updating = false
        return any
    }

    private suspend fun rebuild() {
        val built = withContext(Dispatchers.Default) {
            // One category per core: the first start waits on this.
            Category.entries.map { cat -> async { cat to FilterSet.parse(texts(cat)) } }.awaitAll().toMap()
        }
        sets = built
        ruleCount = built.values.sumOf { it.network.size + it.cosmetics.size }
        version++
    }

    private fun texts(cat: Category): List<String> =
        LISTS.filter { it.category == cat }.mapNotNull { def -> File(dir, "${def.id}.txt").takeIf { it.exists() }?.readText() } +
            when (cat) {
                Category.ADS -> listOf(asset("echoppe-filters.txt"))
                Category.TRACKERS -> listOf(asset("trackers.txt").lines().filter { it.isNotBlank() && !it.startsWith('#') }.joinToString("\n") { "||${it.trim()}^" })
                Category.COOKIES -> emptyList()
            }

    private fun asset(name: String) = context.assets.open(name).bufferedReader().use { it.readText() }

    private fun fetch(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "Echoppe")
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            // A captive portal or an error page must not replace a good list.
            if (text.length < 1000 || !(text.startsWith("[Adblock") || text.startsWith("!"))) throw IOException("Liste invalide")
            return text
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val KEY_UPDATED = "updated"
        private const val MAX_AGE_MS = 4 * 24 * 60 * 60 * 1000L

        val LISTS = listOf(
            ListDef("easylist", "EasyList", "https://easylist.to/easylist/easylist.txt", Category.ADS),
            ListDef("liste_fr", "Liste FR", "https://easylist-downloads.adblockplus.org/liste_fr.txt", Category.ADS),
            ListDef("mobile", "Fanboy's Mobile Notifications", "https://secure.fanboy.co.nz/fanboy-mobile-notifications.txt", Category.ADS),
            ListDef("easyprivacy", "EasyPrivacy", "https://easylist.to/easylist/easyprivacy.txt", Category.TRACKERS),
            ListDef("cookies", "EasyList Cookie", "https://secure.fanboy.co.nz/fanboy-cookiemonster.txt", Category.COOKIES),
        )
    }
}
