package app.echoppe.web

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.ProfileStore
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.echoppe.data.Site
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap

/** What the UI shows about one tab. */
class TabState {
    var progress by mutableIntStateOf(100)
    var blocked by mutableIntStateOf(0)
    var url by mutableStateOf("")
}

/**
 * The live WebViews, one per site, created the first time a tab is shown and kept so that switching
 * tabs never reloads a page. Each site gets its own WebView profile when the installed WebView
 * supports it ([isolated]): its own cookies, storage, cache and service workers, so no site can see
 * another's identifiers. On top of that, in every tab: third-party cookies are refused, the tracker
 * list is blocked, the "do not sell or share" signal (GPC) is sent, the `X-Requested-With` header
 * and ad attribution are off, and nothing a page does can open another app.
 */
class WebPool(
    private val activity: Activity,
    private val trackers: Trackers,
    private val onNotice: (String) -> Unit,
    private val onFileChooser: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Boolean,
) {
    val isolated: Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
    val sendsGpc: Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    val trackerCount: Int get() = trackers.size

    private val views = HashMap<String, WebView>()
    // Read from WebView's network threads (shouldInterceptRequest).
    private val configs = ConcurrentHashMap<String, Site>()
    private val states = HashMap<String, TabState>()
    private val main = Handler(Looper.getMainLooper())
    private val prefs = activity.getSharedPreferences("profiles", Context.MODE_PRIVATE)
    private var shown: String? = null
    private var timersPaused = false

    /** Bumped when WebViews are dropped, so the screen asks for the shown one again. */
    var generation by mutableIntStateOf(0)
        private set

    init {
        deletePendingProfiles()
    }

    fun state(id: String): TabState = states.getOrPut(id) { TabState() }

    /** The site's WebView, created on first use; a changed address or mode is applied to it. */
    fun view(site: Site): WebView {
        views[site.id]?.let { wv ->
            val old = configs[site.id]
            if (old != site) apply(wv, site, old)
            return wv
        }
        val wv = WebView(activity)
        // Must come before anything else touches the WebView.
        if (isolated) WebViewCompat.setProfile(wv, profileName(site.id))
        setup(wv, site.id)
        apply(wv, site, null)
        views[site.id] = wv
        // pauseTimers() outlives the WebViews it was called on: a view built later would start frozen.
        if (timersPaused) { wv.resumeTimers(); timersPaused = false }
        wv.loadUrl(site.url)
        return wv
    }

    /** Only the shown tab runs; the others are paused (timers, animations) but keep their page. */
    fun show(id: String) {
        if (shown == id) return
        shown = id
        views.forEach { (key, wv) -> if (key == id) wv.onResume() else wv.onPause() }
    }

    fun canGoBack(id: String) = views[id]?.canGoBack() == true

    fun goBack(id: String) = views[id]?.goBack()

    fun reload(id: String) = views[id]?.reload()

    fun home(site: Site) = views[site.id]?.loadUrl(site.url)

    /** Drops the WebViews of sites no longer in the list (e.g. removed on another phone). */
    fun retain(ids: Set<String>) {
        (views.keys - ids).forEach { destroy(it) }
    }

    /** Forgets the site entirely: its WebView, and with [isolated] its cookies, storage and cache. */
    fun remove(id: String) {
        destroy(id)
        states.remove(id)
        if (!isolated) return
        prefs.edit().putStringSet(KEY_PENDING, pending() + profileName(id)).apply()
        // The profile can only go once no WebView uses it; the destroy above is processed first.
        main.post { deletePendingProfiles() }
    }

    /**
     * Logs the site out: cookies, local storage and cache. Without [isolated] profiles, every site
     * shares one store, so every site is cleared.
     */
    fun clearData(site: Site) {
        if (isolated) {
            val profile = views[site.id]?.let { WebViewCompat.getProfile(it) }
                ?: ProfileStore.getInstance().getOrCreateProfile(profileName(site.id))
            profile.cookieManager.removeAllCookies(null)
            profile.cookieManager.flush()
            profile.webStorage.deleteAllData()
            views[site.id]?.apply { clearCache(true); clearHistory(); loadUrl(site.url) }
        } else {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            views.forEach { (id, wv) -> wv.clearCache(true); wv.clearHistory(); configs[id]?.let { wv.loadUrl(it.url) } }
        }
    }

    /** Writes the session cookies to disk, so logins survive the app being killed. */
    fun flush() {
        if (isolated) views.values.forEach { WebViewCompat.getProfile(it).cookieManager.flush() }
        else CookieManager.getInstance().flush()
    }

    fun onStart() {
        views.values.firstOrNull()?.let { it.resumeTimers(); timersPaused = false }
        shown?.let { views[it]?.onResume() }
    }

    fun onStop() {
        flush()
        views.values.forEach { it.onPause() }
        // Global to every WebView of the app: no page runs JavaScript while the app is in the background.
        views.values.firstOrNull()?.let { it.pauseTimers(); timersPaused = true }
    }

    fun destroyAll() {
        views.keys.toList().forEach { destroy(it) }
    }

    private fun destroy(id: String) {
        val wv = views.remove(id) ?: return
        configs.remove(id)
        if (shown == id) shown = null
        (wv.parent as? ViewGroup)?.removeView(wv)
        wv.stopLoading()
        wv.destroy()
        generation++
    }

    private fun pending(): Set<String> = prefs.getStringSet(KEY_PENDING, emptySet()).orEmpty().toSet()

    private fun deletePendingProfiles() {
        if (!isolated) return
        val left = pending().filterNot { name ->
            try {
                ProfileStore.getInstance().deleteProfile(name)
                true
            } catch (e: IllegalStateException) {
                false // Loaded in this process: deleted at the next start, before any WebView exists.
            }
        }.toSet()
        prefs.edit().putStringSet(KEY_PENDING, left).apply()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setup(wv: WebView, id: String) {
        val s = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.setGeolocationEnabled(false)
        s.mediaPlaybackRequiresUserGesture = true
        // window.open and target=_blank stay in the tab: no pop-up windows.
        s.setSupportMultipleWindows(false)
        s.javaScriptCanOpenWindowsAutomatically = false
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            // Otherwise every request may carry this app's package name.
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(s, emptySet())
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ATTRIBUTION_REGISTRATION_BEHAVIOR)) {
            WebSettingsCompat.setAttributionRegistrationBehavior(s, WebSettingsCompat.ATTRIBUTION_BEHAVIOR_DISABLED)
        }
        cookies(wv).apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, false)
        }
        if (sendsGpc) WebViewCompat.addDocumentStartJavaScript(wv, PRIVACY_SCRIPT, setOf("*"))

        val state = state(id)
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                when (val d = Navigation.decide(request.url.toString())) {
                    Decision.Allow -> false
                    is Decision.Redirect -> { view.loadUrl(d.url); true }
                    is Decision.Block -> { if (request.hasGesture() || request.isForMainFrame) onNotice(d.reason); true }
                }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (request.isForMainFrame) return null
                val site = configs[id] ?: return null
                if (!site.blockTrackers) return null
                val host = request.url.host ?: return null
                trackers.match(host, site.host) ?: return null
                main.post { state.blocked++ }
                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                state.url = url
            }

            override fun onPageFinished(view: WebView, url: String) {
                state.url = url
                cookies(view).flush()
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                // The renderer is shared: every tab lost its page. They are rebuilt when shown again.
                views.keys.toList().forEach { destroy(it) }
                onNotice("La page a planté : elle est rechargée")
                return true
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                state.progress = newProgress
            }

            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean =
                onFileChooser(callback, params)

            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                callback.invoke(origin, false, false)
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                request.deny()
            }
        }
        wv.setDownloadListener { url, userAgent, disposition, mime, _ ->
            download(wv, url, userAgent, disposition, mime)
        }
    }

    private fun apply(wv: WebView, site: Site, old: Site?) {
        configs[site.id] = site
        val s = wv.settings
        val default = WebSettingsDefaults.userAgent(activity)
        s.userAgentString = if (site.desktop) UserAgents.desktop(default) else UserAgents.mobile(default)
        s.useWideViewPort = site.desktop
        s.loadWithOverviewMode = site.desktop
        when {
            old == null -> Unit
            old.url != site.url -> wv.loadUrl(site.url)
            old.desktop != site.desktop || old.blockTrackers != site.blockTrackers -> wv.reload()
        }
    }

    private fun download(wv: WebView, url: String, userAgent: String, disposition: String?, mime: String?) {
        if (!url.startsWith("http")) {
            onNotice("Ce fichier ne peut pas être téléchargé")
            return
        }
        val name = URLUtil.guessFileName(url, disposition, mime)
        val request = DownloadManager.Request(Uri.parse(url))
            .setMimeType(mime)
            .addRequestHeader("User-Agent", userAgent)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        // The site's own cookies only, so an invoice behind a login downloads.
        cookies(wv).getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
        try {
            (activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            onNotice("Téléchargement de $name")
        } catch (e: Exception) {
            onNotice("Téléchargement impossible : ${e.message}")
        }
    }

    private fun cookies(wv: WebView): CookieManager =
        if (isolated) WebViewCompat.getProfile(wv).cookieManager else CookieManager.getInstance()

    private companion object {
        const val KEY_PENDING = "to_delete"

        fun profileName(id: String) = "site_$id"

        /**
         * Sends Global Privacy Control (the legal "do not sell or share my data" signal) and hides
         * which apps are installed, so a site cannot tell the phone has its app.
         */
        const val PRIVACY_SCRIPT = """
            try { Object.defineProperty(Navigator.prototype, 'globalPrivacyControl', { get: () => true, configurable: true }); } catch (e) {}
            try { if (Navigator.prototype.getInstalledRelatedApps) Navigator.prototype.getInstalledRelatedApps = () => Promise.resolve([]); } catch (e) {}
        """
    }
}

/** The WebView's own user agent, read once. */
private object WebSettingsDefaults {
    private var cached: String? = null

    fun userAgent(context: Context): String =
        cached ?: android.webkit.WebSettings.getDefaultUserAgent(context).also { cached = it }
}
