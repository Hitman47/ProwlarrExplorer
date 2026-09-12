package dev.mkdev.prowlarrexplorer.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
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
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.mkdev.prowlarrexplorer.domain.CategoryFilter
import dev.mkdev.prowlarrexplorer.domain.ParsedTitle
import dev.mkdev.prowlarrexplorer.domain.Release
import dev.mkdev.prowlarrexplorer.domain.ReleaseTitle
import dev.mkdev.prowlarrexplorer.domain.SortMode
import dev.mkdev.prowlarrexplorer.domain.Tag
import dev.mkdev.prowlarrexplorer.domain.TagKind
import dev.mkdev.prowlarrexplorer.domain.humanAge
import dev.mkdev.prowlarrexplorer.domain.humanSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    vm: SearchViewModel,
    state: UiState,
    twoPane: Boolean,
    onSettings: () -> Unit,
    onShowDownloads: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val focus = LocalFocusManager.current
    var showIndexers by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        val m = state.message ?: return@LaunchedEffect
        val r = snackbar.showSnackbar(m.text, actionLabel = if (m.goDownloads) "Voir" else null)
        if (r == SnackbarResult.ActionPerformed) onShowDownloads()
        vm.consumeMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prowlarr Explorer") },
                actions = {
                    if (state.qbit?.configured == true) IconButton(onClick = { vm.openManualAdd() }) {
                        Icon(Icons.Default.AddLink, contentDescription = "Ajouter un lien")
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Réglages") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Row(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
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
                    AssistChip(
                        onClick = { showIndexers = true },
                        label = { Text(state.indexerLabel) },
                        leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                    )
                    CategoryFilter.entries.forEach { c ->
                        FilterChip(selected = state.category == c, onClick = { vm.setCategory(c) }, label = { Text(c.label) })
                    }
                }

                if (state.searched) Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Tri", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SortMode.entries.forEach { s ->
                        FilterChip(selected = state.sort == s, onClick = { vm.setSort(s) }, label = { Text(s.label) })
                    }
                    FilterChip(selected = state.hideDead, onClick = vm::toggleHideDead, label = { Text("Masquer 0 seed") })
                }

                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                else Spacer(Modifier.height(8.dp))

                when {
                    state.error != null -> Message(state.error)
                    !state.searched -> HistoryPanel(state, onPick = { vm.searchFor(it) }, onClear = vm::clearHistory)
                    state.results.isEmpty() && !state.loading -> Message(
                        if (state.hiddenCount > 0) "Aucun résultat avec seeders (${state.hiddenCount} masqués)." else "Aucun résultat.",
                    )
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            Text(
                                "${state.results.size} résultats" +
                                    (if (state.mergedCount > 0) " · ${state.mergedCount} doublons regroupés" else "") +
                                    (if (state.hiddenCount > 0) " · ${state.hiddenCount} masqués" else ""),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        items(state.results, key = { "${it.indexerId}:${it.guid}" }) { r ->
                            SwipeToGrab(onGrab = { vm.grab(r) }) {
                                ReleaseRow(r, others = state.alternates(r).size, selected = twoPane && state.selected?.guid == r.guid, onClick = { vm.select(r) })
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }

            if (twoPane) {
                VerticalDivider()
                Box(Modifier.width(400.dp).fillMaxHeight()) {
                    val sel = state.selected
                    if (sel == null) Message("Sélectionne un résultat.")
                    else ReleaseDetail(sel, state, vm)
                }
            }
        }
    }

    if (showIndexers) IndexerDialog(vm, state, onDismiss = { showIndexers = false })
    state.manualAdd?.let { prefill -> ManualAddDialog(prefill, state, vm) }
    if (!twoPane) state.selected?.let { r ->
        ModalBottomSheet(onDismissRequest = { vm.select(null) }) {
            ReleaseDetail(r, state, vm)
        }
    }
}

