package dev.mkdev.prowlarrexplorer.ui

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.mkdev.prowlarrexplorer.domain.QbitCategory

/**
 * Message temporaire (4 s même avec un bouton d'action : Material3 le rendrait sinon indéfini),
 * avec croix de fermeture ; l'hôte [DismissibleSnackbarHost] ajoute le glisser vers le bas.
 */
suspend fun SnackbarHostState.showTimed(text: String, actionLabel: String? = null): SnackbarResult =
    showSnackbar(text, actionLabel = actionLabel, withDismissAction = true, duration = SnackbarDuration.Short)

@Composable
fun DismissibleSnackbarHost(state: SnackbarHostState) {
    SnackbarHost(state) { data ->
        Snackbar(
            snackbarData = data,
            modifier = Modifier.pointerInput(data) {
                detectVerticalDragGestures { _, dy -> if (dy > 6f) data.dismiss() }
            },
        )
    }
}

/** Liste déroulante des catégories qBittorrent ; « Aucune » = chaîne vide. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPicker(categories: List<QbitCategory>, current: String, onPick: (String) -> Unit, label: String = "Catégorie qBittorrent", enabled: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { if (enabled) open = it }) {
        OutlinedTextField(
            value = current.ifBlank { "Aucune" },
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Aucune") }, onClick = { onPick(""); open = false })
            categories.forEach { c ->
                DropdownMenuItem(text = { Text(c.name) }, onClick = { onPick(c.name); open = false })
            }
        }
    }
}
