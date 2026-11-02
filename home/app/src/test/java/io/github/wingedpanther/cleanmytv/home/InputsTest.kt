package io.github.wingedpanther.cleanmytv.home

import org.junit.Assert.assertEquals
import org.junit.Test

class InputsTest {
    private val svc = "com.mediatek.tvinput/.hdmi.HDMIInputService"

    private fun port(n: Int, custom: String? = null, hidden: Boolean = false) =
        InputRow("$svc/HW$n", null, hdmi = true, hidden = hidden, custom = custom, label = "HDMI $n")

    private fun device(n: Int, name: String) =
        InputRow("$svc/HDMI${n}00$n", "$svc/HW$n", hdmi = true, hidden = false, custom = null, label = name)

    private val av = InputRow("com.mediatek.tvinput/.composite.CompositeInputService/HW10", null, hdmi = false, hidden = false, custom = null, label = "AV")

    @Test fun `the #50 TV shows one entry per port with the names from TV settings`() {
        // As listed on a Xiaomi MiTV-MOOQ0: CEC devices first, then the ports, then AV.
        val rows = listOf(device(2, "SHIELD"), port(3, "Video"), port(2, "Shield"), port(1, "PS5"), av, device(1, "PlayStation 5"))
        assertEquals(listOf("PS5", "Shield", "Video", "AV"), pickInputs(rows).map { it.second })
        assertEquals("$svc/HW1", pickInputs(rows).first().first)
    }

    @Test fun `a port with no name of its own takes the device's name, then the TV's`() {
        val rows = listOf(port(1), port(2), device(2, "SHIELD"))
        assertEquals(listOf("HDMI 1", "SHIELD"), pickInputs(rows).map { it.second })
    }

    @Test fun `inputs hidden in TV settings stay out, with their devices`() {
        val rows = listOf(port(1, hidden = true), device(1, "PlayStation 5"), port(2))
        assertEquals(listOf("HDMI 2"), pickInputs(rows).map { it.second })
    }

    @Test fun `a device whose port isn't listed stands in for it, and blank names fall through`() {
        val rows = listOf(device(4, "Xbox"), port(1, custom = "  "))
        assertEquals(listOf("HDMI 1", "Xbox"), pickInputs(rows).map { it.second })
    }
}
