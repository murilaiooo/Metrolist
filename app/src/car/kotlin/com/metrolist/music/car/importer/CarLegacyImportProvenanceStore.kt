package com.metrolist.music.car.importer

import android.content.Context

class CarLegacyImportProvenanceStore(
    context: Context,
) {
    private val preferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun importedSongIds(): Set<String> =
        preferences.getStringSet(KEY_IMPORTED_IDS, emptySet())
            ?.toSet()
            .orEmpty()

    fun markImported(songIds: Collection<String>) {
        if (songIds.isEmpty()) return
        val merged = importedSongIds() + songIds
        preferences.edit()
            .putStringSet(KEY_IMPORTED_IDS, merged)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "car-legacy-provenance"
        const val KEY_IMPORTED_IDS = "imported-song-ids"
    }
}