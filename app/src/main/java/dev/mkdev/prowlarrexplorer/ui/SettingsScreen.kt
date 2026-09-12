package dev.mkdev.prowlarrexplorer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.mkdev.prowlarrexplorer.BuildConfig
import dev.mkdev.prowlarrexplorer.data.short
import dev.mkdev.prowlarrexplorer.domain.AppSettings
import dev.mkdev.prowlarrexplorer.domain.ProwlarrConfig
import dev.mkdev.prowlarrexplorer.domain.QbitConfig
import dev.mkdev.prowlarrexplorer.domain.UrlParts
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    initial: AppSettings,
    onTestProwlarr: suspend (ProwlarrConfig) -> Result<String>,
    onTestQbit: suspend (QbitConfig) -> Result<String>,
    onCheckUpdate: suspend (githubToken: String) -> Result<String>,
    onSave: (AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    var prUrl by remember { mutableStateOf(UrlParts.parse(initial.prowlarr.url, 9696)) }
    var apiKey by remember { mutableStateOf(initial.prowlarr.apiKey) }
    var qbUrl by remember { mutableStateOf(UrlParts.parse(initial.qbit.url, 8080)) }
    var qbUser by remember { mutableStateOf(initial.qbit.username) }
    var qbPass by remember { mutableStateOf(initial.qbit.password) }
    var ghToken by remember { mutableStateOf(initial.githubToken) }

    fun prowlarr() = ProwlarrConfig(prUrl.toUrl(), apiKey)
    fun qbit() = QbitConfig(qbUrl.toUrl(), qbUser, qbPass)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réglages") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Prowlarr", style = MaterialTheme.typography.titleMedium)
            UrlFields(prUrl, onChange = { prUrl = it })
            OutlinedTextField(
                value = apiKey, onValueChange = { apiKey = it },
                label = { Text("Clé API") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Hint("Prowlarr → Settings → General → Security → API Key. Chiffrée dans le Keystore du téléphone.")
            ActionButton("Tester", enabled = prowlarr().configured, action = { onTestProwlarr(prowlarr()) })

            HorizontalDivider()

            Text("qBittorrent", style = MaterialTheme.typography.titleMedium)
            UrlFields(qbUrl, onChange = { qbUrl = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = qbUser, onValueChange = { qbUser = it },
                    label = { Text("Login") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = qbPass, onValueChange = { qbPass = it },
                    label = { Text("Mot de passe") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.weight(1f))
            }
            Hint(
                "qBittorrent n'a pas de clé API. Login/mot de passe vides = « Bypass authentication for clients in " +
                    "whitelisted IP subnets » activé dans qBittorrent (Options → WebUI) avec le sous-réseau Tailscale 100.64.0.0/10.",
            )
            ActionButton("Tester", enabled = qbit().configured, action = { onTestQbit(qbit()) })

            HorizontalDivider()

            Text("Mises à jour", style = MaterialTheme.typography.titleMedium)
            Text("Version installée : ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = ghToken, onValueChange = { ghToken = it },
                label = { Text("Token GitHub (dépôt privé)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Hint("Fine-grained token, dépôt Hitman47/ProwlarrExplorer, permission Contents : Read. Inutile si le dépôt est public.")
            ActionButton("Vérifier maintenant", enabled = true, action = { onCheckUpdate(ghToken) })

            Button(
                enabled = prowlarr().configured,
                onClick = { onSave(AppSettings(prowlarr(), qbit(), ghToken)); onBack() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Enregistrer") }
        }
    }
}

/** Schéma / hôte / port sur une ligne ; le port vide = port par défaut du schéma. */
@Composable
private fun UrlFields(parts: UrlParts, onChange: (UrlParts) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !parts.https, onClick = { onChange(parts.copy(https = false)) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("http") }
            SegmentedButton(
                selected = parts.https, onClick = { onChange(parts.copy(https = true)) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("https") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = parts.host, onValueChange = { onChange(parts.copy(host = it)) },
                label = { Text("IP ou hôte") },
                placeholder = { Text("100.x.y.z") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = parts.port, onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 5) onChange(parts.copy(port = v)) },
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(100.dp),
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ActionButton(label: String, enabled: Boolean, action: suspend () -> Result<String>) {
    var result by remember { mutableStateOf<String?>(null) }
    var ok by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    OutlinedButton(
        enabled = enabled && !running,
        onClick = {
            running = true
            scope.launch {
                action().onSuccess { result = it; ok = true }.onFailure { result = it.short(); ok = false }
                running = false
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (running) "$label…" else label) }

    result?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium,
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
    }
}
