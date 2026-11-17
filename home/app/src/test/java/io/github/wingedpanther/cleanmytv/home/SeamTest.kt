package io.github.wingedpanther.cleanmytv.home

import org.junit.Assert.assertEquals
import org.junit.Test

class SeamTest {
    private fun Seam.feed(pass: Long, s: String): String {
        val b = s.toByteArray()
        val n = take(pass, b, b.size)
        return String(out, 0, n)
    }

    @Test fun `every pass loses its lead-in and its tail, so the loop has no gap`() {
        // Two lead-in frames (<) and one frame of tail (>), one byte to a frame.
        val seam = Seam(delay = 2, padding = 1, frameBytes = 1)
        val played = buildString {
            for (pass in 0L..2L) {
                append(seam.feed(pass, "<<ab"))
                append(seam.feed(pass, "cde>"))
            }
        }
        assertEquals("abcdeabcdeabcde", played)
    }

    @Test fun `a lead-in longer than one buffer is cut across buffers, in whole frames`() {
        val seam = Seam(delay = 3, padding = 0, frameBytes = 2)
        assertEquals("", seam.feed(0, "<<<<"))
        assertEquals("aabb", seam.feed(0, "<<aabb"))
        assertEquals("cc", seam.feed(0, "cc"))
    }

    @Test fun `with no lead-in or tail everything plays as it comes`() {
        val seam = Seam(delay = 0, padding = 0, frameBytes = 4)
        assertEquals("abcdefgh", seam.feed(0, "abcdefgh"))
        assertEquals("ijkl", seam.feed(1, "ijkl"))
    }
}
