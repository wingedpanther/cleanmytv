package io.github.wingedpanther.cleanmytv.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherRedirectTest {
    @Test fun `each queued retry checks the current Amazon screen`() {
        val amazon = LauncherRedirect.AMAZON
        val queued = sortedMapOf<Long, Runnable>()
        var settingsVisible = false
        var returns = 0
        LauncherRedirect.scheduleRetries(
            { callback, delay -> queued[delay] = callback },
            { LauncherRedirect.mayReturn(amazon, amazon, settingsVisible) },
            { returns++ }
        )
        assertEquals(listOf(300L, 1200L, 5300L), queued.keys.toList())

        settingsVisible = true   // The Settings grid appeared after the Home activity event.
        queued.getValue(300L).run()
        assertEquals(0, returns)
        settingsVisible = false  // Back to Amazon Home before the second retry.
        queued.getValue(1200L).run()
        assertEquals(1, returns)
        settingsVisible = true   // Settings reopened before the final retry.
        queued.getValue(5300L).run()
        assertEquals(1, returns)
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
