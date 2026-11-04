package app.echoppe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echoppe.EchoppeViewModel
import app.echoppe.Screen
import app.echoppe.data.Site
import app.echoppe.suite.AboutSettings
import app.echoppe.suite.SectionTitle
import app.echoppe.suite.SettingsCard
import app.echoppe.suite.SettingsHint
import app.echoppe.suite.SuiteTopBar
import app.echoppe.suite.SyncSettings
import app.echoppe.suite.UpdateSettings
import app.echoppe.web.WebPool

@Composable
fun SettingsScreen(vm: EchoppeViewModel, pool: WebPool, snackbar: SnackbarHostState) {
    var editing by remember { mutableStateOf<Site?>(null) }
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { SuiteTopBar("Réglages", onBack = { vm.screen = Screen.Main }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionTitle("Sites")
            SettingsCard {
                if (vm.sites.isEmpty()) SettingsHint("Aucun site. Ajoute-les un par un : rien n'est prérempli.")
                vm.sites.forEachIndexed { i, site ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(site.name, fontWeight = FontWeight.SemiBold)
                            SettingsHint(listOfNotNull(site.host, "ordinateur".takeIf { site.desktop }, "traqueurs autorisés".takeIf { !site.blockTrackers }).joinToString(" · "))
                        }
                        IconButton(onClick = { vm.move(i, i - 1); vm.orderChanged() }, enabled = i > 0) {
                            Icon(Icons.Default.ArrowUpward, "Monter")
                        }
                        IconButton(onClick = { vm.move(i, i + 1); vm.orderChanged() }, enabled = i < vm.sites.lastIndex) {
                            Icon(Icons.Default.ArrowDownward, "Descendre")
                        }
                        IconButton(onClick = { editing = site }) { Icon(Icons.Default.Edit, "Modifier") }
                    }
                }
                FilledTonalButton(onClick = { if (!vm.readOnly()) adding = true }) {
                    Icon(Icons.Default.Add, null)
                    Text("Ajouter un site", Modifier.padding(start = 8.dp))
                }
            }

            SectionTitle("Vie privée")
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (pool.isolated) {
                        Text("Chaque site est isolé", fontWeight = FontWeight.SemiBold)
                        SettingsHint("Cookies, stockage et cache séparés par site : un site ne peut pas reconnaître ce que tu fais sur un autre.")
                    } else {
                        Text("Isolation des sites indisponible", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                        SettingsHint(
                            "La version d'« Android System WebView » de ce téléphone ne sait pas séparer les sites : ils partagent les mêmes cookies. " +
                                "Mets-la à jour depuis le Play Store, puis relance Échoppe.",
                            MaterialTheme.colorScheme.error,
                        )
                    }
                    SettingsHint("Cookies tiers refusés partout.")
                    SettingsHint("${pool.trackerCount} domaines de traqueurs bloqués (pub, statistiques, pixels des réseaux sociaux, enregistrement de session). Désactivable site par site s'il casse une page.")
                    if (pool.sendsGpc) SettingsHint("Le signal « ne pas vendre ni partager mes données » (GPC) est envoyé à chaque site.")
                    SettingsHint("Les liens vers les applis et le Play Store sont bloqués : un site ne peut jamais ouvrir une autre appli.")
                    SettingsHint("Aucune statistique d'usage envoyée par l'appli ni par la WebView.")
                }
            }

            SyncSettings(vm.syncFolder)
            UpdateSettings(vm.updates)
            AboutSettings(
                "Échoppe",
                "Tes sites d'achat dans une seule appli, sans traqueurs ni passerelle vers leurs applis. " +
                    "La liste des sites est synchronisée ; les connexions restent sur ce téléphone.",
            )
            Spacer(Modifier.height(32.dp))
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
