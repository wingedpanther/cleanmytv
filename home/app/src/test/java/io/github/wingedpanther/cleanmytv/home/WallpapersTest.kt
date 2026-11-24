package io.github.wingedpanther.cleanmytv.home

import org.junit.Assert.assertEquals
import org.junit.Test

class WallpapersTest {
    @Test fun `a picture the size of the screen or smaller is decoded whole`() {
        assertEquals(1, Wallpapers.sampleFor(1920, 1080, 1920, 1080))
        assertEquals(1, Wallpapers.sampleFor(1280, 720, 1920, 1080))
        assertEquals(1, Wallpapers.sampleFor(3000, 2000, 1920, 1080))
    }

    @Test fun `a huge photo is shrunk in halves but never below the screen`() {
        assertEquals(2, Wallpapers.sampleFor(3840, 2160, 1920, 1080))
        assertEquals(4, Wallpapers.sampleFor(8192, 5464, 1920, 1080))
        // A tall panorama keeps enough height.
        assertEquals(1, Wallpapers.sampleFor(12000, 1500, 1920, 1080))
    }
}
