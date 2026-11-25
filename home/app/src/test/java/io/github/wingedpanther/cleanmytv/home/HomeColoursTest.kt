package io.github.wingedpanther.cleanmytv.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeColoursTest {
    @After fun reset() = HomeColours.follow(HomeConfig().background)

    @Test fun `the default background keeps the green panels`() {
        HomeColours.follow(0xFF0B0B0FL)
        HomeColours.follow(HomeConfig().background)
        assertEquals(Color(0xFF2D3A2E), HomeColours.panel)
        assertEquals(Color(0xFF3A4A3B), HomeColours.raised)
    }

    @Test fun `a near-black background gives grey panels, not green (#150)`() {
        HomeColours.follow(0xFF0B0B0FL)
        val p = HomeColours.panel
        assertTrue(p.blue >= p.green && p.green - p.red < 0.01f)
        assertTrue(HomeColours.raised.luminance() > p.luminance())
    }

    @Test fun `a white background is darkened so the light text still reads`() {
        HomeColours.follow(0xFFFFFFFFL)
        assertTrue(HomeColours.panel.luminance() < 0.2f)
    }
}
