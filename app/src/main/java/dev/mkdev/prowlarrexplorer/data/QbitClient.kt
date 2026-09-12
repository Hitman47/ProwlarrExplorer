package dev.mkdev.prowlarrexplorer.data

import dev.mkdev.prowlarrexplorer.domain.QbitConfig
import dev.mkdev.prowlarrexplorer.domain.Torrent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class QbitError(message: String) : Exception(message)

/**
 * Client WebUI API v2 de qBittorrent. Session par cookie SID (login/mot de passe) ; sans identifiants,
 * on suppose le « bypass » IP activé côté qBittorrent. Un 403 en cours de session → re-login puis rejeu.
 */
class QbitClient(private val config: () -> QbitConfig) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private val http = HttpClient(OkHttp) {
        install(HttpCookies)
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 15_000
        }
        expectSuccess = false
    }

    private val loginLock = Mutex()
    /** Config pour laquelle la session est ouverte ; null = pas de session. */
    private var sessionFor: QbitConfig? = null
    /** qBittorrent ≥ 5.0 (API 2.11) a renommé pause/resume en stop/start. */
    private var useStopStart: Boolean? = null

    suspend fun login(cfg: QbitConfig) {
        val r = http.submitForm(
            url = "${cfg.url}/api/v2/auth/login",
            formParameters = Parameters.build {
                append("username", cfg.username)
                append("password", cfg.password)
            },
        )
        val body = r.bodyAsText()
        when {
            r.status == HttpStatusCode.Forbidden -> throw QbitError("IP bannie par qBittorrent (trop d'échecs)")
            !r.status.isSuccess() -> throw QbitError("HTTP ${r.status.value}")
            !body.startsWith("Ok") -> throw QbitError("Identifiants refusés")
        }
        sessionFor = cfg
    }

    private suspend fun ensureSession(cfg: QbitConfig) {
        if (!cfg.hasCredentials || sessionFor == cfg) return
        loginLock.withLock { if (sessionFor != cfg) login(cfg) }
    }

    private suspend fun call(cfg: QbitConfig = config(), retry: Boolean = true, block: suspend () -> HttpResponse): String {
        ensureSession(cfg)
        val r = block()
        if (r.status == HttpStatusCode.Forbidden) {
            if (retry && cfg.hasCredentials) {
                sessionFor = null
                return call(cfg, retry = false, block)
            }
            throw QbitError(
                if (cfg.hasCredentials) "Session refusée (403)"
                else "Authentification requise : renseigne login/mot de passe ou active le bypass IP dans qBittorrent",
            )
        }
        val text = r.bodyAsText()
        if (!r.status.isSuccess()) throw QbitError("HTTP ${r.status.value} ${text.take(120)}")
        return text
    }

    suspend fun version(cfg: QbitConfig = config()): String =
        call(cfg) { http.get("${cfg.url}/api/v2/app/version") }

    private suspend fun stopStart(cfg: QbitConfig): Boolean = useStopStart ?: run {
        val v = call(cfg) { http.get("${cfg.url}/api/v2/app/webapiVersion") }.trim()
        val parts = v.split('.').mapNotNull { it.toIntOrNull() }
        val recent = (parts.getOrNull(0) ?: 2) > 2 || (parts.getOrNull(1) ?: 0) >= 11
        recent.also { useStopStart = it }
    }

    suspend fun torrents(): List<Torrent> {
        val cfg = config()
        val text = call(cfg) {
            http.get("${cfg.url}/api/v2/torrents/info") {
                parameter("sort", "added_on")
                parameter("reverse", "true")
            }
        }
        return json.decodeFromString(ListSerializer(Torrent.serializer()), text)
    }

    suspend fun pause(hash: String) {
        val cfg = config()
        val path = if (stopStart(cfg)) "stop" else "pause"
        call(cfg) { http.submitForm("${cfg.url}/api/v2/torrents/$path", Parameters.build { append("hashes", hash) }) }
    }

    suspend fun resume(hash: String) {
        val cfg = config()
        val path = if (stopStart(cfg)) "start" else "resume"
        call(cfg) { http.submitForm("${cfg.url}/api/v2/torrents/$path", Parameters.build { append("hashes", hash) }) }
    }

    suspend fun delete(hash: String, deleteFiles: Boolean) {
        val cfg = config()
        call(cfg) {
            http.submitForm(
                "${cfg.url}/api/v2/torrents/delete",
                Parameters.build { append("hashes", hash); append("deleteFiles", deleteFiles.toString()) },
            )
        }
    }
}
