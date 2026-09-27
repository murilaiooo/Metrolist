package com.metrolist.desktop.library.importer

object SongVersionClassifier {
    fun classify(
        title: String,
        album: String?,
    ): SongVersionKind {
        val text = SongTextNormalizer.normalize(listOfNotNull(title, album).joinToString(" "))
        return when {
            Regex("""\bkaraoke\b""").containsMatchIn(text) -> SongVersionKind.KARAOKE
            Regex("""\bcover\b""").containsMatchIn(text) -> SongVersionKind.COVER
            Regex("""\bsped\s+up\b""").containsMatchIn(text) -> SongVersionKind.SPED_UP
            Regex("""\bslowed\b""").containsMatchIn(text) -> SongVersionKind.SLOWED
            Regex("""\bnightcore\b""").containsMatchIn(text) -> SongVersionKind.NIGHTCORE
            Regex("""\bradio\s+edit\b""").containsMatchIn(text) -> SongVersionKind.RADIO_EDIT
            Regex("""\blive\b""").containsMatchIn(text) -> SongVersionKind.LIVE
            Regex("""\bremix(?:ed)?\b""").containsMatchIn(text) -> SongVersionKind.REMIX
            Regex("""\bacoustic\b""").containsMatchIn(text) -> SongVersionKind.ACOUSTIC
            Regex("""\bunplugged\b""").containsMatchIn(text) -> SongVersionKind.UNPLUGGED
            Regex("""\bremaster(?:ed)?\b""").containsMatchIn(text) -> SongVersionKind.REMASTER
            Regex("""\bdeluxe\b""").containsMatchIn(text) -> SongVersionKind.DELUXE
            else -> SongVersionKind.ORIGINAL
        }
    }
}
