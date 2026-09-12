package dev.mkdev.prowlarrexplorer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.mkdev.prowlarrexplorer.domain.Torrent
import kotlinx.coroutines.flow.first

private val Context.trackerStore: DataStore<Preferences> by preferencesDataStore(name = "completions")

/**
 * Mémorise les torrents déjà vus terminés pour ne notifier qu'une fois chaque fin de téléchargement.
 * Le premier relevé sert de référence (rien n'est notifié) : les torrents déjà finis avant
 * l'installation ne déclenchent rien.
 */
class CompletionTracker(private val context: Context) {

    private val knownKey = stringSetPreferencesKey("known_done")
    private val baselineKey = booleanPreferencesKey("baseline_set")

    /** Enregistre l'état courant et retourne les torrents nouvellement terminés. */
    suspend fun record(torrents: List<Torrent>): List<Torrent> {
        val doneNow = torrents.filter { it.done }
        val p = context.trackerStore.data.first()
        val known = p[knownKey] ?: emptySet()
        val baseline = p[baselineKey] ?: false
        val fresh = if (baseline) doneNow.filter { it.hash !in known } else emptyList()
        val next = doneNow.map { t -> t.hash }.toSet()
        // Appelé toutes les 3 s quand l'écran est ouvert : n'écrit que si l'ensemble change.
        if (!baseline || next != known) {
            // Les hash disparus de qBittorrent sont oubliés : liste bornée.
            context.trackerStore.edit {
                it[knownKey] = next
                it[baselineKey] = true
            }
        }
        return fresh
    }
}
