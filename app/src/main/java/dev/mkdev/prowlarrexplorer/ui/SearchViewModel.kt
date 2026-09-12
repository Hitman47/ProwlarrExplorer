package dev.mkdev.prowlarrexplorer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mkdev.prowlarrexplorer.data.JournalStore
import dev.mkdev.prowlarrexplorer.data.ProwlarrClient
import dev.mkdev.prowlarrexplorer.data.QbitClient
import dev.mkdev.prowlarrexplorer.data.SettingsStore
import dev.mkdev.prowlarrexplorer.data.short
import dev.mkdev.prowlarrexplorer.domain.AppSettings
import dev.mkdev.prowlarrexplorer.domain.CategoryFilter
import dev.mkdev.prowlarrexplorer.domain.Indexer
import dev.mkdev.prowlarrexplorer.domain.ProwlarrConfig
import dev.mkdev.prowlarrexplorer.domain.QbitCategory
import dev.mkdev.prowlarrexplorer.domain.QbitConfig
import dev.mkdev.prowlarrexplorer.domain.Release
import dev.mkdev.prowlarrexplorer.domain.SentEntry
import dev.mkdev.prowlarrexplorer.domain.SortMode
import dev.mkdev.prowlarrexplorer.domain.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Message pour la snackbar ; `goDownloads` ajoute l'action « Voir » vers l'onglet Téléchargements. */
data class Msg(val text: String, val goDownloads: Boolean = false)

data class UiState(
    val config: ProwlarrConfig? = null,
    val qbit: QbitConfig? = null,
    /** Catégories qBittorrent (vide si non configuré / injoignable). */
    val categories: List<QbitCategory> = emptyList(),
    /** Catégorie qBittorrent pour le prochain envoi ; vide = aucune. */
    val qbCategory: String = "",
    val indexers: List<Indexer> = emptyList(),
    /** Ids des indexers cochés ; null = tous les indexers activés. */
    val selectedIndexers: Set<Int>? = null,
    val category: CategoryFilter = CategoryFilter.ALL,
    val query: String = "",
    val history: List<String> = emptyList(),
    val sort: SortMode = SortMode.SEEDERS,
    val hideDead: Boolean = true,
    val rawResults: List<Release> = emptyList(),
    val searched: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val selected: Release? = null,
    /** guid de la release en cours d'envoi. */
    val grabbing: String? = null,
    /** Page ouverte dans le navigateur intégré. */
    val web: WebTarget? = null,
    /** Dialogue d'ajout manuel : lien pré-rempli (null = fermé). */
    val manualAdd: String? = null,
    val adding: Boolean = false,
    val message: Msg? = null,
) {
    val enabledIndexers: List<Indexer> get() = indexers.filter { it.enable }
    /** Envoi direct à qBittorrent possible pour cette release (sinon grab Prowlarr). */
    fun direct(r: Release): Boolean = qbit?.configured == true && r.protocol != "usenet" && (r.magnetUrl != null || r.downloadUrl != null)
    val indexerLabel: String
        get() = when (val s = selectedIndexers) {
            null -> "Tous (${enabledIndexers.size})"
            else -> "${s.size} / ${enabledIndexers.size}"
        }

    private val sorted: List<Release>
        get() {
            val kept = if (hideDead) rawResults.filter { it.protocol == "usenet" || (it.seeders ?: 0) > 0 } else rawResults
            return when (sort) {
                SortMode.SEEDERS -> kept.sortedWith(compareByDescending<Release> { it.seeders ?: -1 }.thenByDescending { it.size })
                SortMode.SIZE -> kept.sortedByDescending { it.size }
                SortMode.DATE -> kept.sortedBy { it.ageHours }
            }
        }

    /** Même release sur plusieurs indexers : regroupée, la mieux classée (selon le tri) en tête. */
    private val groups: Map<String, List<Release>> get() = sorted.groupBy { it.dedupKey }
    val results: List<Release> get() = groups.values.map { it.first() }
    fun alternates(r: Release): List<Release> = groups[r.dedupKey]?.filter { it.guid != r.guid || it.indexerId != r.indexerId } ?: emptyList()
    val hiddenCount: Int get() = rawResults.size - sorted.size
    val mergedCount: Int get() = sorted.size - results.size
}

