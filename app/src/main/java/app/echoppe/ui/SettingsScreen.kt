package app.echoppe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import app.echoppe.web.Extensions
import app.echoppe.web.Tabs

@Composable
fun SettingsScreen(vm: EchoppeViewModel, tabs: Tabs, extensions: Extensions, snackbar: SnackbarHostState) {
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
                            SettingsHint(listOfNotNull(site.host, "version ordinateur".takeIf { site.desktop }).joinToString(" · "))
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

            ProtectionSettings(extensions) { title, url -> vm.extensionPage = title to url; vm.screen = Screen.Extension }
            SyncSettings(vm.syncFolder)
            UpdateSettings(vm.updates)
            AboutSettings(
                "Échoppe",
                "Tes sites d'achat dans une seule appli, sur le moteur de Firefox, sans pubs, traqueurs ni bandeaux. " +
                    "La liste des sites est synchronisée ; les connexions restent sur ce téléphone.",
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (adding) SiteDialog(null, onDismiss = { adding = false }, onSave = { if (vm.upsert(it)) adding = false })
    editing?.let { s ->
        SiteDialog(
            s,
            onDismiss = { editing = null },
            onSave = { if (vm.upsert(it)) editing = null },
            onRemove = { if (vm.remove(s)) { tabs.remove(s.id); editing = null } },
            onClear = { tabs.clearData(s); vm.show("Données de ${s.name} effacées"); editing = null },
        )
    }
}

/** The extensions that do the blocking, with their own settings pages, and Firefox's protections. */
@Composable
private fun ProtectionSettings(extensions: Extensions, open: (String, String) -> Unit) {
    SectionTitle("Protections")
    SettingsCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (spec in Extensions.SPECS) {
                val ext = extensions.installed[spec.id]
                Text(spec.title, fontWeight = FontWeight.SemiBold)
                SettingsHint(if (ext == null) "${spec.role} · pas encore installé" else "${spec.role} · version ${ext.metaData.version}")
            }
            extensions.error?.let { SettingsHint(it, MaterialTheme.colorScheme.error) }
            if (extensions.installing) LinearProgressIndicator(Modifier.fillMaxWidth())
            else OutlinedButton(onClick = { extensions.install(onlyMissing = false) }) { Text("Mettre à jour les extensions") }
        }
    }
    SettingsCard {
        Text("Réglages de uBlock Origin", fontWeight = FontWeight.SemiBold)
        SettingsHint(
            "Dans « Listes de filtres », coche la catégorie « Nuisances » (Annoyances) : bandeaux de cookies, widgets de chat, " +
                "bannières « ouvrir l'appli », pop-ups de newsletter. uBlock met ses listes à jour tout seul.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { extensions.optionsUrl(Extensions.UBLOCK, "dashboard.html#3p-filters.html")?.let { open("Listes de filtres", it) } },
                enabled = Extensions.UBLOCK.id in extensions.installed,
            ) { Text("Listes de filtres") }
            OutlinedButton(
                onClick = { extensions.optionsUrl(Extensions.UBLOCK)?.let { open("uBlock Origin", it) } },
                enabled = Extensions.UBLOCK.id in extensions.installed,
            ) { Text("Tout") }
        }
    }
    SettingsCard {
        Text("Réglages de Consent-O-Matic", fontWeight = FontWeight.SemiBold)
        SettingsHint("Toutes les catégories décochées = tout refuser, sauf les cookies nécessaires (connexion, panier).")
        OutlinedButton(
            onClick = { extensions.optionsUrl(Extensions.CONSENT)?.let { open("Consent-O-Matic", it) } },
            enabled = Extensions.CONSENT.id in extensions.installed,
        ) { Text("Ouvrir") }
    }
    SettingsCard {
        Text("Moteur Firefox", fontWeight = FontWeight.SemiBold)
        SettingsHint(
            "Chaque site a ses propres cookies, son stockage et son cache : un site ne peut pas reconnaître ce que tu fais sur un autre. " +
                "Protection renforcée contre le pistage en mode strict (traqueurs, empreinte numérique, cookies tiers isolés), " +
                "paramètres de pistage retirés des liens, signal « ne pas vendre mes données » (GPC) envoyé.",
        )
        SettingsHint("Les liens vers les applis et le Play Store sont bloqués : un site ne peut jamais ouvrir une autre appli.")
    }
}
