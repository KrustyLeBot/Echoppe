package app.echoppe

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.echoppe.data.Site
import app.echoppe.suite.SuiteUpdates
import app.echoppe.sync.SkipReason
import app.echoppe.sync.SyncState
import app.echoppe.sync.SynchrotronClient
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.File

enum class Screen { Main, Settings }

class EchoppeViewModel(app: Application) : AndroidViewModel(app) {
    private val echoppe = app as EchoppeApp
    private val prefs = app.getSharedPreferences("ui", Context.MODE_PRIVATE)

    var screen by mutableStateOf(Screen.Main)

    var storeError by mutableStateOf<String?>(null)
        private set
    var sites by mutableStateOf(readSites())
        private set
    /** Id of the shown tab; device-only, never synced. */
    var selectedId by mutableStateOf(prefs.getString(KEY_SELECTED, null))
        private set

    val selected: Site? get() = sites.firstOrNull { it.id == selectedId } ?: sites.firstOrNull()

    val updates = SuiteUpdates(app, echoppe.updater, viewModelScope)

    private val noticeChannel = Channel<String>(Channel.CONFLATED)
    /** Short messages, shown as snackbars. */
    val notices = noticeChannel.receiveAsFlow()

    // --- Proton Drive sync through Synchrotron ---

    /** A sync is running: the site list is read-only until it ends (browsing goes on). */
    var syncing by mutableStateOf(false)
        private set
    /** Synchrotron is installed but the app may not write `Documents/Echoppe` yet: ask for it. */
    var needsStorageAccess by mutableStateOf(false)
        private set
    private var stopSync: (() -> Unit)? = null

    val syncFolder = "Documents/Echoppe"

    fun show(text: String) {
        noticeChannel.trySend(text)
    }

    private fun readSites(): List<Site> = try {
        echoppe.siteStore.load().also { storeError = null }
    } catch (e: Exception) {
        storeError = e.message
        emptyList()
    }

    /** Re-reads the list, e.g. after a sync brought another device's changes. */
    private fun reloadSites() {
        sites = readSites()
    }

    /** Asks Synchrotron for a sync so the site list is up to date when the app opens. */
    fun syncOnOpen() {
        if (syncing) return
        needsStorageAccess = !echoppe.siteStore.hasStorageAccess() && SynchrotronClient.isInstalled(echoppe)
        if (!echoppe.siteStore.hasStorageAccess()) return
        // So the folder can be picked in Synchrotron before the first site is saved there.
        File(echoppe.siteStore.syncPath).mkdirs()
        stopSync?.invoke()
        stopSync = SynchrotronClient.syncOnOpen(echoppe, echoppe.siteStore.syncPath) { state ->
            when (state) {
                SyncState.Syncing -> syncing = true
                is SyncState.Done -> {
                    syncing = false
                    if (moveToFolder() || state.changes > 0) reloadSites()
                    if (state.changes > 0) show("Sites synchronisés")
                }
                is SyncState.Failed -> { syncing = false; show("Échec de la synchro") }
                SyncState.TimedOut -> { syncing = false; show("Synchro longue : elle continue en arrière-plan") }
                is SyncState.Skipped -> {
                    syncing = false
                    if (state.reason in SKIPS_WITHOUT_REMOTE_DATA && moveToFolder()) reloadSites()
                    if (state.reason == SkipReason.NEEDS_SETUP) show("Termine la configuration dans Synchrotron")
                }
            }
        }
    }

    private fun moveToFolder(): Boolean = try {
        echoppe.siteStore.moveToFolder()
    } catch (e: Exception) {
        storeError = e.message
        false
    }

    /** True when the list cannot be changed now (sync running or unreadable file), with the reason shown. */
    fun readOnly(): Boolean {
        when {
            syncing -> show("Synchronisation en cours…")
            storeError != null -> show("sites.json est illisible : corrige-le ou supprime-le")
            else -> return false
        }
        return true
    }

    override fun onCleared() {
        stopSync?.invoke()
    }

    fun select(id: String) {
        selectedId = id
        prefs.edit().putString(KEY_SELECTED, id).apply()
    }

    /** Adds [site], or replaces the site with the same id. */
    fun upsert(site: Site): Boolean {
        if (readOnly()) return false
        val index = sites.indexOfFirst { it.id == site.id }
        val list = if (index >= 0) sites.toMutableList().also { it[index] = site } else sites + site
        if (!save(list)) return false
        if (index < 0) select(site.id)
        return true
    }

    fun remove(site: Site): Boolean {
        if (readOnly()) return false
        if (!save(sites.filter { it.id != site.id })) return false
        show("${site.name} supprimé")
        return true
    }

    /** Live move while a tab is dragged; saved once by [orderChanged]. */
    fun move(from: Int, to: Int) {
        if (syncing || storeError != null) return
        if (from !in sites.indices || to !in sites.indices) return
        sites = sites.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun orderChanged() {
        if (readOnly()) {
            reloadSites()
            return
        }
        save(sites)
    }

    private fun save(list: List<Site>): Boolean = try {
        echoppe.siteStore.save(list)
        sites = list
        true
    } catch (e: Exception) {
        show("Enregistrement impossible : ${e.message}")
        reloadSites()
        false
    }

    private companion object {
        const val KEY_SELECTED = "selected"

        /**
         * Proton Drive's copy is already here (or there is no Synchrotron): the private list can move to
         * the synced folder. Not while the folder is unregistered: the first sync after registering it
         * would then push that list over the saved one.
         */
        val SKIPS_WITHOUT_REMOTE_DATA = setOf(SkipReason.NOT_INSTALLED, SkipReason.FRESH, SkipReason.COOLDOWN)
    }
}
