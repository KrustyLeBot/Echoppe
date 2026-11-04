package app.echoppe

import android.app.Application
import app.echoppe.data.SiteStore
import app.echoppe.suite.SuiteUpdater
import app.echoppe.web.Browser
import app.echoppe.web.Extensions

class EchoppeApp : Application() {
    val siteStore by lazy { SiteStore(this) }
    val updater by lazy { SuiteUpdater(this, "KrustyLeBot/echoppe", "$packageName.fileprovider") }

    // Lazy: Gecko's content processes run this class too, and must not start a second engine.
    val startup by lazy { Startup(this) }
    val runtime by lazy { Browser.create(this).also { startup.mark(Startup.ENGINE) } }
    val extensions by lazy { Extensions(this, runtime, onReady = { startup.mark(Startup.EXTENSIONS) }).also { it.start() } }
}
