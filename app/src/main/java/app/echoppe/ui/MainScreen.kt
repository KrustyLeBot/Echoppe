package app.echoppe.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import app.echoppe.EchoppeViewModel
import app.echoppe.R
import app.echoppe.Screen
import app.echoppe.data.Site
import app.echoppe.suite.SuiteBanner
import app.echoppe.suite.Tone
import app.echoppe.suite.UpdateBanner
import app.echoppe.web.WebPool

@Composable
fun EchoppeRoot(vm: EchoppeViewModel, pool: WebPool, onAllowStorage: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.notices.collect { snackbar.showSnackbar(it) } }
    // Sites removed elsewhere (another phone, through the sync) lose their live WebView too.
    LaunchedEffect(vm.sites) { pool.retain(vm.sites.map { it.id }.toSet()) }
    LaunchedEffect(pool.filters.version) { pool.filtersChanged() }
    BackHandler(enabled = vm.screen != Screen.Main) { vm.screen = Screen.Main }
    when (vm.screen) {
        Screen.Main -> MainScreen(vm, pool, snackbar, onAllowStorage)
        Screen.Settings -> SettingsScreen(vm, pool, snackbar)
    }
}

@Composable
private fun MainScreen(vm: EchoppeViewModel, pool: WebPool, snackbar: SnackbarHostState, onAllowStorage: () -> Unit) {
    val site = vm.selected
    var editing by remember { mutableStateOf<Site?>(null) }
    var adding by remember { mutableStateOf(false) }

    val activity = LocalContext.current as Activity
    // Back walks the site's history; past its first page the app goes to the background, pages kept.
    BackHandler(enabled = site != null) {
        if (site != null && pool.canGoBack(site.id)) pool.goBack(site.id) else activity.moveTaskToBack(true)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.statusBarsPadding().fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (vm.sites.isEmpty()) {
                        Text(
                            "Échoppe", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f).padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
                        )
                    } else {
                        SiteTabs(vm, Modifier.weight(1f))
                    }
                    SiteMenu(vm, pool, site, onAdd = { adding = true }, onEdit = { editing = it })
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize().imePadding()) {
            vm.storeError?.let { SuiteBanner(it, tone = Tone.Error, title = "Liste des sites illisible", icon = Icons.Default.ErrorOutline) }
            if (vm.syncing) SuiteBanner("Liste des sites en lecture seule le temps de la synchro.", title = "Synchronisation", icon = Icons.Default.Sync, progress = -1f)
            if (vm.needsStorageAccess) {
                SuiteBanner("Ta liste de sites est enregistrée dans ${vm.syncFolder}, synchronisé par Synchrotron : autorise l'accès aux fichiers.", title = "Accès aux fichiers", icon = Icons.Default.Folder) {
                    TextButton(onClick = onAllowStorage) { Text("Autoriser") }
                }
            }
            UpdateBanner(vm.updates)
            if (site == null) {
                EmptyState(onAdd = { if (!vm.readOnly()) adding = true })
            } else {
                val state = pool.state(site.id)
                if (state.progress < 100) LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                if (pool.filters.ready) {
                    WebHost(pool, site, Modifier.weight(1f).fillMaxWidth())
                } else {
                    // No page loads before the lists are ready, so none ever runs unfiltered.
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
        }
    }

    if (adding) SiteDialog(null, pool, onDismiss = { adding = false }, onSave = { if (vm.upsert(it)) adding = false })
    editing?.let { s ->
        SiteDialog(
            s, pool,
            onDismiss = { editing = null },
            onSave = { if (vm.upsert(it)) editing = null },
            onRemove = { if (vm.remove(s)) { pool.remove(s.id); editing = null } },
            onClear = { pool.clearData(s); vm.show("Données de ${s.name} effacées"); editing = null },
        )
    }
}

