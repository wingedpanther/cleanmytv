package io.github.wingedpanther.cleanmytv.home

import org.junit.Assert.assertEquals
import org.junit.Test

// The Inputs menu from what a TV reports, as in #50: three named HDMI ports, two with a device on them.
class InputMenuTest {
    private fun port(id: String, label: String, custom: String? = null, hidden: Boolean = false) =
        InputRow(id, null, passthrough = true, hidden = hidden, customLabel = custom, label = label)

    private fun device(id: String, parent: String, label: String) =
        InputRow(id, parent, passthrough = true, hidden = false, customLabel = null, label = label)

    private val tuner = InputRow("tuner", null, passthrough = false, hidden = false, customLabel = null, label = "Live TV")

    @Test
    fun onePerPortWithTheNameTheOwnerGaveIt() {
        val rows = listOf(
            port("hdmi1", "HDMI 1", "PS5"), port("hdmi2", "HDMI 2", "Shield"), port("hdmi3", "HDMI 3", "Video"),
            device("cec4", "hdmi1", "PlayStation 5"), device("cec8", "hdmi2", "SHIELD"), tuner
        )
        assertEquals(listOf("hdmi1" to "PS5", "hdmi2" to "Shield", "hdmi3" to "Video"), inputMenu(rows))
    }

    @Test
    fun anUnnamedPortTakesTheNameOfItsDevice() {
        val rows = listOf(port("hdmi1", "HDMI 1"), device("cec4", "hdmi1", "PS5"), port("hdmi2", "HDMI 2", " "))
        assertEquals(listOf("hdmi1" to "PS5", "hdmi2" to "HDMI 2"), inputMenu(rows))
    }

    @Test
    fun aPortWithTwoDevicesKeepsItsOwnName() {
        val rows = listOf(port("hdmi1", "HDMI 1"), device("a", "hdmi1", "Soundbar"), device("b", "hdmi1", "PS5"))
        assertEquals(listOf("hdmi1" to "HDMI 1"), inputMenu(rows))
    }

    @Test
    fun skipsInputsTheOwnerHid() {
        assertEquals(listOf("hdmi2" to "HDMI 2"), inputMenu(listOf(port("hdmi1", "HDMI 1", hidden = true), port("hdmi2", "HDMI 2"))))
    }

    @Test
    fun keepsADeviceWhosePortIsNotListed() {
        assertEquals(listOf("cec4" to "PS5"), inputMenu(listOf(device("cec4", "gone", "PS5"))))
    }
}
