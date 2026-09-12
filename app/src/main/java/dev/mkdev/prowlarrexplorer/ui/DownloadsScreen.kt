package dev.mkdev.prowlarrexplorer.ui

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.mkdev.prowlarrexplorer.domain.Torrent
import dev.mkdev.prowlarrexplorer.domain.humanEta
import dev.mkdev.prowlarrexplorer.domain.humanSize
import dev.mkdev.prowlarrexplorer.domain.humanSpeed

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DownloadsScreen(vm: DownloadsViewModel, state: DownloadsState, twoPane: Boolean, onSettings: () -> Unit) {
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
        Row(Modifier.padding(padding).fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when {
                    !configured -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("qBittorrent n'est pas configuré.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = onSettings) { Text("Configurer qBittorrent") }
                        }
                    }
                    state.error != null && !state.loaded -> Message(state.error)
                    state.loaded && state.torrents.isEmpty() -> Message("Aucun torrent.")
                    !state.loaded -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    else -> PullToRefreshBox(isRefreshing = false, onRefresh = { vm.refreshNow() }) {
                        LazyColumn(Modifier.fillMaxSize()) {
                            if (state.error != null) item {
                                Text(state.error, color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
                            }
                            state.sections.forEach { (section, list) ->
                                stickyHeader(key = "h:${section.name}") {
                                    Text(
                                        "${section.label} · ${list.size}",
                                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                    )
                                }
                                items(list, key = { it.hash }) { t ->
                                    SwipeToToggle(paused = t.paused, onToggle = { vm.togglePause(t) }) {
                                        TorrentRow(t, selected = twoPane && state.selected?.hash == t.hash, onClick = { vm.select(t) })
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }

            if (twoPane) {
                VerticalDivider()
                Box(Modifier.width(400.dp).fillMaxHeight()) {
                    val sel = state.selected
                    if (sel == null) Message("Sélectionne un torrent.")
                    else TorrentDetail(sel, state, vm)
                }
            }
        }
    }

    if (!twoPane) state.selected?.let { t ->
        ModalBottomSheet(onDismissRequest = { vm.select(null) }) {
            TorrentDetail(t, state, vm)
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Glisser vers la droite = pause / reprise ; la ligne revient en place. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToToggle(paused: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val dismiss = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            if (v == SwipeToDismissBoxValue.StartToEnd) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle()
            }
            false
        },
    )
    SwipeToDismissBox(
        state = dismiss,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Row(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(8.dp))
                Text(if (paused) "Reprendre" else "Pause", color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.SemiBold)
            }
        },
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) { content() }
    }
}

private fun Torrent.progressLine(): String = buildString {
    append(completed.humanSize()).append(" / ").append(size.humanSize())
    if (dlspeed > 0) append(" · ↓ ").append(dlspeed.humanSpeed())
    if (!done) eta.humanEta().takeIf { it.isNotEmpty() }?.let { append(" · ").append(it) }
}

@Composable
private fun TorrentRow(t: Torrent, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.surfaceVariant) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
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
            Text(t.progressLine(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(t.stateLabel, style = MaterialTheme.typography.labelSmall,
                color = if (t.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Fiche d'un torrent : bottom sheet (téléphone) ou volet droit (tablette paysage). */
@Composable
fun TorrentDetail(t: Torrent, state: DownloadsState, vm: DownloadsViewModel) {
    val ctx = LocalContext.current
    val busy = state.busy == t.hash
    var confirmDelete by remember { mutableStateOf(false) }
    var withFiles by remember { mutableStateOf(false) }
    var showLimits by remember { mutableStateOf(false) }
    var showFiles by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(t.name, style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(progress = { t.progress.toFloat() }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("${(t.progress * 100).toInt()} %", fontWeight = FontWeight.SemiBold)
            Text(t.progressLine())
        }
        Text(
            listOf(t.stateLabel, "▲ ${t.numSeeds}  ▼ ${t.numLeechs}",
                t.dlLimit.takeIf { it > 0 }?.let { "↓ max ${it.humanSpeed()}" }, t.upLimit.takeIf { it > 0 }?.let { "↑ max ${it.humanSpeed()}" })
                .filterNotNull().joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(t.savePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)

        CategoryPicker(state.categories, t.category, onPick = { vm.setCategory(it) }, label = "Catégorie", enabled = !busy)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.togglePause(t) }, enabled = !busy, modifier = Modifier.weight(1f)) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showLimits = true }, enabled = !busy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Speed, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Limites")
            }
            OutlinedButton(onClick = vm::recheck, enabled = !busy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Revérifier")
            }
        }
        OutlinedButton(
            onClick = { showFiles = !showFiles; if (showFiles) vm.loadFiles() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Folder, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(if (showFiles) "Masquer les fichiers" else "Fichiers" + (state.files?.let { " (${it.size})" } ?: ""))
        }
        if (showFiles) FilesList(state, vm, t)

        state.config?.url?.let { u ->
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

    if (showLimits) LimitsDialog(t, onApply = { d, u -> vm.setLimits(d, u); showLimits = false }, onDismiss = { showLimits = false })

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
            TextButton(onClick = { confirmDelete = false; vm.delete(withFiles) }) {
                Text("Supprimer", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
    )
}

/** Fichiers du torrent : case = téléchargé ou non (priorité 0/1), taille, progression. */
@Composable
private fun FilesList(state: DownloadsState, vm: DownloadsViewModel, t: Torrent) {
    val files = state.files
    when {
        state.filesFor != t.hash || files == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
        files.isEmpty() -> Text("Aucun fichier (métadonnées pas encore reçues).", style = MaterialTheme.typography.bodySmall)
        else -> Column {
            files.take(300).forEach { f ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.toggleFile(f) }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = f.wanted, onCheckedChange = { vm.toggleFile(f) })
                    Column(Modifier.weight(1f)) {
                        Text(f.shortName, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${f.size.humanSize()} · ${(f.progress * 100).toInt()} %",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (files.size > 300) Text("… ${files.size - 300} autres fichiers (WebUI pour le détail).", style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Limites de vitesse par torrent, en Ko/s ; 0 ou vide = illimité. */
@Composable
private fun LimitsDialog(t: Torrent, onApply: (Long, Long) -> Unit, onDismiss: () -> Unit) {
    var down by remember { mutableStateOf(if (t.dlLimit > 0) (t.dlLimit / 1024).toString() else "") }
    var up by remember { mutableStateOf(if (t.upLimit > 0) (t.upLimit / 1024).toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Limites de vitesse") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = down, onValueChange = { v -> if (v.all { it.isDigit() }) down = v },
                    label = { Text("↓ Ko/s (vide = illimité)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = up, onValueChange = { v -> if (v.all { it.isDigit() }) up = v },
                    label = { Text("↑ Ko/s (vide = illimité)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onApply(down.toLongOrNull() ?: 0, up.toLongOrNull() ?: 0) }) { Text("Appliquer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
