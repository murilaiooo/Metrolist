package com.metrolist.desktop.library.importer

data class ParsedImportLine(
    val position: Int,
    val original: String,
    val left: String,
    val right: String?,
)

object PlaylistTextParser {
    private const val MAX_LINES = 1000
    private val numbering = Regex("""^\s*(?:\d+[.)]|[-*•])\s+""")

    fun parse(text: String): List<ParsedImportLine> {
        val meaningful =
            text
                .removePrefix("\uFEFF")
                .lineSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .map { numbering.replaceFirst(it, "").trim() }
                .filter(String::isNotEmpty)
                .toList()

        require(meaningful.size <= MAX_LINES) {
            "Playlist import supports at most 1000 non-blank lines"
        }

        return meaningful.mapIndexed { position, original ->
            val tabIndex = original.indexOf('\t')
            val spacedHyphenIndex = original.indexOf(" - ")
            val separator =
                when {
                    tabIndex >= 0 -> Separator(tabIndex, 1)
                    spacedHyphenIndex >= 0 -> Separator(spacedHyphenIndex, 3)
                    else -> null
                }

            val left = separator?.let { original.substring(0, it.index).trim() } ?: original
            val right =
                separator
                    ?.let { original.substring(it.index + it.length).trim() }
                    ?.takeIf(String::isNotEmpty)

            ParsedImportLine(
                position = position,
                original = original,
                left = left,
                right = right,
            )
        }
    }

    private data class Separator(
        val index: Int,
        val length: Int,
    )
}
