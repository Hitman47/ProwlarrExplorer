package dev.mkdev.prowlarrexplorer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mkdev.prowlarrexplorer.data.CompletionTracker
import dev.mkdev.prowlarrexplorer.data.QbitClient
import dev.mkdev.prowlarrexplorer.data.SettingsStore
import dev.mkdev.prowlarrexplorer.data.short
import dev.mkdev.prowlarrexplorer.domain.QbitCategory
import dev.mkdev.prowlarrexplorer.domain.QbitConfig
import dev.mkdev.prowlarrexplorer.domain.Torrent
import dev.mkdev.prowlarrexplorer.domain.TorrentFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import dev.mkdev.prowlarrexplorer.work.DownloadWatcher
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Section(val label: String) { ACTIVE("Actifs"), PAUSED("En pause"), DONE("Terminés") }

fun Torrent.section(): Section = when {
    !done && paused -> Section.PAUSED
    !done -> Section.ACTIVE
    else -> Section.DONE
}

data class DownloadsState(
    val config: QbitConfig? = null,
    val torrents: List<Torrent> = emptyList(),
    val loaded: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val selected: Torrent? = null,
    /** hash du torrent sur lequel une action est en cours. */
    val busy: String? = null,
    val message: String? = null,
    val categories: List<QbitCategory> = emptyList(),
    /** Fichiers du torrent sélectionné (null = pas chargés). */
    val files: List<TorrentFile>? = null,
    val filesFor: String? = null,
) {
    val sections: List<Pair<Section, List<Torrent>>>
        get() = Section.entries.mapNotNull { s -> torrents.filter { it.section() == s }.takeIf { it.isNotEmpty() }?.let { s to it } }
    val activeCount: Int get() = torrents.count { it.section() == Section.ACTIVE }
    val totalDown: Long get() = torrents.sumOf { it.dlspeed }
    val totalUp: Long get() = torrents.sumOf { it.upspeed }
}

class DownloadsViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)
    private val client = QbitClient { _state.value.config ?: QbitConfig() }

    private val tracker = CompletionTracker(app)

    private val _state = MutableStateFlow(DownloadsState())
    val state: StateFlow<DownloadsState> = _state
    private var poll: Job? = null

    val notifyDone: StateFlow<Boolean> = store.notifyDone.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setNotifyDone(on: Boolean) = viewModelScope.launch {
        store.setNotifyDone(on)
        if (on) DownloadWatcher.schedule(getApplication()) else DownloadWatcher.cancel(getApplication())
    }

    init {
        viewModelScope.launch {
            store.settings.collect { s ->
                val changed = _state.value.config != s.qbit
                _state.update { it.copy(config = s.qbit) }
                if (changed) _state.update { it.copy(torrents = emptyList(), loaded = false, error = null, categories = emptyList()) }
                if (changed && s.qbit.configured) launch {
                    val cats = runCatching { client.categories() }.getOrDefault(emptyList())
                    _state.update { it.copy(categories = cats) }
                }
                if (s.qbit.configured && store.notifyDone.first()) DownloadWatcher.schedule(getApplication())
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
                // Vu à l'écran : enregistré comme connu, pas de notification en arrière-plan ensuite.
                tracker.record(list).takeIf { it.isNotEmpty() }?.let { done ->
                    toast(if (done.size == 1) "Terminé : ${done.first().name.take(50)}" else "${done.size} téléchargements terminés")
                }
            }
            .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(refreshing = false, error = e.short()) }
            }
    }

    fun refreshNow() = viewModelScope.launch { refresh() }

    fun select(t: Torrent?) {
        _state.update { it.copy(selected = t, files = null, filesFor = null) }
    }

    fun loadFiles() {
        val t = _state.value.selected ?: return
        if (_state.value.filesFor == t.hash) return
        _state.update { it.copy(filesFor = t.hash, files = null) }
        viewModelScope.launch {
            runCatching { client.files(t.hash) }
                .onSuccess { list -> _state.update { if (it.selected?.hash == t.hash) it.copy(files = list) else it } }
                .onFailure { e -> _state.update { it.copy(filesFor = null) }; toast("Fichiers : ${e.short()}") }
        }
    }

    fun toggleFile(f: TorrentFile) {
        val t = _state.value.selected ?: return
        val next = if (f.wanted) 0 else 1
        // Optimiste : la case bascule tout de suite, l'API confirme.
        _state.update { s -> s.copy(files = s.files?.map { if (it.index == f.index) it.copy(priority = next) else it }) }
        viewModelScope.launch {
            runCatching { client.setFilePriority(t.hash, f.index, next) }
                .onFailure { e -> toast("Fichier : ${e.short()}"); loadFilesForce() }
        }
    }

    private fun loadFilesForce() { _state.update { it.copy(filesFor = null) }; loadFiles() }

    fun setCategory(name: String) = action(_state.value.selected) { client.setCategory(it.hash, name) }

    fun recheck() = action(_state.value.selected) { client.recheck(it.hash); toast("Revérification lancée") }

    /** Limites en Ko/s (0 = illimité). */
    fun setLimits(downKb: Long, upKb: Long) = action(_state.value.selected) {
        client.setLimits(it.hash, downKb * 1024, upKb * 1024)
        toast("Limites appliquées")
    }

    fun togglePause(t: Torrent? = _state.value.selected) = action(t) {
        if (it.paused) client.resume(it.hash) else client.pause(it.hash)
    }

    fun delete(withFiles: Boolean) = action(_state.value.selected, close = true) { t ->
        client.delete(t.hash, withFiles)
        toast(if (withFiles) "Supprimé avec ses fichiers" else "Retiré de qBittorrent")
    }

    private fun action(t: Torrent?, close: Boolean = false, block: suspend (Torrent) -> Unit) {
        t ?: return
        if (_state.value.busy != null) return
        viewModelScope.launch {
            _state.update { it.copy(busy = t.hash) }
            runCatching { block(t) }.onFailure { e -> toast(e.short()) }
            _state.update { it.copy(busy = null, selected = if (close) null else it.selected) }
            refresh()
        }
    }

    /** Test depuis l'écran de réglages, avant enregistrement. */
    suspend fun testConfig(c: QbitConfig): Result<String> = runCatching {
        val n = c.normalized()
        val v = client.version(n)
        val mode = when {
            n.hasApiKey -> "clé API"
            n.hasCredentials -> "session ouverte"
            else -> "sans authentification (bypass IP)"
        }
        "qBittorrent $v — $mode"
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
    private fun toast(m: String) = _state.update { it.copy(message = m) }
}
