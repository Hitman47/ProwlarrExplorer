package dev.mkdev.prowlarrexplorer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mkdev.prowlarrexplorer.domain.humanSize

/** Carte « nouvelle version » au-dessus du contenu ; disparaît si ignorée ou une fois installée. */
@Composable
fun UpdateBanner(state: UpdateState, onInstall: () -> Unit, onDismiss: (() -> Unit)?) {
    val info = state.available ?: return
    if (state.dismissed) return

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = if (onDismiss != null) 16.dp else 0.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Version ${info.version} disponible (${info.size.humanSize()})", style = MaterialTheme.typography.titleSmall)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            state.progress?.let { p ->
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                Text("Téléchargement ${(p * 100).toInt()} %", style = MaterialTheme.typography.bodySmall)
            } ?: Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onInstall) { Text("Installer") }
                if (onDismiss != null) TextButton(onClick = onDismiss) { Text("Plus tard") }
            }
        }
    }
}
