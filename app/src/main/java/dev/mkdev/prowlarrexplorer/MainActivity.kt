package dev.mkdev.prowlarrexplorer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.mkdev.prowlarrexplorer.data.short
import dev.mkdev.prowlarrexplorer.domain.AppSettings
import dev.mkdev.prowlarrexplorer.domain.humanSpeed
import dev.mkdev.prowlarrexplorer.ui.AppTheme
import dev.mkdev.prowlarrexplorer.ui.DownloadsScreen
import dev.mkdev.prowlarrexplorer.ui.DownloadsViewModel
import dev.mkdev.prowlarrexplorer.ui.Probe
import dev.mkdev.prowlarrexplorer.ui.ProwlarrSettings
import dev.mkdev.prowlarrexplorer.ui.QbitSettings
import dev.mkdev.prowlarrexplorer.ui.SearchScreen
import dev.mkdev.prowlarrexplorer.ui.SearchViewModel
import dev.mkdev.prowlarrexplorer.ui.SettingsHome
import dev.mkdev.prowlarrexplorer.ui.SettingsPage
import dev.mkdev.prowlarrexplorer.ui.UpdateBanner
import dev.mkdev.prowlarrexplorer.ui.UpdateViewModel

class MainActivity : ComponentActivity() {

    private val searchVm: SearchViewModel by viewModels()
    private val downloadsVm: DownloadsViewModel by viewModels()
    private val updateVm: UpdateViewModel by viewModels()

