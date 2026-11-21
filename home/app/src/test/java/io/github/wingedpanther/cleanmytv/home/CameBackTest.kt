package io.github.wingedpanther.cleanmytv.home

import org.junit.Assert.assertEquals
import org.junit.Test

class CameBackTest {
    @Test fun `apps switched off and on again are the ones that came back`() {
        val on = setOf("com.google.android.tvrecommendations", "com.netflix.ninja")
        assertEquals(listOf("com.google.android.tvrecommendations"), CameBack.find(listOf("com.google.android.tvrecommendations", "com.android.vending.ads")) { it in on })
    }

    @Test fun `the owner hears about each app once`() {
        assertEquals(emptyList<String>(), CameBack.news(listOf("a.b", "c.d"), listOf("a.b", "c.d")))
        assertEquals(listOf("e.f"), CameBack.news(listOf("a.b", "e.f"), listOf("a.b")))
    }

    @Test fun `an input tile's name fits where a package name goes`() {
        val key = InputTiles.key("com.mediatek.tvinput/.hdmi.HDMIInputService/HW1")
        assertEquals("cmtv_input.com_mediatek_tvinput__hdmi_hdmiinputservice_hw1", key)
        assertEquals(listOf(key), HomeConfig.fromJson(org.json.JSONObject().put("order", org.json.JSONArray(listOf(key)))).order)
    }
}
