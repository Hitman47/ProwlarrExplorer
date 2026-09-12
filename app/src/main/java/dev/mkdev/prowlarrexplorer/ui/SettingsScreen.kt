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
import dev.mkdev.prowlarrexplorer.domain.ProwlarrConfig
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    initial: ProwlarrConfig,
    onTest: suspend (ProwlarrConfig) -> Result<String>,
    onSave: (ProwlarrConfig) -> Unit,
    onBack: () -> Unit,
) {
    var url by remember { mutableStateOf(initial.url) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testOk by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun current() = ProwlarrConfig(url, apiKey)

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
            OutlinedTextField(
                value = url, onValueChange = { url = it; testResult = null },
                label = { Text("URL Prowlarr") },
                placeholder = { Text("http://100.x.y.z:9696") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = apiKey, onValueChange = { apiKey = it; testResult = null },
                label = { Text("Clé API") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Prowlarr → Settings → General → Security → API Key. La clé est chiffrée dans le Keystore du téléphone. " +
                    "L'envoi utilise le client de téléchargement déclaré dans Prowlarr (Settings → Download Clients).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = current().configured && !testing,
                    onClick = {
                        testing = true
                        scope.launch {
                            onTest(current())
                                .onSuccess { testResult = it; testOk = true }
                                .onFailure { testResult = it.short(); testOk = false }
                            testing = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(if (testing) "Test…" else "Tester") }
                Button(
                    enabled = current().configured,
                    onClick = { onSave(current()); onBack() },
                    modifier = Modifier.weight(1f),
                ) { Text("Enregistrer") }
            }

            testResult?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (testOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
