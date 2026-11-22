package io.github.wingedpanther.cleanmytv.home

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepTest {
    @Test fun `Android 9 and later sleep through the key service`() {
        assertEquals(SleepBy.LOCK, KeyService.sleepBy(28, serviceOn = true, screensaver = true))
        assertEquals(SleepBy.LOCK, KeyService.sleepBy(34, serviceOn = true, screensaver = false))
    }

    @Test fun `Android 9 and later stay on with the service off, as before`() {
        assertEquals(SleepBy.NONE, KeyService.sleepBy(28, serviceOn = false, screensaver = true))
    }

    @Test fun `Android 8 starts the screensaver, service or not`() {
        assertEquals(SleepBy.SCREENSAVER, KeyService.sleepBy(26, serviceOn = true, screensaver = true))
        assertEquals(SleepBy.SCREENSAVER, KeyService.sleepBy(27, serviceOn = false, screensaver = true))
    }

    @Test fun `Android 8 with no screensaver to start stays on`() {
        assertEquals(SleepBy.NONE, KeyService.sleepBy(26, serviceOn = true, screensaver = false))
    }
}
