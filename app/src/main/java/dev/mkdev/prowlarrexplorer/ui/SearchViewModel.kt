package dev.mkdev.prowlarrexplorer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mkdev.prowlarrexplorer.data.ProwlarrClient
import dev.mkdev.prowlarrexplorer.data.SettingsStore
import dev.mkdev.prowlarrexplorer.data.short
import dev.mkdev.prowlarrexplorer.domain.AppSettings
import dev.mkdev.prowlarrexplorer.domain.CategoryFilter
import dev.mkdev.prowlarrexplorer.domain.Indexer
import dev.mkdev.prowlarrexplorer.domain.ProwlarrConfig
import dev.mkdev.prowlarrexplorer.domain.Release
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val config: ProwlarrConfig? = null,
    val githubToken: String = "",
    val indexers: List<Indexer> = emptyList(),
    /** Ids des indexers cochés ; null = tous les indexers activés. */
    val selectedIndexers: Set<Int>? = null,
    val category: CategoryFilter = CategoryFilter.ALL,
    val query: String = "",
    val results: List<Release> = emptyList(),
    val searched: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val selected: Release? = null,
    val grabbing: Boolean = false,
    val message: String? = null,
) {
    val enabledIndexers: List<Indexer> get() = indexers.filter { it.enable }
    val indexerLabel: String
        get() = when (val s = selectedIndexers) {
            null -> "Tous (${enabledIndexers.size})"
            else -> "${s.size} / ${enabledIndexers.size}"
        }
}

class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)
    private val client = ProwlarrClient { _state.value.config ?: ProwlarrConfig() }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            store.settings.collect { s ->
                val first = _state.value.config == null
                _state.update { it.copy(config = s.prowlarr, githubToken = s.githubToken) }
                if (first && s.prowlarr.configured) loadIndexers()
            }
        }
    }

    fun loadIndexers() {
        viewModelScope.launch {
            runCatching { client.indexers() }
                .onSuccess { list -> _state.update { it.copy(indexers = list.sortedBy { i -> i.name.lowercase() }, error = null) } }
                .onFailure { e -> _state.update { it.copy(error = "Indexers : ${e.short()}") } }
        }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    fun setCategory(c: CategoryFilter) {
        _state.update { it.copy(category = c) }
        if (_state.value.searched) search()
    }

    fun toggleIndexer(id: Int) = _state.update { s ->
        val all = s.enabledIndexers.map { it.id }.toSet()
        val cur = s.selectedIndexers ?: all
        val next = if (id in cur) cur - id else cur + id
        s.copy(selectedIndexers = if (next == all) null else next)
    }

    fun selectAllIndexers(all: Boolean) = _state.update { it.copy(selectedIndexers = if (all) null else emptySet()) }

    fun search() {
        val s = _state.value
        val q = s.query.trim()
        if (q.isEmpty() || s.config?.configured != true) return
        val ids = s.selectedIndexers?.toList() ?: emptyList()
        if (s.selectedIndexers?.isEmpty() == true) { toast("Aucun indexer sélectionné"); return }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, searched = true) }
            runCatching { client.search(q, s.category.ids, ids) }
                .onSuccess { list ->
                    val sorted = list.sortedWith(compareByDescending<Release> { it.seeders ?: -1 }.thenByDescending { it.size })
                    _state.update { it.copy(results = sorted, loading = false) }
                }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) return@onFailure
                    _state.update { it.copy(results = emptyList(), loading = false, error = "Recherche : ${e.short()}") }
                }
        }
    }

    fun select(r: Release?) = _state.update { it.copy(selected = r) }

    fun grab() {
        val r = _state.value.selected ?: return
        if (_state.value.grabbing) return
        viewModelScope.launch {
            _state.update { it.copy(grabbing = true) }
            runCatching { client.grab(r) }
                .onSuccess { _state.update { it.copy(grabbing = false, selected = null) }; toast("Envoyé : ${r.title.take(60)}") }
                .onFailure { e -> _state.update { it.copy(grabbing = false) }; toast("Envoi : ${e.short()}") }
        }
    }

    fun saveSettings(s: AppSettings) {
        viewModelScope.launch {
            store.save(s)
            _state.update { it.copy(config = store.settings.first().prowlarr, indexers = emptyList(), selectedIndexers = null, results = emptyList(), searched = false) }
            loadIndexers()
        }
    }

    /** Test depuis l'écran de réglages, avant enregistrement. */
    suspend fun testConfig(c: ProwlarrConfig): Result<String> = runCatching {
        val n = c.normalized()
        val st = client.status(n)
        val clients = client.downloadClients(n).filter { it.enable }
        val tail = if (clients.isEmpty()) "aucun client de téléchargement activé dans Prowlarr"
        else "clients : ${clients.joinToString { it.name }}"
        "${st.appName} ${st.version} — $tail"
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
    private fun toast(m: String) = _state.update { it.copy(message = m) }
}
