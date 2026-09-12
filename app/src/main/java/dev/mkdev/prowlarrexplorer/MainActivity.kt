package dev.mkdev.prowlarrexplorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.mkdev.prowlarrexplorer.ui.AppTheme
import dev.mkdev.prowlarrexplorer.ui.SearchScreen
import dev.mkdev.prowlarrexplorer.ui.SearchViewModel
import dev.mkdev.prowlarrexplorer.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    private val vm: SearchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                val state by vm.state.collectAsState()
                var showSettings by remember { mutableStateOf(false) }
                val config = state.config

                // Premier lancement : pas de config → réglages directement.
                LaunchedEffect(config?.configured) {
                    if (config != null && !config.configured) showSettings = true
                }

                if (showSettings && config != null) {
                    BackHandler { showSettings = false }
                    SettingsScreen(
                        initial = config,
                        onTest = vm::testConfig,
                        onSave = vm::saveConfig,
                        onBack = { showSettings = false },
                    )
                } else {
                    SearchScreen(vm = vm, state = state, onSettings = { showSettings = true })
                }
            }
        }
    }
}
