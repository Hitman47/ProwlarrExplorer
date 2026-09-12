package dev.mkdev.prowlarrexplorer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import android.net.Uri
import dev.mkdev.prowlarrexplorer.data.BackupCodec
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import dev.mkdev.prowlarrexplorer.work.DownloadWatcher
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.mkdev.prowlarrexplorer.BuildConfig
import dev.mkdev.prowlarrexplorer.data.short
import dev.mkdev.prowlarrexplorer.domain.ProwlarrConfig
import dev.mkdev.prowlarrexplorer.domain.QbitConfig
import dev.mkdev.prowlarrexplorer.domain.ThemeMode
import dev.mkdev.prowlarrexplorer.domain.UrlParts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class SettingsPage { HOME, PROWLARR, QBIT }

/** Résultat d'un test de connexion ; null = pas encore testé. */
sealed class Probe {
    data object Testing : Probe()
    data class Ok(val text: String) : Probe()
    data class Failed(val text: String) : Probe()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
fun SettingsHome(
    prowlarr: ProwlarrConfig,
    qbit: QbitConfig,
    prowlarrProbe: Probe?,
    qbitProbe: Probe?,
    theme: ThemeMode,
    onTheme: (ThemeMode) -> Unit,
    onOpen: (SettingsPage) -> Unit,
    update: UpdateState,
    onCheckUpdate: suspend () -> Result<String>,
    onInstallUpdate: () -> Unit,
    notifyDone: Boolean,
    onNotifyDone: (Boolean) -> Unit,
    onExport: suspend (Uri, String) -> Result<String>,
    onImport: suspend (Uri, String) -> Result<String>,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var backupResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    // Phrase demandée après le choix du fichier ; (uri, export?) en attente.
    var pending by remember { mutableStateOf<Pair<Uri, Boolean>?>(null) }
    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { pending = it to true }
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { pending = it to false }
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onNotifyDone(granted)
    }
    SettingsScaffold("Réglages", onBack) {
        ServiceRow("Prowlarr", prowlarr.url.ifBlank { "Non configuré" }, if (prowlarr.configured) prowlarrProbe else null) { onOpen(SettingsPage.PROWLARR) }
        ServiceRow("qBittorrent", qbit.url.ifBlank { "Non configuré (optionnel)" }, if (qbit.configured) qbitProbe else null) { onOpen(SettingsPage.QBIT) }

        HorizontalDivider()
        Text("Notifications", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Téléchargement terminé", style = MaterialTheme.typography.bodyLarge)
                Hint("Vérification en arrière-plan toutes les 15 min ; nécessite qBittorrent.")
            }
            Switch(
                checked = notifyDone && qbit.configured && DownloadWatcher.canNotify(ctx),
                enabled = qbit.configured,
                onCheckedChange = { on ->
                    if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !DownloadWatcher.canNotify(ctx)) {
                        askPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else onNotifyDone(on)
                },
            )
        }

