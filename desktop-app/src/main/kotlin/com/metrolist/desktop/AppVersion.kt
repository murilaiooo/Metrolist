package com.metrolist.desktop

import java.util.Properties

private object AppVersionResource

internal val METROLIST_WINDOWS_VERSION: String by lazy {
    val properties = Properties()
    val stream =
        checkNotNull(
            AppVersionResource::class.java.getResourceAsStream("/metrolist-version.properties"),
        ) {
            "MetroList Windows version resource is missing"
        }
    stream.use(properties::load)
    checkNotNull(properties.getProperty("version")?.takeIf(String::isNotBlank)) {
        "MetroList Windows version is missing"
    }
}

internal fun appVersionLabel(version: String): String = "v$version"
