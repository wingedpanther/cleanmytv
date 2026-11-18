package io.github.wingedpanther.cleanmytv.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherRedirectTest {
    @Test fun `Amazon settings sharing the home activity stay open through every retry`() {
        val amazon = LauncherRedirect.AMAZON
        // The same activity emits Home for both screens. The visible settings grid distinguishes them.
        for (retry in listOf(300, 1200, 5300)) {
            assertFalse("Settings must stay open at $retry ms", LauncherRedirect.mayReturn(amazon, amazon, true))
        }
        // Pressing Home removes the grid and allows the next redirect.
        assertTrue(LauncherRedirect.mayReturn(amazon, amazon, false))
    }

    @Test fun `a queued retry cannot cover another app or an unavailable window`() {
        val amazon = LauncherRedirect.AMAZON
        assertFalse(LauncherRedirect.mayReturn(amazon, "com.amazon.tv.settings.v2", false))
        assertFalse(LauncherRedirect.mayReturn(amazon, "io.github.wingedpanther.cleanmytv.home", false))
        assertFalse(LauncherRedirect.mayReturn(amazon, null, false))
    }

    @Test fun `hidden settings controls do not block Amazon home`() {
        assertTrue(LauncherRedirect.mayReturn(LauncherRedirect.AMAZON, LauncherRedirect.AMAZON, false))
    }
}