/** The site's WebView, moved into this screen; switching tabs swaps the view without reloading. */
@Composable
private fun WebHost(pool: WebPool, site: Site, modifier: Modifier) {
    // A new host when the pool dropped its WebViews (renderer crash), so the rebuilt one is attached.
    key(pool.generation) { AndroidView(
        factory = { FrameLayout(it) },
        modifier = modifier,
        update = { host ->
            val wv = pool.view(site)
            if (wv.parent !== host) {
                (wv.parent as? ViewGroup)?.removeView(wv)
                host.removeAllViews()
                host.addView(wv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            pool.show(site.id)
        },
        onRelease = { it.removeAllViews() },
    ) }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(160.dp).clip(CircleShape)) {
            Image(painterResource(R.mipmap.ic_launcher_background), null, Modifier.fillMaxSize())
            Image(painterResource(R.mipmap.ic_launcher_foreground), null, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(24.dp))
        Text("Aucun site pour l'instant", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Ajoute tes sites d'achat un par un. Chacun a son propre onglet, ses propres cookies, et ne voit rien des autres.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAdd) { Text("Ajouter un site") }
    }
}

@Composable
private fun SiteMenu(vm: EchoppeViewModel, pool: WebPool, site: Site?, onAdd: () -> Unit, onEdit: (Site) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, "Menu") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (site != null) {
                val state = pool.state(site.id)
                DropdownMenuItem(text = { Text("Recharger") }, leadingIcon = { Icon(Icons.Default.Refresh, null) },
                    onClick = { open = false; pool.reload(site.id) })
                DropdownMenuItem(text = { Text("Accueil du site") }, leadingIcon = { Icon(Icons.Default.Home, null) },
                    onClick = { open = false; pool.home(site) })
                DropdownMenuItem(text = { Text("Copier l'adresse") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                    onClick = {
                        open = false
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(site.name, state.url.ifEmpty { site.url }))
                    })
                DropdownMenuItem(text = { Text("Modifier ${site.name}") }, leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = { open = false; onEdit(site) })
                DropdownMenuItem(
                    text = {
                        Text(
                            if (!site.blockAds && !site.blockTrackers) "Blocage désactivé sur ce site"
                            else "${state.blocked} requête${if (state.blocked > 1) "s" else ""} bloquée${if (state.blocked > 1) "s" else ""}",
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Shield, null) },
                    enabled = false,
                    onClick = {},
                )
                HorizontalDivider()
            }
            DropdownMenuItem(text = { Text("Ajouter un site") }, leadingIcon = { Icon(Icons.Default.Add, null) },
                onClick = { open = false; if (!vm.readOnly()) onAdd() })
            DropdownMenuItem(text = { Text("Réglages") }, leadingIcon = { Icon(Icons.Default.Settings, null) },
                onClick = { open = false; vm.screen = Screen.Settings })
        }
    }
}

/** One tab per site; long-press a tab and drag it to reorder. */
@Composable
private fun SiteTabs(vm: EchoppeViewModel, modifier: Modifier) {
    val haptic = LocalHapticFeedback.current
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val widths = remember { mutableStateMapOf<String, Int>() }
    val selectedId = vm.selected?.id

    Row(modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
        vm.sites.forEach { site ->
            key(site.id) {
                val dragging = draggingId == site.id
                val selected = site.id == selectedId
                Box(
                    Modifier
                        .onSizeChanged { widths[site.id] = it.width }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationX = if (dragging) dragOffset else 0f
                            scaleX = if (dragging) 1.08f else 1f
                            scaleY = if (dragging) 1.08f else 1f
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (dragging) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { vm.select(site.id) }
                        .pointerInput(site.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingId = site.id
                                    dragOffset = 0f
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.x
                                    val i = vm.sites.indexOfFirst { it.id == site.id }
                                    val next = vm.sites.getOrNull(i + 1)
                                    val prev = vm.sites.getOrNull(i - 1)
                                    val nextWidth = next?.let { widths[it.id] } ?: 0
                                    val prevWidth = prev?.let { widths[it.id] } ?: 0
                                    // Swapping moves the tab in the row, so the finger offset shrinks by that width.
                                    if (next != null && dragOffset > nextWidth / 2f) {
                                        vm.move(i, i + 1)
                                        dragOffset -= nextWidth
                                    } else if (prev != null && dragOffset < -prevWidth / 2f) {
                                        vm.move(i, i - 1)
                                        dragOffset += prevWidth
                                    }
                                },
                                onDragEnd = { draggingId = null; dragOffset = 0f; vm.orderChanged() },
                                onDragCancel = { draggingId = null; dragOffset = 0f; vm.orderChanged() },
                            )
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            site.name,
                            maxLines = 1,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier.width(24.dp).height(3.dp)
                                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
    }
}
