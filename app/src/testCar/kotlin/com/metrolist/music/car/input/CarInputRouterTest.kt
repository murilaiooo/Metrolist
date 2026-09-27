package com.metrolist.music.car.input

import android.view.InputDevice
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarInputRouterTest {
    private val source = InputDevice.SOURCE_KEYBOARD

    @Test
    fun standardMediaKeysAreRecognizedButDelegatedToAndroid() {
        val decision =
            CarInputRouter().decide(
                CarKeySignature(KeyEvent.KEYCODE_MEDIA_NEXT, scanCode = 163, source = source),
                KeyEvent.ACTION_DOWN,
            )

        assertEquals(CarMediaAction.NEXT, decision.action)
        assertFalse(decision.consume)
    }

    @Test
    fun volumeKeysAreNeverConsumed() {
        val signature = CarKeySignature(KeyEvent.KEYCODE_VOLUME_UP, scanCode = 115, source = source)
        val router = CarInputRouter(mapOf(signature to CarMediaAction.NEXT))

        val decision = router.decide(signature, KeyEvent.ACTION_DOWN)

        assertNull(decision.action)
        assertFalse(decision.consume)
    }

    @Test
    fun unknownKeysAreObservedButNotConsumed() {
        val decision =
            CarInputRouter().decide(
                CarKeySignature(keyCode = 777, scanCode = 888, source = source),
                KeyEvent.ACTION_DOWN,
            )

        assertNull(decision.action)
        assertFalse(decision.consume)
    }

    @Test
    fun confirmedVendorMappingConsumesOnlyExactSignatureOnKeyDown() {
        val confirmed = CarKeySignature(keyCode = 700, scanCode = 163, source = source)
        val router = CarInputRouter(mapOf(confirmed to CarMediaAction.NEXT))

        val mapped = router.decide(confirmed, KeyEvent.ACTION_DOWN)
        val wrongScan = router.decide(confirmed.copy(scanCode = 164), KeyEvent.ACTION_DOWN)
        val keyUp = router.decide(confirmed, KeyEvent.ACTION_UP)

        assertEquals(CarMediaAction.NEXT, mapped.action)
        assertTrue(mapped.consume)
        assertNull(wrongScan.action)
        assertFalse(wrongScan.consume)
        assertNull(keyUp.action)
        assertFalse(keyUp.consume)
    }
}
