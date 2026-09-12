package dev.mkdev.prowlarrexplorer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mkdev.prowlarrexplorer.data.SettingsStore
import dev.mkdev.prowlarrexplorer.data.UpdateChecker
import dev.mkdev.prowlarrexplorer.data.short
import dev.mkdev.prowlarrexplorer.domain.UpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UpdateState(
    val checking: Boolean = false,
    val available: UpdateInfo? = null,
    /** null = pas de téléchargement en cours ; 0..1 sinon. */
    val progress: Float? = null,
    val error: String? = null,
    val dismissed: Boolean = false,
)

class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)
    private val checker = UpdateChecker(app)

    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state

    /** À chaque retour au premier plan ; silencieux, au plus une fois toutes les 5 min. */
    fun checkIfDue() {
        viewModelScope.launch {
            if (_state.value.checking || _state.value.progress != null) return@launch
            val last = store.lastUpdateCheck.first()
            if (System.currentTimeMillis() - last > 5 * 60_000L) check(silent = true)
        }
    }

    /** Retourne un message pour l'écran de réglages ; en silencieux, n'affiche que la bannière. */
    suspend fun check(silent: Boolean = false): Result<String> {
        _state.update { it.copy(checking = true, error = null) }
        val r = runCatching { checker.latest() }
        store.markUpdateCheck()
        _state.update { s ->
            s.copy(
                checking = false,
                available = r.getOrNull() ?: s.available.takeIf { r.isFailure },
                dismissed = if (r.getOrNull() != null) false else s.dismissed,
                error = if (silent) null else r.exceptionOrNull()?.short(),
            )
        }
        return r.map { info -> if (info == null) "Déjà à jour" else "Version ${info.version} disponible" }
    }

    fun downloadAndInstall() {
        val info = _state.value.available ?: return
        if (_state.value.progress != null) return
        viewModelScope.launch {
            _state.update { it.copy(progress = 0f, error = null) }
            runCatching { checker.download(info) { p -> _state.update { it.copy(progress = p) } } }
                .onSuccess { file -> _state.update { it.copy(progress = null) }; checker.install(file) }
                .onFailure { e -> _state.update { it.copy(progress = null, error = e.short()) } }
        }
    }

    fun dismiss() = _state.update { it.copy(dismissed = true) }
}
