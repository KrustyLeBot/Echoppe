package app.echoppe.web

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.echoppe.data.Site
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebResponse

/** What the UI shows about one tab. */
class TabState {
    var progress by mutableIntStateOf(100)
    var url by mutableStateOf("")
    var canGoBack by mutableStateOf(false)
}

/**
 * The open tabs, one Gecko session per site, created the first time a tab is shown and kept so that
 * switching tabs never reloads a page. Each site browses in its own context (`site_<id>`): its own
 * cookies, storage and cache, so no site can see another's identifiers. Each tab reopens on its last
 * page. Nothing a page does can open another app (see [Navigation]).
 */
class Tabs(
    private val activity: Activity,
    private val runtime: GeckoRuntime,
    private val extensions: Extensions,
    private val prompts: Prompts,
    private val downloads: Downloads,
    private val onNotice: (String) -> Unit,
) {
    private val sessions = HashMap<String, GeckoSession>()
    private val configs = HashMap<String, Site>()
    private val states = HashMap<String, TabState>()
    private val lastPages = activity.getSharedPreferences("last_pages", Context.MODE_PRIVATE)
    private var shown: String? = null

    fun state(id: String): TabState = states.getOrPut(id) { TabState() }

    fun session(id: String): GeckoSession? = sessions[id]

    /** Puts the site's tab in [view], creating it on first use; a changed address or mode is applied. */
    fun show(site: Site, view: GeckoView) {
        val session = sessions[site.id]?.also { apply(it, site) } ?: create(site)
        if (shown != site.id) {
            shown?.let { old -> sessions[old]?.let { deactivate(it) } }
            shown = site.id
        }
        if (view.session !== session) {
            view.session?.let { view.releaseSession() }
            view.setSession(session)
        }
        session.setActive(true)
        session.setFocused(true)
        runtime.webExtensionController.setTabActive(session, true)
    }

    fun goBack(id: String) = sessions[id]?.goBack()

    fun reload(id: String) = sessions[id]?.reload()

    fun home(site: Site) = sessions[site.id]?.loadUri(site.url)

    /** Closes the tabs of sites no longer in the list (e.g. removed on another phone). */
    fun retain(ids: Set<String>) {
        (sessions.keys - ids).forEach { close(it) }
    }

    /** Forgets the site entirely: its tab, its cookies, storage and cache, its last page. */
    fun remove(id: String) {
        close(id)
        states.remove(id)
        lastPages.edit().remove(id).apply()
        runtime.storageController.clearDataForSessionContext(contextId(id))
    }

    /** Logs the site out: its cookies, storage and cache only. */
    fun clearData(site: Site) {
        runtime.storageController.clearDataForSessionContext(contextId(site.id))
        sessions[site.id]?.apply { purgeHistory(); loadUri(site.url) }
    }

    fun onStop() {
        sessions.values.forEach { it.setActive(false) }
    }

    fun onStart() {
        shown?.let { sessions[it]?.setActive(true) }
    }

    fun closeAll() {
        sessions.keys.toList().forEach { close(it) }
    }

    private fun deactivate(session: GeckoSession) {
        session.setFocused(false)
        session.setActive(false)
        runtime.webExtensionController.setTabActive(session, false)
    }

    private fun close(id: String) {
        val session = sessions.remove(id) ?: return
        configs.remove(id)
        if (shown == id) shown = null
        extensions.detach(session)
        session.close()
    }

    private fun create(site: Site): GeckoSession {
        val settings = GeckoSessionSettings.Builder()
            .contextId(contextId(site.id))
            .useTrackingProtection(true)
            .userAgentMode(if (site.desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .viewportMode(if (site.desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
            .build()
        val session = GeckoSession(settings)
        delegates(session, site.id)
        session.open(runtime)
        extensions.attach(session)
        sessions[site.id] = session
        configs[site.id] = site
        session.loadUri(startUrl(site))
        return session
    }

    private fun apply(session: GeckoSession, site: Site) {
        val old = configs[site.id] ?: return
        configs[site.id] = site
        if (old.desktop != site.desktop) {
            session.settings.userAgentMode = if (site.desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
            session.settings.viewportMode = if (site.desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        }
        when {
            old.url != site.url -> { lastPages.edit().remove(site.id).apply(); session.loadUri(site.url) }
            old.desktop != site.desktop -> session.reload()
        }
    }

    /** The tab's last page when it is still on the site, else the site's address. */
    private fun startUrl(site: Site): String {
        val last = lastPages.getString(site.id, null) ?: return site.url
        val host = Uri.parse(last).host ?: return site.url
        return if (sameSite(host, site.host)) last else site.url
    }

    private fun delegates(session: GeckoSession, id: String) {
        val state = state(id)
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny> =
                GeckoResult.fromValue(
                    when (val d = Navigation.decide(request.uri)) {
                        Decision.Allow -> AllowOrDeny.ALLOW
                        is Decision.Redirect -> { session.loadUri(d.url); AllowOrDeny.DENY }
                        is Decision.Block -> { if (request.hasUserGesture) onNotice(d.reason); AllowOrDeny.DENY }
                    },
                )

            // Links to a new window (target=_blank, window.open) open in the same tab.
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                session.loadUri(uri)
                return null
            }

            override fun onLocationChange(session: GeckoSession, url: String?, perms: MutableList<ContentPermission>, hasUserGesture: Boolean) {
                url ?: return
                state.url = url
                val site = configs[id] ?: return
                val host = Uri.parse(url).host ?: return
                if (url.startsWith("http") && sameSite(host, site.host)) lastPages.edit().putString(id, url).apply()
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                state.canGoBack = canGoBack
            }
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                state.progress = progress
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                state.progress = 100
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                downloads.save(response)
            }

            override fun onCrash(session: GeckoSession) = restart(id)

            override fun onKill(session: GeckoSession) = restart(id)
        }
        session.promptDelegate = prompts
        // Location, camera, microphone, notifications…: a shop needs none of them.
        session.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onContentPermissionRequest(session: GeckoSession, perm: ContentPermission): GeckoResult<Int> =
                GeckoResult.fromValue(ContentPermission.VALUE_DENY)

            override fun onMediaPermissionRequest(
                session: GeckoSession, uri: String,
                video: Array<out GeckoSession.PermissionDelegate.MediaSource>?, audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                callback: GeckoSession.PermissionDelegate.MediaCallback,
            ) = callback.reject()
        }
    }

    /** The content process died: the tab is closed, and rebuilt on its last page when shown again. */
    private fun restart(id: String) {
        close(id)
        onNotice("La page a planté : elle est rechargée")
        restarted++
    }

    /** Bumped when a tab was closed after a crash, so the screen shows it again. */
    var restarted by mutableIntStateOf(0)
        private set

    private companion object {
        fun contextId(id: String) = "site_$id"

        fun sameSite(a: String, b: String) = registrable(a) == registrable(b)

        // "www.amazon.co.uk" → "amazon.co.uk": enough to tell a shop's pages from its bank's.
        fun registrable(host: String): String {
            val labels = host.lowercase().trimEnd('.').split('.')
            if (labels.size <= 2) return labels.joinToString(".")
            val keep = if (labels[labels.size - 2] in setOf("co", "com", "org", "net", "gouv", "ac") && labels.last().length == 2) 3 else 2
            return labels.takeLast(keep).joinToString(".")
        }
    }
}
