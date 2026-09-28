package com.metrolist.music.car

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CarManifestTest {
    @Test
    fun carManifestDeclaresDedicatedLandscapeLauncherAndRemovesPhoneEntryPoints() {
        val manifest =
            sequenceOf(
                File("src/car/AndroidManifest.xml"),
                File("app/src/car/AndroidManifest.xml"),
            ).firstOrNull(File::isFile)
                ?: error("Car manifest overlay is missing")

        val xml = manifest.readText()

        assertTrue(xml.contains("""android:name=".CarMainActivity""""))
        assertTrue(xml.contains("""android:screenOrientation="landscape""""))
        assertTrue(xml.contains("android.intent.category.LAUNCHER"))

        listOf(
            ".MainActivity",
            ".MainActivityAlias",
            ".MainActivityStatic",
            ".discord.DiscordOAuthActivity",
            ".recognition.RecognitionLaunchActivity",
            ".recognition.RecognitionForegroundService",
            ".quicksettings.MusicRecognizerTileService",
            ".widget.MusicWidgetReceiver",
            ".widget.PlaylistWidgetReceiver",
            ".widget.TurntableWidgetReceiver",
            ".widget.MusicRecognizerWidgetReceiver",
        ).forEach { component ->
            val declaration = """android:name="$component""""
            assertTrue(
                "Expected Car manifest to remove $component",
                xml.contains(declaration) &&
                    xml.substringAfter(declaration)
                        .substringBefore("/>")
                        .contains("""tools:node="remove""""),
            )
        }
    }
}