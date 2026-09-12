package dev.mkdev.prowlarrexplorer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mkdev.prowlarrexplorer.domain.ReleaseTitle
import dev.mkdev.prowlarrexplorer.domain.SentEntry
import dev.mkdev.prowlarrexplorer.domain.humanSize
import java.text.DateFormat
import java.util.Date

/**
 * Journal des envois. Une entrée dont le torrent est encore dans qBittorrent (même infoHash)
 * est cliquable et ouvre sa fiche dans l'onglet Téléchargements.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    entries: List<SentEntry>,
    presentHashes: Set<String>,
    onOpen: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Journal des envois") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") } },
                actions = {
                    if (entries.isNotEmpty()) IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Vider")
                    }
                },
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
                Text("Aucun envoi pour l'instant.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(entries, key = { "${it.time}:${it.title}" }) { e ->
                val present = e.infoHash != null && e.infoHash.lowercase() in presentHashes
                val parsed = remember(e.title) { ReleaseTitle.parse(e.title) }
                Column(
                    Modifier.fillMaxWidth()
                        .then(if (present) Modifier.clickable { onOpen(e.infoHash!!.lowercase()) } else Modifier)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(parsed.heading, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(fmt.format(Date(e.time)), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        if (e.size > 0) Text(e.size.humanSize(), style = MaterialTheme.typography.labelMedium)
                        Text(
                            listOf(e.indexer.ifBlank { null }, e.target, e.category.ifBlank { null }).filterNotNull().joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (present) "dans qBittorrent ›" else "",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Vider le journal ?") },
        text = { Text("${entries.size} entrées seront effacées. Les torrents dans qBittorrent ne sont pas touchés.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text("Vider", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annuler") } },
    )
}
