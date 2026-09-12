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
    private val updateCheckKey = longPreferencesKey("update_last_check")

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            prowlarr = ProwlarrConfig(
                url = p[urlKey] ?: "",
                apiKey = p[apiKeyKey]?.let { SecretCrypto.decrypt(it) } ?: "",
            ),
            qbit = QbitConfig(
                url = p[qbUrlKey] ?: "",
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
        }
    }

    val lastUpdateCheck: Flow<Long> = context.dataStore.data.map { it[updateCheckKey] ?: 0L }

    suspend fun markUpdateCheck() {
        context.dataStore.edit { it[updateCheckKey] = System.currentTimeMillis() }
    }
}
