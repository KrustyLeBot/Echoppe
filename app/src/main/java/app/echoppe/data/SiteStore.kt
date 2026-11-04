package app.echoppe.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import java.io.File

/**
 * The site list, as `sites.json` in the public `Documents/Echoppe/` folder so that Synchrotron can
 * sync it with Proton Drive and it survives a reinstall. That folder needs the "all files access"
 * permission; until it is granted, and until the first sync after that, a private file is used
 * (see [moveToFolder]). Logins, cookies and history never go there: they stay in each site's
 * private WebView profile.
 *
 * An unreadable file raises an error and is never overwritten.
 */
class SiteStore(private val context: Context) {
    private val publicDir: File
        get() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Echoppe")

    private val privateFile: File
        get() = File(context.filesDir, SitesFile.NAME)

    /** Folder that Synchrotron syncs with Proton Drive (only meaningful with [hasStorageAccess]). */
    val syncPath: String get() = publicDir.path

    fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private val file: File
        get() = if (hasStorageAccess() && !privateFile.exists()) File(publicDir, SitesFile.NAME) else privateFile

    /**
     * Moves the private list to the public folder. Called once Proton Drive's copy has been pulled
     * (or there is none): moved before, it would be the newer file and replace the saved list.
     * An existing public file keeps its order and only gets the sites it lacks.
     * @return true when the list changed.
     */
    @Synchronized
    fun moveToFolder(): Boolean {
        if (!hasStorageAccess() || !privateFile.exists()) return false
        val target = File(publicDir, SitesFile.NAME)
        val mine = read(privateFile)
        val merged = if (target.exists()) SitesFile.merge(read(target), mine) else mine
        AtomicFiles.write(target, SitesFile.encode(merged))
        privateFile.delete()
        return true
    }

    @Synchronized
    fun load(): List<Site> = file.let { if (it.exists()) read(it) else emptyList() }

    @Synchronized
    fun save(sites: List<Site>) {
        val f = file
        // Rewriting an identical file would still wake Synchrotron's watcher for nothing.
        val current = if (f.exists()) SitesFile.toJson(read(f)).toString() else null
        if (current != SitesFile.toJson(sites).toString()) AtomicFiles.write(f, SitesFile.encode(sites))
    }

    /** A sync may be replacing the file: a parse failure is read again once before being reported. */
    private fun read(f: File): List<Site> = try {
        AtomicFiles.read(f) { SitesFile.decode(it) } ?: emptyList()
    } catch (e: UnreadableFileException) {
        throw IllegalStateException("${f.path} est illisible. Il ne sera pas modifié.", e)
    }
}
