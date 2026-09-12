package dev.mkdev.prowlarrexplorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.mkdev.prowlarrexplorer.domain.AppSettings
import dev.mkdev.prowlarrexplorer.ui.AppTheme
import dev.mkdev.prowlarrexplorer.ui.DownloadsScreen
import dev.mkdev.prowlarrexplorer.ui.DownloadsViewModel
import dev.mkdev.prowlarrexplorer.ui.SearchScreen
import dev.mkdev.prowlarrexplorer.ui.SearchViewModel
import dev.mkdev.prowlarrexplorer.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    private val searchVm: SearchViewModel by viewModels()
    private val downloadsVm: DownloadsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                val search by searchVm.state.collectAsState()
                val downloads by downloadsVm.state.collectAsState()
                var showSettings by remember { mutableStateOf(false) }
                var tab by rememberSaveable { mutableStateOf(0) }
                val prowlarr = search.config
                val qbit = downloads.config

                // Premier lancement : pas de config → réglages directement.
                LaunchedEffect(prowlarr?.configured) {
                    if (prowlarr != null && !prowlarr.configured) showSettings = true
                }

                if (showSettings && prowlarr != null && qbit != null) {
                    BackHandler { showSettings = false }
                    SettingsScreen(
                        initial = AppSettings(prowlarr, qbit),
                        onTestProwlarr = searchVm::testConfig,
                        onTestQbit = downloadsVm::testConfig,
                        onSave = searchVm::saveSettings,
                        onBack = { showSettings = false },
                    )
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) {
                            when (tab) {
                                0 -> SearchScreen(vm = searchVm, state = search, onSettings = { showSettings = true })
                                else -> DownloadsScreen(vm = downloadsVm, state = downloads, onSettings = { showSettings = true })
                            }
                        }
                        NavigationBar {
                            NavigationBarItem(
                                selected = tab == 0, onClick = { tab = 0 },
                                icon = { Icon(Icons.Default.Search, contentDescription = null) }, label = { Text("Recherche") },
                            )
                            NavigationBarItem(
                                selected = tab == 1, onClick = { tab = 1 },
                                icon = { Icon(Icons.Default.Download, contentDescription = null) }, label = { Text("Téléchargements") },
                            )
                        }
                    }
                }
            }
        }
    }
}
