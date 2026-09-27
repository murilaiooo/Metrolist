package com.metrolist.music.car

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarManifestTest {
    @Test
    fun carManifestDeclaresDedicatedLandscapeLauncher() {
        val manifest =
            sequenceOf(
                File("src/car/AndroidManifest.xml"),
                File("app/src/car/AndroidManifest.xml"),
            ).firstOrNull(File::isFile)
                ?: error("Car manifest overlay is missing")

        val xml = manifest.readText()

        assertTrue(xml.contains("android:name=\".CarMainActivity\""))
        assertTrue(xml.contains("android:screenOrientation=\"landscape\""))
        assertTrue(xml.contains("android.intent.category.LAUNCHER"))
        assertTrue(xml.contains("android:name=\".MainActivityAlias\""))
        assertTrue(xml.contains("android:name=\".MainActivityStatic\""))
        assertFalse(xml.contains("android:enabled=\"true\""))
    }
}
