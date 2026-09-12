package dev.mkdev.prowlarrexplorer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.mkdev.prowlarrexplorer.domain.AppSettings
import dev.mkdev.prowlarrexplorer.domain.ProwlarrConfig
import dev.mkdev.prowlarrexplorer.domain.QbitConfig
import dev.mkdev.prowlarrexplorer.domain.ThemeMode
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** URLs et login en clair, secrets (clé API, mot de passe) chiffrés par le Keystore, en DataStore. */
class SettingsStore(private val context: Context) {

    private val urlKey = stringPreferencesKey("url")
    private val apiKeyKey = stringPreferencesKey("api_key_enc")
    private val qbUrlKey = stringPreferencesKey("qb_url")
    private val qbUserKey = stringPreferencesKey("qb_user")
    private val qbPassKey = stringPreferencesKey("qb_pass_enc")
    private val qbApiKeyKey = stringPreferencesKey("qb_api_key_enc")
    private val updateCheckKey = longPreferencesKey("update_last_check")
    private val historyKey = stringPreferencesKey("history")
    private val themeKey = stringPreferencesKey("theme")
    private val qbCategoryKey = stringPreferencesKey("qb_category")
    private val listJson = ListSerializer(String.serializer())

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            prowlarr = ProwlarrConfig(
                url = p[urlKey] ?: "",
                apiKey = p[apiKeyKey]?.let { SecretCrypto.decrypt(it) } ?: "",
            ),
            qbit = QbitConfig(
                url = p[qbUrlKey] ?: "",
                apiKey = p[qbApiKeyKey]?.let { SecretCrypto.decrypt(it) } ?: "",
                username = p[qbUserKey] ?: "",
                password = p[qbPassKey]?.let { SecretCrypto.decrypt(it) } ?: "",
            ),
        )
    }

    suspend fun save(s: AppSettings) {
        val pr = s.prowlarr.normalized()
        val qb = s.qbit.normalized()
        context.dataStore.edit { p ->
            p[urlKey] = pr.url
            p[apiKeyKey] = SecretCrypto.encrypt(pr.apiKey)
            p[qbUrlKey] = qb.url
            p[qbUserKey] = qb.username
            p[qbPassKey] = SecretCrypto.encrypt(qb.password)
            p[qbApiKeyKey] = SecretCrypto.encrypt(qb.apiKey)
        }
    }

    val lastUpdateCheck: Flow<Long> = context.dataStore.data.map { it[updateCheckKey] ?: 0L }

    suspend fun markUpdateCheck() {
        context.dataStore.edit { it[updateCheckKey] = System.currentTimeMillis() }
    }

    /** Dernières requêtes, la plus récente en tête (8 max). */
    val history: Flow<List<String>> = context.dataStore.data.map { p ->
        p[historyKey]?.let { runCatching { Json.decodeFromString(listJson, it) }.getOrNull() } ?: emptyList()
    }

    suspend fun addHistory(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        context.dataStore.edit { p ->
            val cur = p[historyKey]?.let { runCatching { Json.decodeFromString(listJson, it) }.getOrNull() } ?: emptyList()
            val next = (listOf(q) + cur.filter { !it.equals(q, ignoreCase = true) }).take(8)
            p[historyKey] = Json.encodeToString(listJson, next)
        }
    }

    suspend fun clearHistory() {
        context.dataStore.edit { it.remove(historyKey) }
    }

    /** Catégorie qBittorrent choisie au dernier envoi ; vide = aucune. */
    val qbCategory: Flow<String> = context.dataStore.data.map { it[qbCategoryKey] ?: "" }

    suspend fun setQbCategory(name: String) {
        context.dataStore.edit { it[qbCategoryKey] = name }
    }

    val theme: Flow<ThemeMode> = context.dataStore.data.map { p ->
        p[themeKey]?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: ThemeMode.SYSTEM
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }
}
