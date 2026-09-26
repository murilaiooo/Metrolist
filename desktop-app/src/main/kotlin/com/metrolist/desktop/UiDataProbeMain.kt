package com.metrolist.desktop

import com.metrolist.desktop.auth.AuthSessionValidator
import com.metrolist.desktop.auth.DpapiSessionStore
import java.nio.file.Path

suspend fun main() {
    val localAppData = requireNotNull(System.getenv("LOCALAPPDATA"))
    val session =
        requireNotNull(
            DpapiSessionStore(
                Path.of(localAppData, "MetroListWindows", "auth", "session.bin"),
            ).load(),
        ) { "Saved YouTube Music session is missing" }

    AuthSessionValidator().validate(session)

    val catalog = MetroListCatalogGateway()
    val home = catalog.homeSections()
    val search = catalog.searchSongs("Daft Punk")
    val library = catalog.libraryPlaylists()

    println(
        "UI_DATA_OK homeSections=" + home.size +
            " homeItems=" + home.sumOf { it.items.size } +
            " searchSongs=" + search.size +
            " libraryPlaylists=" + library.size,
    )

    kotlin.system.exitProcess(0)
}
