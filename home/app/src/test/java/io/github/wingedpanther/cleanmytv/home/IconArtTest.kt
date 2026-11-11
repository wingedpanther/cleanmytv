package io.github.wingedpanther.cleanmytv.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class IconArtTest {
    private val red = 0xFFE52D27.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val clear = 0

    @Test fun `a white shape inside a red logo is cut out for Mono (#91)`() {
        // A 10x10 red square with a 4x4 white middle, like SmartTube's S on its red shape.
        val px = IntArray(100) { i -> if (i % 10 in 3..6 && i / 10 in 3..6) white else red }
        val out = IconArt.cutOut(px)
        assertNotNull(out)
        assertEquals(0, out!![45] ushr 24)
        assertEquals(255, out[0] ushr 24)
        assertEquals(red and 0xFFFFFF, out[0] and 0xFFFFFF)
    }

    @Test fun `a logo in one colour is left alone`() {
        val px = IntArray(100) { i -> if (i % 10 < 5) red else clear }
        assertNull(IconArt.cutOut(px))
    }

    @Test fun `a logo in many colours, with none in charge, is left alone`() {
        val colours = intArrayOf(0xFF34A853.toInt(), 0xFF4285F4.toInt(), 0xFFFBBC04.toInt(), 0xFFEA4335.toInt())
        assertNull(IconArt.cutOut(IntArray(100) { i -> colours[i % 4] }))
    }

    @Test fun `shading close to the main colour stays`() {
        val darker = 0xFFC42520.toInt()
        val px = IntArray(100) { i -> if (i < 20) darker else red }
        assertNull(IconArt.cutOut(px))
    }
}
