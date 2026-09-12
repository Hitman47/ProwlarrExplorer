package dev.mkdev.prowlarrexplorer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mkdev.prowlarrexplorer.data.QbitClient
import dev.mkdev.prowlarrexplorer.data.SettingsStore
import dev.mkdev.prowlarrexplorer.data.short
import dev.mkdev.prowlarrexplorer.domain.QbitConfig
import dev.mkdev.prowlarrexplorer.domain.Torrent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class TorrentFilter(val label: String) { ALL("Tous"), ACTIVE("En cours"), DONE("Terminés") }

data class DownloadsState(
    val config: QbitConfig? = null,
    val torrents: List<Torrent> = emptyList(),
    val filter: TorrentFilter = TorrentFilter.ALL,
    val loaded: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val selected: Torrent? = null,
    val busy: Boolean = false,
    val message: String? = null,
) {
    val visible: List<Torrent>
        get() = when (filter) {
            TorrentFilter.ALL -> torrents
            TorrentFilter.ACTIVE -> torrents.filter { !it.done }
            TorrentFilter.DONE -> torrents.filter { it.done }
        }
    val totalDown: Long get() = torrents.sumOf { it.dlspeed }
    val totalUp: Long get() = torrents.sumOf { it.upspeed }
}

class DownloadsViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)
    private val client = QbitClient { _state.value.config ?: QbitConfig() }

    private val _state = MutableStateFlow(DownloadsState())
    val state: StateFlow<DownloadsState> = _state
    private var poll: Job? = null

    init {
        viewModelScope.launch {
            store.settings.collect { s ->
                val changed = _state.value.config != s.qbit
                _state.update { it.copy(config = s.qbit) }
                if (changed) _state.update { it.copy(torrents = emptyList(), loaded = false, error = null) }
            }
        }
    }

    /** Rafraîchit toutes les 3 s tant que l'écran est visible (appelé/annulé par l'UI). */
    fun startPolling() {
        if (poll?.isActive == true) return
        poll = viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(3_000)
            }
        }
    }

    fun stopPolling() { poll?.cancel(); poll = null }

    suspend fun refresh() {
        val cfg = _state.value.config ?: return
        if (!cfg.configured) return
        _state.update { it.copy(refreshing = true) }
        runCatching { client.torrents() }
            .onSuccess { list ->
                _state.update { s ->
                    s.copy(
                        torrents = list, loaded = true, refreshing = false, error = null,
                        // La fiche ouverte suit la progression.
                        selected = s.selected?.let { sel -> list.firstOrNull { it.hash == sel.hash } },
                    )
                }
            }
            .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(refreshing = false, error = e.short()) }
            }
    }

    fun setFilter(f: TorrentFilter) = _state.update { it.copy(filter = f) }
    fun select(t: Torrent?) = _state.update { it.copy(selected = t) }

    fun togglePause() = action { t -> if (t.paused) client.resume(t.hash) else client.pause(t.hash) }

    fun delete(withFiles: Boolean) = action(close = true) { t ->
        client.delete(t.hash, withFiles)
        toast(if (withFiles) "Supprimé avec ses fichiers" else "Retiré de qBittorrent")
    }

    private fun action(close: Boolean = false, block: suspend (Torrent) -> Unit) {
        val t = _state.value.selected ?: return
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching { block(t) }
                .onFailure { e -> toast(e.short()) }
            _state.update { it.copy(busy = false, selected = if (close) null else it.selected) }
            refresh()
        }
    }

    /** Test depuis l'écran de réglages, avant enregistrement. */
    suspend fun testConfig(c: QbitConfig): Result<String> = runCatching {
        val n = c.normalized()
        val v = client.version(n)
        val mode = if (n.hasCredentials) "session ouverte" else "sans authentification (bypass IP)"
        "qBittorrent $v — $mode"
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
    private fun toast(m: String) = _state.update { it.copy(message = m) }
}
