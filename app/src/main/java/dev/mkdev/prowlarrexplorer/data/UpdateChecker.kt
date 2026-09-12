package dev.mkdev.prowlarrexplorer.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.mkdev.prowlarrexplorer.BuildConfig
import dev.mkdev.prowlarrexplorer.domain.UpdateInfo
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.onDownload
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class UpdateError(message: String) : Exception(message)

/**
 * Mise à jour depuis les releases GitHub du dépôt : dernière release → asset .apk → téléchargement
 * dans cache/updates → installeur système. L'APK doit être signé par la même clé que celui installé.
 * OkHttp retire l'en-tête Authorization sur la redirection vers le stockage GitHub (autre hôte).
 */
class UpdateChecker(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http = HttpClient(OkHttp) {
        install(HttpTimeout) {
            connectTimeoutMillis = 8_000
            requestTimeoutMillis = 20_000
        }
        expectSuccess = false
    }

    private fun HttpRequestBuilder.github(token: String, accept: String) {
        header("Accept", accept)
        header("X-GitHub-Api-Version", "2022-11-28")
        if (token.isNotBlank()) header("Authorization", "Bearer $token")
    }

    /** Null si la version installée est déjà la dernière. */
    suspend fun latest(token: String): UpdateInfo? {
        val r = http.get("$API/releases/latest") { github(token, "application/vnd.github+json") }
        val text = r.bodyAsText()
        when {
            r.status == HttpStatusCode.NotFound ->
                throw UpdateError(if (token.isBlank()) "Aucune release, ou dépôt privé : renseigne un token GitHub" else "Aucune release publiée")
            r.status == HttpStatusCode.Unauthorized -> throw UpdateError("Token GitHub refusé (401)")
            !r.status.isSuccess() -> throw UpdateError("GitHub HTTP ${r.status.value}")
        }
        val rel = json.parseToJsonElement(text).jsonObject
        val tag = rel["tag_name"]?.jsonPrimitive?.content ?: throw UpdateError("Release sans tag")
        val version = tag.removePrefix("v")
        val apk = rel["assets"]?.jsonArray?.map { it.jsonObject }
            ?.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
            ?: throw UpdateError("Release $tag sans APK")
        if (!isNewer(version, BuildConfig.VERSION_NAME)) return null
        return UpdateInfo(
            tag = tag,
            version = version,
            assetId = apk["id"]?.jsonPrimitive?.content?.toLong() ?: 0L,
            assetName = apk["name"]?.jsonPrimitive?.content ?: "update.apk",
            size = apk["size"]?.jsonPrimitive?.content?.toLong() ?: 0L,
            pageUrl = rel["html_url"]?.jsonPrimitive?.content ?: "https://github.com/$REPO/releases",
        )
    }

    /** Télécharge l'asset (redirection GitHub → stockage) dans cache/updates ; progression 0..1. */
    suspend fun download(info: UpdateInfo, token: String, onProgress: (Float) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val file = File(dir, info.assetName)
        http.prepareGet("$API/releases/assets/${info.assetId}") {
            github(token, "application/octet-stream")
            timeout { requestTimeoutMillis = 300_000 }
            onDownload { sent, total -> if (total != null && total > 0) onProgress(sent.toFloat() / total) }
        }.execute { r ->
            if (!r.status.isSuccess()) throw UpdateError("Téléchargement HTTP ${r.status.value}")
            val ch = r.bodyAsChannel()
            file.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = ch.readAvailable(buf, 0, buf.size)
                    if (n < 0) break
                    if (n > 0) out.write(buf, 0, n)
                }
            }
        }
        if (info.size > 0 && file.length() != info.size) throw UpdateError("APK incomplet (${file.length()} / ${info.size} octets)")
        return file
    }

    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    companion object {
        const val REPO = "Hitman47/ProwlarrExplorer"
        private const val API = "https://api.github.com/repos/$REPO"

        /** Compare "0.3.1" à "0.2.0" segment par segment. */
        fun isNewer(candidate: String, current: String): Boolean {
            fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = parts(candidate); val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
