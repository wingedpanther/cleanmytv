package io.github.wingedpanther.cleanmytv.home

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ButtonHoldTest {
    private fun parse(json: String) = HomeConfig.fromJson(JSONObject("""{"buttons":[$json]}""")).buttons.single()

    @Test fun `a button keeps its press job and gains a hold job`() {
        val b = parse("""{"code":183,"action":"settings","hold":"app","hold_app":"com.netflix.ninja"}""")
        assertEquals(ButtonMap(183, "settings", null, "app", "com.netflix.ninja"), b)
        assertEquals(b, HomeConfig.fromJson(HomeConfig(buttons = listOf(b)).toJson()).buttons.single())
    }

    @Test fun `a hold job that can't run is dropped, the press job stays`() {
        val noApp = parse("""{"code":183,"action":"home","hold":"app"}""")
        assertEquals("home", noApp.action)
        assertNull(noApp.hold)
        assertNull(parse("""{"code":183,"action":"home","hold":"dance"}""").hold)
        assertNull(parse("""{"code":183,"action":"home","hold":"none"}""").hold)
    }
}
