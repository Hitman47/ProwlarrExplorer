package dev.mkdev.prowlarrexplorer.data

import dev.mkdev.prowlarrexplorer.domain.QbitCategory
import dev.mkdev.prowlarrexplorer.domain.QbitConfig
import dev.mkdev.prowlarrexplorer.domain.Torrent
import dev.mkdev.prowlarrexplorer.domain.TorrentFile
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class QbitError(message: String) : Exception(message)

/**
 * Client WebUI API v2 de qBittorrent. Clé API (≥ 5.2) en en-tête Bearer, sinon session par cookie SID
 * (login/mot de passe), sinon « bypass » IP côté qBittorrent. Un 403 en session cookie → re-login puis rejeu.
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

    private fun HttpRequestBuilder.auth(cfg: QbitConfig) {
        if (cfg.hasApiKey) header("Authorization", "Bearer ${cfg.apiKey}")
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
                when {
                    cfg.hasApiKey -> "Clé API refusée (403) : qBittorrent ≥ 5.2 requis, clé à régénérer ?"
                    cfg.hasCredentials -> "Session refusée (403)"
                    else -> "Authentification requise : clé API, login/mot de passe, ou bypass IP dans qBittorrent"
                },
            )
        }
        val text = r.bodyAsText()
        if (!r.status.isSuccess()) throw QbitError("HTTP ${r.status.value} ${text.take(120)}")
        return text
    }

    suspend fun version(cfg: QbitConfig = config()): String =
        call(cfg) { http.get("${cfg.url}/api/v2/app/version") { auth(cfg) } }

    private suspend fun stopStart(cfg: QbitConfig): Boolean = useStopStart ?: run {
        val v = call(cfg) { http.get("${cfg.url}/api/v2/app/webapiVersion") { auth(cfg) } }.trim()
        val parts = v.split('.').mapNotNull { it.toIntOrNull() }
        val recent = (parts.getOrNull(0) ?: 2) > 2 || (parts.getOrNull(1) ?: 0) >= 11
        recent.also { useStopStart = it }
    }

    suspend fun torrents(): List<Torrent> {
        val cfg = config()
        val text = call(cfg) {
            http.get("${cfg.url}/api/v2/torrents/info") {
                auth(cfg)
                parameter("sort", "added_on")
                parameter("reverse", "true")
            }
        }
        return json.decodeFromString(ListSerializer(Torrent.serializer()), text)
    }

    suspend fun categories(): List<QbitCategory> {
        val cfg = config()
        val text = call(cfg) { http.get("${cfg.url}/api/v2/torrents/categories") { auth(cfg) } }
        return json.decodeFromString(MapSerializer(String.serializer(), QbitCategory.serializer()), text)
            .values.sortedBy { it.name.lowercase() }
    }

    /** Ajout par lien (magnet ou URL http) ; `cookie` = session à présenter au tracker pour une URL de .torrent. */
    suspend fun addUrl(url: String, category: String, cookie: String? = null) {
        val cfg = config()
        val body = call(cfg) {
            http.submitForm(
                "${cfg.url}/api/v2/torrents/add",
                Parameters.build {
                    append("urls", url)
                    if (category.isNotBlank()) append("category", category)
                    if (!cookie.isNullOrBlank()) append("cookie", cookie)
                },
            ) { auth(cfg) }
        }
        if (body.startsWith("Fails")) throw QbitError("qBittorrent a refusé le lien")
    }

    /** Ajout d'un fichier .torrent (multipart). */
    suspend fun addTorrentFile(bytes: ByteArray, fileName: String, category: String) {
        val cfg = config()
        val body = call(cfg) {
            http.submitFormWithBinaryData(
                "${cfg.url}/api/v2/torrents/add",
                formData {
                    append("torrents", bytes, Headers.build {
                        append(HttpHeaders.ContentType, "application/x-bittorrent")
                        append(HttpHeaders.ContentDisposition, "filename=\"${fileName.replace("\"", "")}\"")
                    })
                    if (category.isNotBlank()) append("category", category)
                },
            ) { auth(cfg) }
        }
        if (body.startsWith("Fails")) throw QbitError("qBittorrent a refusé le fichier .torrent")
    }

    suspend fun pause(hash: String) {
        val cfg = config()
        val path = if (stopStart(cfg)) "stop" else "pause"
        call(cfg) { http.submitForm("${cfg.url}/api/v2/torrents/$path", Parameters.build { append("hashes", hash) }) { auth(cfg) } }
    }

    suspend fun resume(hash: String) {
        val cfg = config()
        val path = if (stopStart(cfg)) "start" else "resume"
        call(cfg) { http.submitForm("${cfg.url}/api/v2/torrents/$path", Parameters.build { append("hashes", hash) }) { auth(cfg) } }
    }

    suspend fun files(hash: String): List<TorrentFile> {
        val cfg = config()
        val text = call(cfg) { http.get("${cfg.url}/api/v2/torrents/files") { auth(cfg); parameter("hash", hash) } }
        return json.decodeFromString(ListSerializer(TorrentFile.serializer()), text)
    }

    /** priorité 0 = ne pas télécharger, 1 = normale. */
    suspend fun setFilePriority(hash: String, index: Int, priority: Int) = post(
        "torrents/filePrio", "hash" to hash, "id" to index.toString(), "priority" to priority.toString(),
    )

    suspend fun setCategory(hash: String, category: String) = post("torrents/setCategory", "hashes" to hash, "category" to category)

    suspend fun recheck(hash: String) = post("torrents/recheck", "hashes" to hash)

    /** Limites en octets/s ; 0 = illimité. */
    suspend fun setLimits(hash: String, down: Long, up: Long) {
        post("torrents/setDownloadLimit", "hashes" to hash, "limit" to down.toString())
        post("torrents/setUploadLimit", "hashes" to hash, "limit" to up.toString())
    }

    private suspend fun post(path: String, vararg params: Pair<String, String>) {
        val cfg = config()
        call(cfg) {
            http.submitForm("${cfg.url}/api/v2/$path", Parameters.build { params.forEach { (k, v) -> append(k, v) } }) { auth(cfg) }
        }
    }

    suspend fun delete(hash: String, deleteFiles: Boolean) {
        val cfg = config()
        call(cfg) {
            http.submitForm(
                "${cfg.url}/api/v2/torrents/delete",
                Parameters.build { append("hashes", hash); append("deleteFiles", deleteFiles.toString()) },
            ) { auth(cfg) }
        }
    }
}
