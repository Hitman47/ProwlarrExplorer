package dev.mkdev.prowlarrexplorer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.mkdev.prowlarrexplorer.data.short
import dev.mkdev.prowlarrexplorer.domain.AppSettings
import dev.mkdev.prowlarrexplorer.domain.ProwlarrConfig
import dev.mkdev.prowlarrexplorer.domain.QbitConfig
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    initial: AppSettings,
    onTestProwlarr: suspend (ProwlarrConfig) -> Result<String>,
    onTestQbit: suspend (QbitConfig) -> Result<String>,
    onSave: (AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    var url by remember { mutableStateOf(initial.prowlarr.url) }
    var apiKey by remember { mutableStateOf(initial.prowlarr.apiKey) }
    var qbUrl by remember { mutableStateOf(initial.qbit.url) }
    var qbUser by remember { mutableStateOf(initial.qbit.username) }
    var qbPass by remember { mutableStateOf(initial.qbit.password) }

    fun prowlarr() = ProwlarrConfig(url, apiKey)
    fun qbit() = QbitConfig(qbUrl, qbUser, qbPass)

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
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text("URL Prowlarr") },
                placeholder = { Text("http://100.x.y.z:9696") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = apiKey, onValueChange = { apiKey = it },
                label = { Text("Clé API") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Hint("Prowlarr → Settings → General → Security → API Key. Chiffrée dans le Keystore du téléphone.")
            TestButton(enabled = prowlarr().configured, test = { onTestProwlarr(prowlarr()) })

            HorizontalDivider()

            Text("qBittorrent", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = qbUrl, onValueChange = { qbUrl = it },
                label = { Text("URL WebUI qBittorrent") },
                placeholder = { Text("http://100.x.y.z:8080") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
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
            TestButton(enabled = qbit().configured, test = { onTestQbit(qbit()) })

            Button(
                enabled = prowlarr().configured,
                onClick = { onSave(AppSettings(prowlarr(), qbit())); onBack() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Enregistrer") }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun TestButton(enabled: Boolean, test: suspend () -> Result<String>) {
    var result by remember { mutableStateOf<String?>(null) }
    var ok by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    OutlinedButton(
        enabled = enabled && !testing,
        onClick = {
            testing = true
            scope.launch {
                test().onSuccess { result = it; ok = true }.onFailure { result = it.short(); ok = false }
                testing = false
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (testing) "Test…" else "Tester") }

    result?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium,
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
    }
}