/** Ajout manuel : magnet ou URL de .torrent (saisi, collé, ou reçu par partage) + catégorie. */
@Composable
private fun ManualAddDialog(prefill: String, state: UiState, vm: SearchViewModel) {
    var link by remember(prefill) { mutableStateOf(prefill) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val valid = SearchViewModel.isTorrentLink(link)
    AlertDialog(
        onDismissRequest = { if (!state.adding) vm.closeManualAdd() },
        title = { Text("Ajouter à qBittorrent") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = link, onValueChange = { link = it },
                    label = { Text("Lien magnet ou .torrent") },
                    placeholder = { Text("magnet:?xt=urn:btih:…") },
                    maxLines = 4,
                    trailingIcon = {
                        IconButton(onClick = { clipboard.getText()?.text?.trim()?.let { link = it } }) {
                            Icon(Icons.Default.AddLink, contentDescription = "Coller")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                CategoryPicker(state.categories, state.qbCategory, onPick = vm::setQbCategory)
            }
        },
        confirmButton = {
            TextButton(enabled = valid && !state.adding, onClick = { vm.addManual(link) }) {
                Text(if (state.adding) "Envoi…" else "Envoyer")
            }
        },
        dismissButton = { TextButton(enabled = !state.adding, onClick = vm::closeManualAdd) { Text("Annuler") } },
    )
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryPanel(state: UiState, onPick: (String) -> Unit, onClear: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (state.history.isEmpty()) {
            Text(
                "Lance une recherche : Prowlarr interroge ${state.enabledIndexers.size} indexer(s). " +
                    "Astuce : sélectionne un titre dans n'importe quelle app → « Prowlarr Explorer ».",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
            return
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text("Récentes", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClear) { Text("Effacer") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.history.forEach { q -> SuggestionChip(onClick = { onPick(q) }, label = { Text(q) }) }
        }
    }
}

/** Glisser vers la droite = envoyer, sans ouvrir la fiche ; la ligne revient en place. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToGrab(onGrab: () -> Unit, content: @Composable () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val dismiss = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            if (v == SwipeToDismissBoxValue.StartToEnd) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onGrab()
            }
            false
        },
    )
    SwipeToDismissBox(
        state = dismiss,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Row(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(8.dp))
                Text("Envoyer", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
            }
        },
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) { content() }
    }
}

@Composable
private fun ReleaseRow(r: Release, others: Int, selected: Boolean, onClick: () -> Unit) {
    val parsed = remember(r.guid) { ReleaseTitle.parse(r.title) }
    Column(
        Modifier.fillMaxWidth()
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.surfaceVariant) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(parsed.heading, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (parsed.tags.isNotEmpty()) TagRow(parsed.tags)
        Text(r.title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(r.size.humanSize(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            SeedLeech(r)
            Text(r.humanAge(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(
                r.indexer + if (others > 0) " +$others" else "",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagRow(tags: List<Tag>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        tags.forEach { t ->
            val (bg, fg) = when (t.kind) {
                TagKind.RES -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                TagKind.HDR -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                TagKind.LANG -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                t.label, style = MaterialTheme.typography.labelSmall, color = fg,
                modifier = Modifier.background(bg, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
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

/** Fiche d'une release : en bottom sheet (téléphone) ou volet droit (tablette paysage). */
@Composable
fun ReleaseDetail(r: Release, state: UiState, vm: SearchViewModel) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val parsed: ParsedTitle = remember(r.guid) { ReleaseTitle.parse(r.title) }
    val grabbing = state.grabbing == r.guid
    val direct = state.direct(r)
    fun open(url: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(parsed.heading, style = MaterialTheme.typography.titleLarge)
        if (parsed.tags.isNotEmpty()) TagRow(parsed.tags)
        Text(r.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        val others = state.alternates(r)
        if (others.isNotEmpty()) {
            Text("Aussi sur :", style = MaterialTheme.typography.labelMedium)
            others.forEach { o ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.select(o) }.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(o.indexer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    SeedLeech(o)
                    Text(o.humanAge(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (direct) CategoryPicker(state.categories, state.qbCategory, onPick = vm::setQbCategory)

        Button(
            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.grab(r) },
            enabled = !grabbing, modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Download, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    grabbing -> "Envoi…"
                    direct -> "Envoyer à qBittorrent"
                    else -> "Envoyer via Prowlarr"
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (r.infoUrl != null) {
                OutlinedButton(onClick = { vm.openInfo(r) }, modifier = Modifier.weight(1f)) {
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
