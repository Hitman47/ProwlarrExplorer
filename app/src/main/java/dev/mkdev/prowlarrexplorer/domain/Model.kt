package dev.mkdev.prowlarrexplorer.domain

import kotlinx.serialization.Serializable

/** Connexion à l'instance Prowlarr. L'URL est normalisée à l'enregistrement (voir [normalized]). */
data class ProwlarrConfig(val url: String = "", val apiKey: String = "") {
    val configured: Boolean get() = url.isNotBlank() && apiKey.isNotBlank()

    fun normalized(): ProwlarrConfig {
        var u = url.trim().trimEnd('/')
        if (u.isNotEmpty() && !u.contains("://")) u = "http://$u"
        return copy(url = u, apiKey = apiKey.trim())
    }
}

/** Catégories Newznab standard ; liste vide = pas de filtre côté Prowlarr. */
enum class CategoryFilter(val label: String, val ids: List<Int>) {
    ALL("Tout", emptyList()),
    MOVIES("Films", listOf(2000)),
    TV("Séries", listOf(5000)),
    MUSIC("Musique", listOf(3000)),
    BOOKS("Livres", listOf(7000)),
}

@Serializable
data class Category(val id: Int = 0, val name: String = "")

/** GET /api/v1/indexer — seuls les champs utiles au filtre. */
@Serializable
data class Indexer(
    val id: Int,
    val name: String,
    val enable: Boolean = true,
    val protocol: String = "torrent",
    val priority: Int = 25,
)

/** Élément de résultat de GET /api/v1/search (ReleaseResource). */
@Serializable
data class Release(
    val guid: String,
    val indexerId: Int,
    val indexer: String = "",
    val title: String,
    val size: Long = 0,
    val seeders: Int? = null,
    val leechers: Int? = null,
    val age: Int = 0,
    val ageHours: Double = 0.0,
    val protocol: String = "torrent",
    val infoUrl: String? = null,
    val downloadUrl: String? = null,
    val magnetUrl: String? = null,
    val categories: List<Category> = emptyList(),
    val grabs: Int? = null,
    val publishDate: String = "",
)

/** Corps de POST /api/v1/search : Prowlarr pousse la release vers son client de téléchargement. */
@Serializable
data class GrabRequest(val guid: String, val indexerId: Int)

@Serializable
data class SystemStatus(val appName: String = "Prowlarr", val version: String = "", val instanceName: String = "")

@Serializable
data class DownloadClient(val id: Int, val name: String, val enable: Boolean = true, val protocol: String = "")

fun Long.humanSize(): String {
    if (this <= 0) return "?"
    val units = arrayOf("o", "Ko", "Mo", "Go", "To")
    var v = this.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return if (i == 0) "$this o" else String.format(java.util.Locale.FRANCE, "%.1f %s", v, units[i])
}

fun Release.humanAge(): String = when {
    ageHours < 1 -> "< 1 h"
    ageHours < 48 -> "${ageHours.toInt()} h"
    age < 60 -> "$age j"
    age < 730 -> "${age / 30} mois"
    else -> "${age / 365} ans"
}
