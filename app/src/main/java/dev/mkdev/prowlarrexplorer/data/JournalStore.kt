package dev.mkdev.prowlarrexplorer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.mkdev.prowlarrexplorer.domain.SentEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.journalStore: DataStore<Preferences> by preferencesDataStore(name = "journal")

/** Journal des envois (200 derniers), le plus récent en tête. */
class JournalStore(private val context: Context) {

    private val key = stringPreferencesKey("entries")
    private val json = Json { ignoreUnknownKeys = true }
    private val ser = ListSerializer(SentEntry.serializer())

    val entries: Flow<List<SentEntry>> = context.journalStore.data.map { p ->
        p[key]?.let { runCatching { json.decodeFromString(ser, it) }.getOrNull() } ?: emptyList()
    }

    suspend fun add(e: SentEntry) {
        context.journalStore.edit { p ->
            val cur = p[key]?.let { runCatching { json.decodeFromString(ser, it) }.getOrNull() } ?: emptyList()
            p[key] = json.encodeToString(ser, (listOf(e) + cur).take(200))
        }
    }

    suspend fun clear() {
        context.journalStore.edit { it.remove(key) }
    }
}
