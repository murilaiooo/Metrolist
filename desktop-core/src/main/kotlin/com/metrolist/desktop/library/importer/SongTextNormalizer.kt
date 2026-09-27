package com.metrolist.desktop.library.importer

import java.text.Normalizer
import java.util.Locale

object SongTextNormalizer {
    private val combiningMarks = Regex("\\p{M}+")
    private val featuring = Regex("""\b(?:featuring|feat\.?|ft\.?)\b""", RegexOption.IGNORE_CASE)
    private val nonAlphaNumeric = Regex("""[^\p{L}\p{N}]+""")
    private val spaces = Regex("""\s+""")
    private val year = Regex("""\b(?:19|20)\d{2}\b""")
    private val versionMarkers =
        listOf(
            Regex("""\bradio\s+edit\b"""),
            Regex("""\bsped\s+up\b"""),
            Regex("""\bslowed(?:\s+and\s+reverb|\s+reverb)?\b"""),
            Regex("""\bnightcore\b"""),
            Regex("""\bkaraoke(?:\s+version)?\b"""),
            Regex("""\bcover(?:\s+version)?\b"""),
            Regex("""\blive(?:\s+version)?\b"""),
            Regex("""\bremix(?:ed)?\b"""),
            Regex("""\bacoustic(?:\s+version)?\b"""),
            Regex("""\bunplugged\b"""),
            Regex("""\bremaster(?:ed)?(?:\s+\d{4})?\b"""),
            Regex("""\bdeluxe(?:\s+edition)?\b"""),
            Regex("""\bexplicit(?:\s+version)?\b"""),
            Regex("""\bclean(?:\s+version)?\b"""),
        )

    fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        return decomposed
            .replace(combiningMarks, "")
            .lowercase(Locale.ROOT)
            .replace(featuring, " feat ")
            .replace("&", " and ")
            .replace(nonAlphaNumeric, " ")
            .replace(spaces, " ")
            .trim()
    }

    fun baseTitle(
        value: String,
        removeVersionMarkers: Boolean = false,
    ): String {
        var normalized = normalize(value)
        if (!removeVersionMarkers) return normalized
        val containedRemaster = "remaster" in normalized
        versionMarkers.forEach { marker -> normalized = normalized.replace(marker, " ") }
        if (containedRemaster) normalized = normalized.replace(year, " ")
        return normalized.replace(spaces, " ").trim()
    }
}
