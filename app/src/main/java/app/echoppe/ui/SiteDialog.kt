package app.echoppe.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.echoppe.data.Site
import app.echoppe.data.SiteUrl
import app.echoppe.suite.ConfirmDialog
import app.echoppe.suite.SettingsHint
import app.echoppe.web.WebPool

/** Adds a site ([site] null) or edits one, with its data and removal actions. */
@Composable
fun SiteDialog(
    site: Site?,
    pool: WebPool,
    onDismiss: () -> Unit,
    onSave: (Site) -> Unit,
    onRemove: () -> Unit = {},
    onClear: () -> Unit = {},
) {
    var address by remember { mutableStateOf(site?.url.orEmpty()) }
    var name by remember { mutableStateOf(site?.name.orEmpty()) }
    var desktop by remember { mutableStateOf(site?.desktop ?: false) }
    var blockTrackers by remember { mutableStateOf(site?.blockTrackers ?: true) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }

    fun save() {
        val url = SiteUrl.normalize(address)
        if (url == null) {
            error = "Adresse invalide (exemple : amazon.fr)"
            return
        }
        val base = site ?: Site(Site.newId(), "", url)
        onSave(base.copy(name = name.trim().ifEmpty { SiteUrl.defaultName(url) }, url = url, desktop = desktop, blockTrackers = blockTrackers))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (site == null) "Ajouter un site" else "Modifier ${site.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    address, { address = it; error = null },
                    label = { Text("Adresse") },
                    placeholder = { Text("amazon.fr") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    name, { name = it },
                    label = { Text("Nom de l'onglet") },
                    placeholder = { Text(SiteUrl.normalize(address)?.let { SiteUrl.defaultName(it) } ?: "Amazon") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Toggle("Version ordinateur", "Pour un site dont la version mobile est moins pratique.", desktop) { desktop = it }
                Toggle("Bloquer les traqueurs", "À couper seulement si une page du site ne marche pas.", blockTrackers) { blockTrackers = it }
                if (site != null) {
                    HorizontalDivider()
                    TextButton(onClick = { confirm = "clear" }) { Text("Effacer cookies et données") }
                    TextButton(
                        onClick = { confirm = "remove" },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Supprimer le site") }
                }
            }
        },
        confirmButton = { TextButton(onClick = ::save) { Text(if (site == null) "Ajouter" else "Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )

    if (site != null) when (confirm) {
        "clear" -> ConfirmDialog(
            "Effacer les données de ${site.name} ?",
            if (pool.isolated) "Tu seras déconnecté de ce site et son panier peut être perdu. Les autres sites ne sont pas touchés."
            else "Sans isolation, les sites partagent leurs cookies : tu seras déconnecté de TOUS les sites.",
            "Effacer", onConfirm = onClear, onDismiss = { confirm = null }, destructive = true,
        )
        "remove" -> ConfirmDialog(
            "Supprimer ${site.name} ?",
            "L'onglet disparaît de tous tes appareils synchronisés. Les cookies et la connexion à ce site sont effacés de ce téléphone.",
            "Supprimer", onConfirm = onRemove, onDismiss = { confirm = null }, destructive = true,
        )
    }
}

@Composable
private fun Toggle(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            SettingsHint(hint)
        }
        Switch(checked, onChange)
    }
}
