package dev.mkdev.prowlarrexplorer.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.mkdev.prowlarrexplorer.domain.Torrent
import dev.mkdev.prowlarrexplorer.domain.humanEta
import dev.mkdev.prowlarrexplorer.domain.humanSize
import dev.mkdev.prowlarrexplorer.domain.humanSpeed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(vm: DownloadsViewModel, state: DownloadsState, onSettings: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val configured = state.config?.configured == true

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }
    // Sondage uniquement tant que l'écran est affiché.
    DisposableEffect(configured) {
        if (configured) vm.startPolling()
        onDispose { vm.stopPolling() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Téléchargements")
                        if (state.loaded) Text(
                            "↓ ${state.totalDown.humanSpeed().ifEmpty { "0" }}   ↑ ${state.totalUp.humanSpeed().ifEmpty { "0" }}",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Réglages") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                !configured -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("qBittorrent n'est pas configuré.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onSettings) { Text("Configurer qBittorrent") }
                    }
                }
                else -> {
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TorrentFilter.entries.forEach { f ->
                            FilterChip(selected = state.filter == f, onClick = { vm.setFilter(f) }, label = { Text(f.label) })
                        }
                    }
                    if (!state.loaded && state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                    else Spacer(Modifier.height(8.dp))

                    when {
                        state.error != null && !state.loaded -> Message(state.error)
                        state.loaded && state.visible.isEmpty() -> Message("Aucun torrent.")
                        else -> LazyColumn(Modifier.fillMaxSize()) {
                            if (state.error != null) item {
                                Text(state.error, color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
                            }
                            items(state.visible, key = { it.hash }) { t ->
                                TorrentRow(t, onClick = { vm.select(t) })
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    state.selected?.let { t ->
        TorrentSheet(t, webUi = state.config?.url, busy = state.busy,
            onTogglePause = vm::togglePause, onDelete = vm::delete, onDismiss = { vm.select(null) })
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TorrentRow(t: Torrent, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(t.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { t.progress.toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = when {
                t.error -> MaterialTheme.colorScheme.error
                t.paused -> MaterialTheme.colorScheme.outline
                else -> MaterialTheme.colorScheme.primary
            },
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${(t.progress * 100).toInt()} %", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(t.size.humanSize(), style = MaterialTheme.typography.labelMedium)
            if (t.dlspeed > 0) Text("↓ ${t.dlspeed.humanSpeed()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            t.eta.humanEta().takeIf { it.isNotEmpty() && !t.done }?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            Text(t.stateLabel, style = MaterialTheme.typography.labelSmall,
                color = if (t.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TorrentSheet(
    t: Torrent, webUi: String?, busy: Boolean,
    onTogglePause: () -> Unit, onDelete: (Boolean) -> Unit, onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var withFiles by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t.name, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${(t.progress * 100).toInt()} %", fontWeight = FontWeight.SemiBold)
                Text(t.size.humanSize())
                Text("▲ ${t.numSeeds}  ▼ ${t.numLeechs}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                listOf(t.stateLabel, t.category.ifBlank { null }, t.dlspeed.humanSpeed().ifEmpty { null }?.let { "↓ $it" },
                    t.eta.humanEta().ifEmpty { null }?.takeIf { !t.done }?.let { "reste $it" })
                    .filterNotNull().joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(t.savePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onTogglePause, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Icon(if (t.paused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (t.paused) "Reprendre" else "Pause")
                }
                OutlinedButton(onClick = { confirmDelete = true }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Supprimer")
                }
            }
            webUi?.let { u ->
                OutlinedButton(
                    onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, u.toUri())) } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Ouvrir la WebUI qBittorrent")
                }
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Supprimer ce torrent ?") },
        text = {
            Column {
                Text(t.name, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { withFiles = !withFiles }) {
                    Checkbox(checked = withFiles, onCheckedChange = { withFiles = it })
                    Text("Supprimer aussi les fichiers sur le disque")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { confirmDelete = false; onDelete(withFiles) }) {
                Text("Supprimer", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
    )
}
