package io.github.wingedpanther.cleanmytv.home

import io.github.wingedpanther.cleanmytv.home.PowerOn.Wake
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerOnTest {
    private val tvApp = "com.tcl.tvinput"
    private val hdmi1 = "com.mediatek.tvinput/.hdmi.HDMIInputService/HW5"
    private val hdmi2 = "com.mediatek.tvinput/.hdmi.HDMIInputService/HW6"
    private val inputs = setOf(hdmi1, hdmi2)
    private val apps = setOf(tvApp)
    private val home = "io.github.wingedpanther.cleanmytv.home"

    @Test fun `the last input goes back to the input app the TV slept on`() {
        assertEquals(Wake.App(tvApp), PowerOn.decide(true, "", tvApp, home, apps, inputs))
        assertTrue(PowerOn.decide(true, "", tvApp, tvApp, apps, inputs) is Wake.Skip)
        assertTrue(PowerOn.decide(true, "", null, home, apps, inputs) is Wake.Skip)
        assertTrue(PowerOn.decide(false, "", tvApp, home, apps, inputs) is Wake.Skip)
    }

    @Test fun `a picked input opens at every power on, unless the TV is on an input already`() {
        assertEquals(Wake.Input(hdmi2), PowerOn.decide(true, hdmi2, null, home, apps, inputs))
        assertEquals(Wake.Input(hdmi2), PowerOn.decide(true, hdmi2, tvApp, "com.netflix.ninja", apps, inputs))
        // A cold start, before any window has come up.
        assertEquals(Wake.Input(hdmi1), PowerOn.decide(true, hdmi1, null, null, apps, inputs))
        assertTrue(PowerOn.decide(true, hdmi2, null, tvApp, apps, inputs) is Wake.Skip)
        assertTrue(PowerOn.decide(false, hdmi2, null, home, apps, inputs) is Wake.Skip)
    }

    @Test fun `an input the TV no longer has opens nothing, and says so`() {
        val w = PowerOn.decide(true, "com.mediatek.tvinput/.hdmi.HDMIInputService/HW9", tvApp, home, apps, inputs)
        assertTrue(w is Wake.Skip && w.why.contains("HW9"))
    }

    @Test fun `the wait adds to the settle time, and only the offered waits are taken`() {
        assertEquals(PowerOn.SETTLE_MS, PowerOn.delayMs(HomeConfig()))
        assertEquals(PowerOn.SETTLE_MS + 5000, PowerOn.delayMs(HomeConfig(powerOnWait = 5)))
        val c = HomeConfig.fromJson(JSONObject("""{"power_on_target":"$hdmi1","power_on_wait":10}"""))
        assertEquals(hdmi1, c.powerOnTarget)
        assertEquals(10, c.powerOnWait)
        assertEquals(c, HomeConfig.fromJson(c.toJson()))
        assertEquals(10, HomeConfig.fromJson(JSONObject("""{"power_on_wait":7}"""), c).powerOnWait)
        assertEquals(hdmi1, HomeConfig.fromJson(JSONObject("""{"power_on_target":"x; rm -rf /"}"""), c).powerOnTarget)
        assertEquals("", HomeConfig.fromJson(JSONObject("""{"power_on_target":""}"""), c).powerOnTarget)
        assertEquals("", HomeConfig().powerOnTarget)
        assertEquals(0, HomeConfig().powerOnWait)
    }
}
