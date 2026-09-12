package dev.mkdev.prowlarrexplorer.domain

enum class TagKind { RES, HDR, SOURCE, CODEC, LANG, AUDIO }

data class Tag(val label: String, val kind: TagKind)

/** Nom de release décomposé : titre lisible, année / saison, étiquettes techniques. */
data class ParsedTitle(
    val title: String,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val tags: List<Tag> = emptyList(),
) {
    val heading: String
        get() = buildString {
            append(title)
            year?.let { append(" ($it)") }
            season?.let { s ->
                append(" · S").append(s.toString().padStart(2, '0'))
                episode?.let { e -> append("E").append(e.toString().padStart(2, '0')) }
            }
        }
}

/**
 * Analyse heuristique de « Titre.2023.MULTI.1080p.WEB-DL.x265-GROUP » : le titre s'arrête au premier
 * jeton reconnu (année, SxxEyy, étiquette). Sans jeton reconnu, le nom brut sert de titre.
 */
object ReleaseTitle {

    private val tokenRe = Regex("""[^ ._\-\[\]()]+""")
    private val yearRe = Regex("""^(19|20)\d{2}$""")
    private val seasonRe = Regex("""^S(\d{1,2})(?:E(\d{1,3}))?$""", RegexOption.IGNORE_CASE)
    private val seasonWordRe = Regex("""^(?:Saison|Season)$""", RegexOption.IGNORE_CASE)

    private val tagRules: List<Pair<Regex, TagKind>> = listOf(
        Regex("""^(2160p|1080p|1080i|720p|480p|4K|UHD)$""", RegexOption.IGNORE_CASE) to TagKind.RES,
        Regex("""^(HDR|HDR10|HDR10Plus|DV|DoVi|DolbyVision|SDR)$""", RegexOption.IGNORE_CASE) to TagKind.HDR,
        Regex("""^(BluRay|BDRip|BRRip|Remux|WEBDL|WEBRip|WEB|HDTV|DVDRip|DVD|HDRip|HDLight|CAM|TS)$""", RegexOption.IGNORE_CASE) to TagKind.SOURCE,
        Regex("""^(x265|x264|HEVC|H264|H265|AV1|XviD|DivX)$""", RegexOption.IGNORE_CASE) to TagKind.CODEC,
        Regex("""^(MULTI|FRENCH|TRUEFRENCH|VFF|VFQ|VF2|VF|VOSTFR|VOST|SUBFRENCH|ENGLISH|GERMAN|ITALIAN|SPANISH|NORDIC)$""", RegexOption.IGNORE_CASE) to TagKind.LANG,
        Regex("""^(Atmos|TrueHD|DTSHD|DTS|DDP71|DDP51|DD51|DDP|EAC3|AC3|AAC|FLAC|MP3|Opus)$""", RegexOption.IGNORE_CASE) to TagKind.AUDIO,
    )

    private val canonical = mapOf(
        "WEBDL" to "WEB-DL", "H264" to "H.264", "H265" to "H.265", "DDP51" to "DDP 5.1", "DD51" to "DD 5.1",
        "DDP71" to "DDP 7.1", "DTSHD" to "DTS-HD", "DOLBYVISION" to "DV", "DOVI" to "DV", "HDR10PLUS" to "HDR10+",
        "4K" to "2160p", "UHD" to "2160p",
    )

    fun parse(raw: String): ParsedTitle {
        // WEB-DL, H.264, DDP5.1 : recollés avant la découpe en jetons.
        val norm = raw
            .replace(Regex("""(?i)WEB-DL"""), "WEBDL")
            .replace(Regex("""(?i)\bH\.26([45])"""), "H26$1")
            .replace(Regex("""(?i)DTS-HD"""), "DTSHD")
            .replace(Regex("""(?i)HDR10\+"""), "HDR10Plus")
            .replace(Regex("""(?<!\d)([257])\.([01])(?!\d)"""), "$1$2")

        val tokens = tokenRe.findAll(norm).toList()
        var year: Int? = null
        var season: Int? = null
        var episode: Int? = null
        val tags = LinkedHashMap<String, Tag>()
        var stop = -1

        tokens.forEachIndexed { i, m ->
            val t = m.value
            var hit = false
            if (i > 0 && year == null && yearRe.matches(t)) { year = t.toInt(); hit = true }
            seasonRe.find(t)?.let { s ->
                if (season == null) { season = s.groupValues[1].toInt(); episode = s.groupValues[2].toIntOrNull() }
                hit = true
            }
            if (seasonWordRe.matches(t) && i + 1 < tokens.size) {
                tokens[i + 1].value.toIntOrNull()?.let { if (season == null) season = it; hit = true }
            }
            tagRules.firstOrNull { (re, _) -> re.matches(t) }?.let { (_, kind) ->
                val label = canonical[t.uppercase()] ?: t
                tags.putIfAbsent(label.uppercase(), Tag(label, kind))
                hit = true
            }
            if (hit && stop < 0 && i > 0) stop = m.range.first
        }

        val title = (if (stop > 0) norm.substring(0, stop) else norm)
            .replace(Regex("""[._]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', '[', '(', ')', ']')
            .ifBlank { raw.take(60) }

        val ordered = tags.values.sortedBy { it.kind.ordinal }.take(6)
        return ParsedTitle(title, year, season, episode, ordered)
    }
}