        HorizontalDivider()
        Text("Apparence", style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = theme == m, onClick = { onTheme(m) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = ThemeMode.entries.size),
                ) { Text(m.label) }
            }
        }

        HorizontalDivider()
        Text("Sauvegarde des réglages", style = MaterialTheme.typography.titleMedium)
        Hint("Fichier .${BackupCodec.EXTENSION} chiffré par une phrase secrète (AES-256, PBKDF2). Contient URLs, clés, login, thème, notifications, catégorie.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { createDoc.launch("prowlarr-explorer-reglages.${BackupCodec.EXTENSION}") }, modifier = Modifier.weight(1f)) { Text("Exporter…") }
            OutlinedButton(onClick = { openDoc.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) { Text("Importer…") }
        }
        backupResult?.let { (ok, text) ->
            Text(text, style = MaterialTheme.typography.bodyMedium,
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }

        HorizontalDivider()
        Text("Mises à jour", style = MaterialTheme.typography.titleMedium)
        Text("Version installée : ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
        Hint("Releases GitHub Hitman47/ProwlarrExplorer, vérifiées à chaque ouverture. Mise à jour en place : réglages conservés.")
        ActionButton("Vérifier maintenant", enabled = true, action = onCheckUpdate)
        // Même carte que sur l'écran principal : Installer / progression, ici sans « Plus tard ».
        UpdateBanner(update.copy(dismissed = false), onInstall = onInstallUpdate, onDismiss = null)
    }

    pending?.let { (uri, export) ->
        PassphraseDialog(
            export = export,
            onDismiss = { pending = null },
            onConfirm = { pass ->
                pending = null
                scope.launch {
                    val r = if (export) onExport(uri, pass) else onImport(uri, pass)
                    backupResult = r.fold({ true to it }, { false to it.short() })
                }
            },
        )
    }
}

/** Phrase secrète de la sauvegarde ; à l'export, saisie deux fois. */
@Composable
private fun PassphraseDialog(export: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    val ok = p1.length >= 6 && (!export || p1 == p2)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (export) "Phrase secrète de la sauvegarde" else "Phrase secrète du fichier") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SecretField(p1, onChange = { p1 = it }, label = "Phrase (6 caractères min.)", paste = !export)
                if (export) SecretField(p2, onChange = { p2 = it }, label = "Confirmer", paste = false)
                if (export) Hint("Sans cette phrase, le fichier est inutilisable. Elle n'est stockée nulle part.")
                else Hint("Tous les réglages actuels seront remplacés.")
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onConfirm(p1) }) { Text(if (export) "Exporter" else "Restaurer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun ServiceRow(name: String, subtitle: String, probe: Probe?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(name) },
        supportingContent = {
            Text(
                when (probe) {
                    is Probe.Ok -> probe.text
                    is Probe.Failed -> probe.text
                    Probe.Testing -> "Test…"
                    null -> subtitle
                },
                color = if (probe is Probe.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = { StatusDot(probe) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun StatusDot(probe: Probe?) {
    val color = when (probe) {
        is Probe.Ok -> Color(0xFF2E7D32)
        is Probe.Failed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        if (probe == Probe.Testing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        else Box(Modifier.size(12.dp).background(color, CircleShape))
    }
}

@Composable
fun ProwlarrSettings(
    initial: ProwlarrConfig,
    onboarding: Boolean,
    onTest: suspend (ProwlarrConfig) -> Result<String>,
    onSave: (ProwlarrConfig) -> Unit,
    onBack: () -> Unit,
) {
    var url by remember { mutableStateOf(UrlParts.parse(initial.url, 9696)) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    val config = ProwlarrConfig(url.toUrl(), apiKey)
    val probe = autoProbe(config, config.configured) { onTest(config) }

    SettingsScaffold(if (onboarding) "Étape 1/2 · Prowlarr" else "Prowlarr", onBack) {
        if (onboarding) Hint("Bienvenue. Renseigne ton instance Prowlarr ; le test de connexion se lance tout seul.")
        UrlFields(url, onChange = { url = it })
        SecretField(apiKey, onChange = { apiKey = it }, label = "Clé API")
        Hint("Prowlarr → Settings → General → Security → API Key. Chiffrée dans le Keystore du téléphone.")
        ProbeLine(probe)
        Button(enabled = config.configured, onClick = { onSave(config); onBack() }, modifier = Modifier.fillMaxWidth()) {
            Text(if (onboarding) "Continuer" else "Enregistrer")
        }
    }
}

@Composable
fun QbitSettings(
    initial: QbitConfig,
    onboarding: Boolean,
    onTest: suspend (QbitConfig) -> Result<String>,
    onSave: (QbitConfig) -> Unit,
    onBack: () -> Unit,
) {
    var url by remember { mutableStateOf(UrlParts.parse(initial.url, 8080)) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var user by remember { mutableStateOf(initial.username) }
    var pass by remember { mutableStateOf(initial.password) }
    val config = QbitConfig(url.toUrl(), apiKey, user, pass)
    val probe = autoProbe(config, config.configured) { onTest(config) }

    SettingsScaffold(if (onboarding) "Étape 2/2 · qBittorrent" else "qBittorrent", onBack) {
        if (onboarding) Hint("Optionnel : donne accès à l'onglet Téléchargements (progression, pause, suppression).")
        UrlFields(url, onChange = { url = it })
        SecretField(apiKey, onChange = { apiKey = it }, label = "Clé API (qBittorrent ≥ 5.2)")
        Hint("qBittorrent → Options → WebUI → Authentication → API Key → Générer. Prioritaire sur le login.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = user, onValueChange = { user = it }, enabled = apiKey.isBlank(),
                label = { Text("Login") }, singleLine = true, modifier = Modifier.weight(1f))
            Box(Modifier.weight(1f)) { SecretField(pass, onChange = { pass = it }, label = "Mot de passe", paste = false, enabled = apiKey.isBlank()) }
        }
        Hint(
            "Sans clé API : login/mot de passe (session par cookie). Tout vide = « Bypass authentication for clients in " +
                "whitelisted IP subnets » activé dans qBittorrent avec le sous-réseau Tailscale 100.64.0.0/10.",
        )
        ProbeLine(probe)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onboarding) OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Passer") }
            Button(enabled = config.configured || !onboarding, onClick = { onSave(config); onBack() }, modifier = Modifier.weight(1f)) {
                Text(if (onboarding) "Terminer" else "Enregistrer")
            }
        }
    }
}

/** Test automatique 1 s après la dernière modification. */
@Composable
private fun <T> autoProbe(config: T, enabled: Boolean, test: suspend () -> Result<String>): Probe? {
    var probe by remember { mutableStateOf<Probe?>(null) }
    LaunchedEffect(config) {
        if (!enabled) { probe = null; return@LaunchedEffect }
        delay(1_000)
        probe = Probe.Testing
        probe = test().fold({ Probe.Ok(it) }, { Probe.Failed(it.short()) })
    }
    return probe
}

@Composable
private fun ProbeLine(probe: Probe?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusDot(probe)
        Text(
            when (probe) {
                is Probe.Ok -> probe.text
                is Probe.Failed -> probe.text
                Probe.Testing -> "Test de connexion…"
                null -> "Renseigne l'adresse pour tester la connexion."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = when (probe) {
                is Probe.Ok -> MaterialTheme.colorScheme.primary
                is Probe.Failed -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** Champ secret : œil pour afficher, bouton coller depuis le presse-papiers. */
@Composable
private fun SecretField(value: String, onChange: (String) -> Unit, label: String, paste: Boolean = true, enabled: Boolean = true) {
    var visible by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    OutlinedTextField(
        value = value, onValueChange = onChange,
        enabled = enabled,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            Row {
                if (paste) IconButton(onClick = { clipboard.getText()?.text?.trim()?.let(onChange) }) {
                    Icon(Icons.Default.ContentPaste, contentDescription = "Coller")
                }
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "Afficher")
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Schéma / hôte / port ; le port vide = port par défaut du schéma. */
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