class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)
    private val client = ProwlarrClient { _state.value.config ?: ProwlarrConfig() }
    private val qbit = QbitClient { _state.value.qbit ?: QbitConfig() }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state
    private var searchJob: Job? = null

    private val journalStore = JournalStore(app)
    val journal: StateFlow<List<SentEntry>> = journalStore.entries.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun clearJournal() = viewModelScope.launch { journalStore.clear() }

    val theme: StateFlow<ThemeMode> = store.theme.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
    fun setTheme(m: ThemeMode) = viewModelScope.launch { store.setTheme(m) }

    init {
        viewModelScope.launch {
            store.settings.collect { s ->
                val first = _state.value.config == null
                val qbitChanged = _state.value.qbit != s.qbit
                _state.update { it.copy(config = s.prowlarr, qbit = s.qbit) }
                if (first && s.prowlarr.configured) loadIndexers()
                if (qbitChanged) loadCategories()
            }
        }
        viewModelScope.launch { store.history.collect { h -> _state.update { it.copy(history = h) } } }
        viewModelScope.launch { store.qbCategory.collect { c -> _state.update { it.copy(qbCategory = c) } } }
    }

    fun loadIndexers() {
        viewModelScope.launch {
            runCatching { client.indexers() }
                .onSuccess { list -> _state.update { it.copy(indexers = list.sortedBy { i -> i.name.lowercase() }, error = null) } }
                .onFailure { e -> _state.update { it.copy(error = "Indexers : ${e.short()}") } }
        }
    }

    fun loadCategories() {
        val cfg = _state.value.qbit ?: return
        if (!cfg.configured) { _state.update { it.copy(categories = emptyList()) }; return }
        viewModelScope.launch {
            val list = runCatching { qbit.categories() }.getOrDefault(emptyList())
            _state.update { it.copy(categories = list) }
        }
    }

    fun setQbCategory(name: String) = viewModelScope.launch { store.setQbCategory(name) }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun setSort(s: SortMode) = _state.update { it.copy(sort = s) }
    fun toggleHideDead() = _state.update { it.copy(hideDead = !it.hideDead) }

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

    /** Recherche depuis l'extérieur (texte partagé / sélectionné) : remplace la requête. */
    fun searchFor(text: String) {
        _state.update { it.copy(query = text.trim().take(200)) }
        search()
    }

    fun search() {
        val s = _state.value
        val q = s.query.trim()
        if (q.isEmpty() || s.config?.configured != true) return
        val ids = s.selectedIndexers?.toList() ?: emptyList()
        if (s.selectedIndexers?.isEmpty() == true) { toast("Aucun indexer sélectionné"); return }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, searched = true, selected = null) }
            store.addHistory(q)
            runCatching { client.search(q, s.category.ids, ids) }
                .onSuccess { list -> _state.update { it.copy(rawResults = list, loading = false) } }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) return@onFailure
                    _state.update { it.copy(rawResults = emptyList(), loading = false, error = "Recherche : ${e.short()}") }
                }
        }
    }

    fun clearHistory() = viewModelScope.launch { store.clearHistory() }

    fun select(r: Release?) = _state.update { it.copy(selected = r) }

    /** Page de la release chez l'indexer, avec le cookie de session Prowlarr si l'indexer en a un. */
    fun openInfo(r: Release) {
        val url = r.infoUrl ?: return
        viewModelScope.launch {
            val cookie = kotlinx.coroutines.withTimeoutOrNull(4_000) { runCatching { client.indexerCookie(r.indexerId) }.getOrNull() }
            _state.update { it.copy(web = WebTarget(url, r.indexer.ifBlank { "Indexer" }, cookie)) }
        }
    }

    fun closeWeb() = _state.update { it.copy(web = null) }

    fun openManualAdd(prefill: String = "") = _state.update { it.copy(manualAdd = prefill) }
    fun closeManualAdd() = _state.update { it.copy(manualAdd = null) }

    /** Lien magnet ou URL de .torrent saisi / partagé → qBittorrent avec la catégorie courante. */
    fun addManual(link: String) {
        val s = _state.value
        val l = link.trim()
        if (l.isEmpty() || s.adding) return
        if (s.qbit?.configured != true) { toast("qBittorrent non configuré"); return }
        if (!isTorrentLink(l)) { toast("Lien non reconnu (magnet: ou http…)"); return }
        viewModelScope.launch {
            _state.update { it.copy(adding = true) }
            runCatching { qbit.addUrl(l, s.qbCategory) }
                .onSuccess {
                    val dn = Regex("[?&]dn=([^&]+)").find(l)?.groupValues?.get(1)?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                    journalStore.add(SentEntry(System.currentTimeMillis(), title = dn ?: l.take(120), indexer = "manuel",
                        category = s.qbCategory, target = "qBittorrent", infoHash = SentEntry.hashOf(l)))
                    _state.update { it.copy(adding = false, manualAdd = null, message = Msg("Envoyé à qBittorrent", goDownloads = true)) }
                }
                .onFailure { e -> _state.update { it.copy(adding = false) }; toast("Envoi : ${e.short()}") }
        }
    }

    companion object {
        fun isTorrentLink(text: String): Boolean {
            val t = text.trim()
            return t.startsWith("magnet:?", ignoreCase = true) ||
                (t.startsWith("http", ignoreCase = true) && !t.contains(' ') && (t.contains(".torrent", ignoreCase = true) || t.contains("download", ignoreCase = true)))
        }
    }

    /** Lien magnet / .torrent cliqué dans le navigateur intégré → qBittorrent (sinon navigateur externe). */
    fun addFromWeb(url: String, cookie: String?) {
        val s = _state.value
        if (s.qbit?.configured != true) {
            toast("qBittorrent non configuré : lien ignoré")
            return
        }
        viewModelScope.launch {
            runCatching { qbit.addUrl(url, s.qbCategory, cookie) }
                .onSuccess {
                    journalStore.add(SentEntry(System.currentTimeMillis(), title = url.substringAfterLast('/').take(120), indexer = _state.value.web?.title ?: "",
                        category = s.qbCategory, target = "qBittorrent", infoHash = SentEntry.hashOf(url)))
                    _state.update { it.copy(message = Msg("Envoyé à qBittorrent", goDownloads = true)) }
                }
                .onFailure { e -> toast("Envoi : ${e.short()}") }
        }
    }

    /**
     * Un seul bouton : qBittorrent en direct (avec catégorie) quand il est configuré et que la release
     * est un torrent ; sinon grab Prowlarr (usenet, ou qBittorrent non configuré).
     */
    fun grab(r: Release? = _state.value.selected) {
        r ?: return
        val s = _state.value
        if (s.grabbing != null) return
        viewModelScope.launch {
            _state.update { it.copy(grabbing = r.guid) }
            runCatching { if (s.direct(r)) sendToQbit(r, s.qbCategory) else client.grab(r) }
                .onSuccess {
                    journalStore.add(SentEntry(
                        time = System.currentTimeMillis(), title = r.title, indexer = r.indexer,
                        category = if (s.direct(r)) s.qbCategory else "", target = if (s.direct(r)) "qBittorrent" else "Prowlarr",
                        infoHash = r.infoHash?.lowercase() ?: r.magnetUrl?.let { SentEntry.hashOf(it) }, size = r.size,
                    ))
                    val where = if (s.direct(r)) "qBittorrent" + (s.qbCategory.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") else "Prowlarr"
                    _state.update { it.copy(grabbing = null, selected = if (it.selected?.guid == r.guid) null else it.selected) }
                    _state.update { it.copy(message = Msg("Envoyé à $where : ${r.title.take(40)}", goDownloads = s.direct(r))) }
                }
                .onFailure { e -> _state.update { it.copy(grabbing = null) }; toast("Envoi : ${e.short()}") }
        }
    }

    private suspend fun sendToQbit(r: Release, category: String) {
        val magnet = r.magnetUrl
        if (magnet != null) { qbit.addUrl(magnet, category); return }
        // Le .torrent passe par Prowlarr (joignable depuis l'app), puis est poussé en multipart :
        // qBittorrent n'a pas besoin de joindre Prowlarr lui-même.
        when (val src = client.fetchTorrent(r.downloadUrl!!, r.title)) {
            is ProwlarrClient.TorrentSource.Magnet -> qbit.addUrl(src.url, category)
            is ProwlarrClient.TorrentSource.File -> qbit.addTorrentFile(src.bytes, src.name, category)
        }
    }

    fun saveSettings(s: AppSettings) {
        viewModelScope.launch {
            val before = _state.value.config
            store.save(s)
            val after = store.settings.first().prowlarr
            if (after != before) {
                _state.update { it.copy(config = after, indexers = emptyList(), selectedIndexers = null, rawResults = emptyList(), searched = false) }
                loadIndexers()
            }
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
    private fun toast(m: String) = _state.update { it.copy(message = Msg(m)) }
}
