package app.echoppe

import android.app.Application
import app.echoppe.data.SiteStore
import app.echoppe.suite.SuiteUpdater
import app.echoppe.web.FilterLists
import kotlinx.coroutines.MainScope

class EchoppeApp : Application() {
    val siteStore by lazy { SiteStore(this) }
    val filters by lazy { FilterLists(this, MainScope()) }

    override fun onCreate() {
        super.onCreate()
        filters.start()
    }
    val updater by lazy { SuiteUpdater(this, "KrustyLeBot/echoppe", "$packageName.fileprovider") }
}
