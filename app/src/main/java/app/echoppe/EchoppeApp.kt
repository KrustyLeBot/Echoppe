package app.echoppe

import android.app.Application
import app.echoppe.data.SiteStore
import app.echoppe.suite.SuiteUpdater
import app.echoppe.web.Trackers

class EchoppeApp : Application() {
    val siteStore by lazy { SiteStore(this) }
    val trackers by lazy { Trackers.parse(assets.open("trackers.txt").bufferedReader().use { it.readText() }) }
    val updater by lazy { SuiteUpdater(this, "KrustyLeBot/echoppe", "$packageName.fileprovider") }
}
