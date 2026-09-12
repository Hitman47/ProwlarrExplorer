package dev.mkdev.prowlarrexplorer.data

import dev.mkdev.prowlarrexplorer.domain.DownloadClient
import dev.mkdev.prowlarrexplorer.domain.GrabRequest
import dev.mkdev.prowlarrexplorer.domain.Indexer
import dev.mkdev.prowlarrexplorer.domain.ProwlarrConfig
import dev.mkdev.prowlarrexplorer.domain.Release
import dev.mkdev.prowlarrexplorer.domain.SystemStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ProwlarrError(message: String) : Exception(message)

/**
 * Client HTTP vers l'API v1 de Prowlarr (en-tête X-Api-Key).
 * La config est lue à chaque appel : modifiable dans les réglages sans recréer le client.
 */
class ProwlarrClient(private val config: () -> ProwlarrConfig) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private val http = HttpClient(OkHttp) {
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 15_000
        }
        expectSuccess = false
    }

    private fun HttpRequestBuilder.auth(cfg: ProwlarrConfig) {
        header("X-Api-Key", cfg.apiKey)
        header("Accept", "application/json")
    }

    suspend fun status(cfg: ProwlarrConfig = config()): SystemStatus {
        val r = http.get("${cfg.url}/api/v1/system/status") { auth(cfg) }
        return json.decodeFromString(SystemStatus.serializer(), r.okBody())
    }

    suspend fun downloadClients(cfg: ProwlarrConfig = config()): List<DownloadClient> {
        val r = http.get("${cfg.url}/api/v1/downloadclient") { auth(cfg) }
        return json.decodeFromString(ListSerializer(DownloadClient.serializer()), r.okBody())
    }

    suspend fun indexers(): List<Indexer> {
        val cfg = config()
        val r = http.get("${cfg.url}/api/v1/indexer") { auth(cfg) }
        return json.decodeFromString(ListSerializer(Indexer.serializer()), r.okBody())
    }

    /** Recherche multi-indexers ; Prowlarr interroge chaque indexer, d'où le délai long. */
    suspend fun search(query: String, categories: List<Int>, indexerIds: List<Int>, limit: Int = 100): List<Release> {
        val cfg = config()
        val r = http.get("${cfg.url}/api/v1/search") {
            auth(cfg)
            timeout { requestTimeoutMillis = 90_000 }
            parameter("query", query)
            parameter("type", "search")
            parameter("limit", limit)
            parameter("offset", 0)
            categories.forEach { parameter("categories", it) }
            indexerIds.forEach { parameter("indexerIds", it) }
        }
        return json.decodeFromString(ListSerializer(Release.serializer()), r.okBody())
    }

    /** Équivalent du bouton « grab » de l'UI web : Prowlarr envoie au client de téléchargement configuré. */
    suspend fun grab(release: Release) {
        val cfg = config()
        val r = http.post("${cfg.url}/api/v1/search") {
            auth(cfg)
            timeout { requestTimeoutMillis = 30_000 }
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(GrabRequest.serializer(), GrabRequest(release.guid, release.indexerId)))
        }
        r.okBody()
    }

    private suspend fun HttpResponse.okBody(): String {
        val text = bodyAsText()
        if (!status.isSuccess()) {
            if (status.value == 401) throw ProwlarrError("Clé API refusée (401)")
            throw ProwlarrError("HTTP ${status.value} ${errorDetail(text)}")
        }
        return text
    }

    /** Prowlarr renvoie soit {"message": ...}, soit une liste de {propertyName, errorMessage}. */
    private fun errorDetail(text: String): String = runCatching {
        val el = json.parseToJsonElement(text)
        when {
            el is kotlinx.serialization.json.JsonObject -> el["message"]?.jsonPrimitive?.content ?: text
            el is kotlinx.serialization.json.JsonArray ->
                el.jsonArray.joinToString("; ") { it.jsonObject["errorMessage"]?.jsonPrimitive?.content ?: "" }
            else -> text
        }
    }.getOrDefault(text).take(200)
}

fun Throwable.short(): String = when (this) {
    is ProwlarrError, is QbitError, is UpdateError -> message ?: "erreur"
    is java.net.ConnectException, is java.net.UnknownHostException -> "serveur injoignable"
    is java.net.SocketTimeoutException, is io.ktor.client.plugins.HttpRequestTimeoutException -> "délai dépassé"
    else -> message?.take(120) ?: javaClass.simpleName
}
