package com.metrolist.desktop.portable

import kotlin.test.Test
import kotlin.test.assertEquals

class SpanPlannerTest {
    @Test
    fun knownDirectory() {
        assertEquals("f9688284ac58d7e4", SpanPlanner.cacheDirectory("WcHpojJz8Bc"))
    }

    @Test
    fun fiveMiBChunks() {
        val spans = SpanPlanner.plan("video", 5_847_658L)
        assertEquals(2, spans.size)
        assertEquals(5_242_880L, spans[0].length)
        assertEquals(5_242_880L, spans[1].position)
        assertEquals(604_778L, spans[1].length)
    }
}
