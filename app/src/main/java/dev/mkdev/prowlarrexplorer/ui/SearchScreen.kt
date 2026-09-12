package dev.mkdev.prowlarrexplorer.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.mkdev.prowlarrexplorer.domain.CategoryFilter
import dev.mkdev.prowlarrexplorer.domain.Release
import dev.mkdev.prowlarrexplorer.domain.humanAge
import dev.mkdev.prowlarrexplorer.domain.humanSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: SearchViewModel, state: UiState, onSettings: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val focus = LocalFocusManager.current
    var showIndexers by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prowlarr Explorer") },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Réglages") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                placeholder = { Text("Titre, année, saison…") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Effacer")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.search() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CategoryFilter.entries.forEach { c ->
                    FilterChip(selected = state.category == c, onClick = { vm.setCategory(c) }, label = { Text(c.label) })
                }
                AssistChip(
                    onClick = { showIndexers = true },
                    label = { Text("Indexers : ${state.indexerLabel}") },
                    leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                )
            }

            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            else Spacer(Modifier.height(12.dp))

            when {
                state.error != null -> Message(state.error)
                !state.searched -> Message("Lance une recherche : Prowlarr interroge ${state.enabledIndexers.size} indexer(s).")
                state.results.isEmpty() && !state.loading -> Message("Aucun résultat.")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.results, key = { "${it.indexerId}:${it.guid}" }) { r ->
                        ReleaseRow(r, onClick = { vm.select(r) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showIndexers) IndexerDialog(vm, state, onDismiss = { showIndexers = false })
    state.selected?.let { r ->
        ReleaseSheet(r, grabbing = state.grabbing, onGrab = vm::grab, onDismiss = { vm.select(null) })
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReleaseRow(r: Release, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(r.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(r.size.humanSize(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            SeedLeech(r)
            Text(r.humanAge(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(r.indexer, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(110.dp))
        }
    }
}

@Composable
private fun SeedLeech(r: Release) {
    if (r.protocol == "usenet") {
        Text("usenet", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val s = r.seeders
    val color = when {
        s == null -> MaterialTheme.colorScheme.onSurfaceVariant
        s == 0 -> MaterialTheme.colorScheme.error
        s < 5 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Text("▲ ${s ?: "?"}  ▼ ${r.leechers ?: "?"}", style = MaterialTheme.typography.labelMedium, color = color)
}

@Composable
private fun IndexerDialog(vm: SearchViewModel, state: UiState, onDismiss: () -> Unit) {
    val all = state.enabledIndexers
    val checked = state.selectedIndexers ?: all.map { it.id }.toSet()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Indexers") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { vm.selectAllIndexers(true) }) { Text("Tous") }
                    TextButton(onClick = { vm.selectAllIndexers(false) }) { Text("Aucun") }
                }
                if (all.isEmpty()) Text("Aucun indexer activé (ou liste non chargée).", style = MaterialTheme.typography.bodySmall)
                LazyColumn {
                    items(all, key = { it.id }) { i ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.toggleIndexer(i.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = i.id in checked, onCheckedChange = { vm.toggleIndexer(i.id) })
                            Text(i.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss(); if (state.searched) vm.search() }) { Text("OK") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReleaseSheet(r: Release, grabbing: Boolean, onGrab: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    fun open(url: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(r.title, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(r.size.humanSize(), fontWeight = FontWeight.SemiBold)
                SeedLeech(r)
                Text(r.humanAge())
                r.grabs?.let { Text("$it grabs", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(
                listOf(r.indexer, r.categories.joinToString { it.name }.ifBlank { null }).filterNotNull().joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(onClick = onGrab, enabled = !grabbing, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (grabbing) "Envoi…" else "Envoyer au client de téléchargement")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                r.infoUrl?.let { u ->
                    OutlinedButton(onClick = { open(u) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Page indexer")
                    }
                }
                r.magnetUrl?.let { u ->
                    OutlinedButton(onClick = { open(u) }, modifier = Modifier.weight(1f)) { Text("Magnet") }
                }
            }
        }
    }
}
