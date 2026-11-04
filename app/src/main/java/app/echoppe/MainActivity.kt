package app.echoppe

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.echoppe.ui.EchoppeRoot
import app.echoppe.ui.EchoppeTheme
import app.echoppe.web.WebPool

class MainActivity : ComponentActivity() {
    private val vm: EchoppeViewModel by viewModels()
    private lateinit var pool: WebPool
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val storagePermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.syncOnOpen() }

    private val fileChooser =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
            fileCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pool = WebPool(this, (application as EchoppeApp).filters, vm::show) { callback, params ->
            fileCallback?.onReceiveValue(null)
            fileCallback = callback
            try {
                fileChooser.launch(params.createIntent())
                true
            } catch (e: Exception) {
                fileCallback = null
                false
            }
        }
        setContent {
            EchoppeTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    EchoppeRoot(vm, pool, onAllowStorage = ::askStorageAccess)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        pool.onStart()
        vm.syncOnOpen()
        vm.updates.checkOnOpen()
    }

    override fun onStop() {
        pool.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        pool.destroyAll()
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