    /** Texte reçu (sélection ou partage) en attente de recherche. */
    private val pendingQuery = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val theme by searchVm.theme.collectAsState()
            AppTheme(theme) { Root() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }?.trim()?.takeIf { it.isNotEmpty() } ?: return
        pendingQuery.value = text
    }

    @Composable
    private fun Root() {
        val search by searchVm.state.collectAsState()
        val downloads by downloadsVm.state.collectAsState()
        val update by updateVm.state.collectAsState()
        val theme by searchVm.theme.collectAsState()
        var settingsPage by remember { mutableStateOf<SettingsPage?>(null) }
        // 0 = pas encore décidé, 1 = Prowlarr, 2 = qBittorrent, 3 = terminé.
        var onboardingStep by rememberSaveable { mutableStateOf(0) }
        var tab by rememberSaveable { mutableStateOf(0) }
        val prowlarr = search.config
        val qbit = downloads.config

        // Tablette en paysage (≥ 840 dp) : rail à gauche + fiche dans un volet droit.
        val expanded = LocalConfiguration.current.screenWidthDp >= 840

        // Retour au premier plan : nouvelle release ?
        val owner = LocalLifecycleOwner.current
        DisposableEffect(owner) {
            val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) updateVm.checkIfDue() }
            owner.lifecycle.addObserver(obs)
            onDispose { owner.lifecycle.removeObserver(obs) }
        }

        // Texte reçu d'une autre app : recherche immédiate.
        val pending by pendingQuery
        LaunchedEffect(pending, prowlarr?.configured) {
            val q = pending ?: return@LaunchedEffect
            if (prowlarr?.configured == true) {
                pendingQuery.value = null
                settingsPage = null
                tab = 0
                searchVm.searchFor(q)
            }
        }

        // Premier lancement : assistant en deux étapes.
        LaunchedEffect(prowlarr?.configured) {
            if (prowlarr != null && onboardingStep == 0) onboardingStep = if (prowlarr.configured) 3 else 1
        }

        if (prowlarr == null || qbit == null || onboardingStep == 0) return

        // État des services pour l'accueil des réglages, testé à l'ouverture.
        var prowlarrProbe by remember { mutableStateOf<Probe?>(null) }
        var qbitProbe by remember { mutableStateOf<Probe?>(null) }
        LaunchedEffect(settingsPage == SettingsPage.HOME, prowlarr, qbit) {
            if (settingsPage != SettingsPage.HOME) return@LaunchedEffect
            if (prowlarr.configured) {
                prowlarrProbe = Probe.Testing
                prowlarrProbe = searchVm.testConfig(prowlarr).fold({ Probe.Ok(it) }, { Probe.Failed(it.short()) })
            }
            if (qbit.configured) {
                qbitProbe = Probe.Testing
                qbitProbe = downloadsVm.testConfig(qbit).fold({ Probe.Ok(it) }, { Probe.Failed(it.short()) })
            }
        }

        val banner: @Composable () -> Unit = {
            AnimatedVisibility(update.available != null && !update.dismissed, enter = expandVertically(), exit = shrinkVertically()) {
                UpdateBanner(update, onInstall = updateVm::downloadAndInstall, onDismiss = updateVm::dismiss)
            }
        }

        when {
            // Assistant : la carte de mise à jour reste accessible même sans configuration.
            onboardingStep == 1 -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    ProwlarrSettings(
                        initial = prowlarr, onboarding = true, onTest = searchVm::testConfig,
                        onSave = { searchVm.saveSettings(AppSettings(it, qbit)); onboardingStep = 2 },
                        onBack = { if (prowlarr.configured) onboardingStep = 2 },
                    )
                }
                banner()
            }
            onboardingStep == 2 -> Column(Modifier.fillMaxSize()) {
                BackHandler { onboardingStep = 3 }
                Box(Modifier.weight(1f)) {
                    QbitSettings(
                        initial = qbit, onboarding = true, onTest = downloadsVm::testConfig,
                        onSave = { searchVm.saveSettings(AppSettings(prowlarr, it)) },
                        onBack = { onboardingStep = 3 },
                    )
                }
                banner()
            }
            settingsPage == SettingsPage.PROWLARR -> {
                BackHandler { settingsPage = SettingsPage.HOME }
                ProwlarrSettings(
                    initial = prowlarr, onboarding = false, onTest = searchVm::testConfig,
                    onSave = { searchVm.saveSettings(AppSettings(it, qbit)) },
                    onBack = { settingsPage = SettingsPage.HOME },
                )
            }
            settingsPage == SettingsPage.QBIT -> {
                BackHandler { settingsPage = SettingsPage.HOME }
                QbitSettings(
                    initial = qbit, onboarding = false, onTest = downloadsVm::testConfig,
                    onSave = { searchVm.saveSettings(AppSettings(prowlarr, it)) },
                    onBack = { settingsPage = SettingsPage.HOME },
                )
            }
            settingsPage == SettingsPage.HOME -> {
                BackHandler { settingsPage = null }
                SettingsHome(
                    prowlarr = prowlarr, qbit = qbit, prowlarrProbe = prowlarrProbe, qbitProbe = qbitProbe,
                    theme = theme, onTheme = searchVm::setTheme,
                    onOpen = { settingsPage = it },
                    update = update,
                    onCheckUpdate = { updateVm.check() },
                    onInstallUpdate = updateVm::downloadAndInstall,
                    onBack = { settingsPage = null },
                )
            }
            else -> {
                val openSettings = { settingsPage = SettingsPage.HOME }
                val content: @Composable () -> Unit = {
                    when (tab) {
                        0 -> SearchScreen(vm = searchVm, state = search, twoPane = expanded, onSettings = openSettings, onShowDownloads = { tab = 1 })
                        else -> DownloadsScreen(vm = downloadsVm, state = downloads, twoPane = expanded, onSettings = openSettings)
                    }
                }
                val downloadsLabel = downloads.totalDown.humanSpeed().ifEmpty { "Téléchargements" }
                val badge: @Composable () -> Unit = {
                    BadgedBox(badge = { if (downloads.activeCount > 0) Badge { Text(downloads.activeCount.toString()) } }) {
                        Icon(Icons.Default.Download, contentDescription = null)
                    }
                }

                if (expanded) Row(Modifier.fillMaxSize()) {
                    NavigationRail {
                        NavigationRailItem(selected = tab == 0, onClick = { tab = 0 },
                            icon = { Icon(Icons.Default.Search, contentDescription = null) }, label = { Text("Recherche") })
                        NavigationRailItem(selected = tab == 1, onClick = { tab = 1 }, icon = badge, label = { Text(downloadsLabel) })
                    }
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.weight(1f)) { content() }
                        banner()
                    }
                } else Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) { content() }
                    // Sous le contenu : pas de conflit avec l'inset de la barre d'état.
                    banner()
                    NavigationBar {
                        NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                            icon = { Icon(Icons.Default.Search, contentDescription = null) }, label = { Text("Recherche") })
                        NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = badge, label = { Text(downloadsLabel) })
                    }
                }
            }
        }
    }
}
