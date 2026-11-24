package io.github.wingedpanther.cleanmytv.home

import org.junit.Assert.assertEquals
import org.junit.Test

class GuestTest {
    @Test fun `a WPA network gives the code phone cameras read`() {
        assertEquals("WIFI:T:WPA;S:Living Room;P:hunter22;;", Guest.wifiCode("Living Room", "hunter22", "WPA"))
    }

    @Test fun `the characters the format uses are escaped`() {
        assertEquals("""WIFI:T:WPA;S:a\;b\,c;P:pa\;ss\:word\"1\;;""", Guest.wifiCode("a;b,c", """pa;ss:word"1\""", "WPA"))
    }

    @Test fun `an open network has no password`() {
        assertEquals("WIFI:T:nopass;S:Cafe;;", Guest.wifiCode("Cafe", "ignored", "nopass"))
    }
}
