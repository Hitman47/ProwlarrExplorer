package dev.mkdev.prowlarrexplorer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.mkdev.prowlarrexplorer.domain.ProwlarrConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** URL en clair, clé API chiffrée (Keystore), en DataStore. */
class SettingsStore(private val context: Context) {

    private val urlKey = stringPreferencesKey("url")
    private val apiKeyKey = stringPreferencesKey("api_key_enc")

    val config: Flow<ProwlarrConfig> = context.dataStore.data.map { p ->
        ProwlarrConfig(
            url = p[urlKey] ?: "",
            apiKey = p[apiKeyKey]?.let { SecretCrypto.decrypt(it) } ?: "",
        )
    }

    suspend fun save(c: ProwlarrConfig) {
        val n = c.normalized()
        context.dataStore.edit { p ->
            p[urlKey] = n.url
            p[apiKeyKey] = SecretCrypto.encrypt(n.apiKey)
        }
    }
}
