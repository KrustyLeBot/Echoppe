package app.echoppe

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import app.echoppe.ui.EchoppeRoot
import app.echoppe.ui.EchoppeTheme
import app.echoppe.web.Downloads
import app.echoppe.web.Prompts
import app.echoppe.web.Tabs

class MainActivity : ComponentActivity() {
    private val vm: EchoppeViewModel by viewModels()
    private lateinit var tabs: Tabs
    private var onFiles: ((List<Uri>) -> Unit)? = null

    private val storagePermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.syncOnOpen() }

    private val filePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            val uris = when {
                result.resultCode != RESULT_OK || data == null -> emptyList()
                data.clipData != null -> List(data.clipData!!.itemCount) { data.clipData!!.getItemAt(it).uri }
                else -> listOfNotNull(data.data)
            }
            onFiles?.invoke(uris)
            onFiles = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as EchoppeApp
        val prompts = Prompts(this) { intent, done ->
            onFiles?.invoke(emptyList())
            onFiles = done
            try {
                filePicker.launch(intent)
            } catch (e: Exception) {
                onFiles = null
                done(emptyList())
            }
        }
        tabs = Tabs(this, app.runtime, app.extensions, prompts, Downloads(this, lifecycleScope, vm::show), vm::show)
        setContent {
            EchoppeTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    EchoppeRoot(vm, tabs, app.extensions, app.runtime, onAllowStorage = ::askStorageAccess)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        tabs.onStart()
        vm.syncOnOpen()
        vm.updates.checkOnOpen()
        (application as EchoppeApp).extensions.updateIfStale()
    }

    override fun onStop() {
        tabs.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        tabs.closeAll()
        super.onDestroy()
    }

    /** `Documents/Echoppe` is shared with Synchrotron, hence "all files access" (a plain permission before Android 11). */
    private fun askStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
            runCatching { startActivity(intent) }.onFailure {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            storagePermission.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }
}
