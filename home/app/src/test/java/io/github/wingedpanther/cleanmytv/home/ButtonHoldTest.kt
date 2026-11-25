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

    @Test fun `the Home button can open CleanMyTV Home itself, pressed or held (#137)`() {
        assertEquals(ButtonMap(3, "launcher", null, "launcher", null), parse("""{"code":3,"action":"launcher","hold":"launcher"}"""))
    }

    @Test fun `buttons sharing a key code go by scan code (#148)`() {
        val cfg = HomeConfig.fromJson(JSONObject("""{"buttons":[
            {"code":296,"action":"home","scan":405},{"code":296,"action":"settings","scan":406},{"code":296,"action":"inputs"}]}"""))
        assertEquals(3, cfg.buttons.size)
        assertEquals("home", cfg.buttons.forKey(296, 405)?.action)
        assertEquals("settings", cfg.buttons.forKey(296, 406)?.action)
        // The one saved without a scan code takes the rest.
        assertEquals("inputs", cfg.buttons.forKey(296, 407)?.action)
        assertNull(cfg.buttons.forKey(297, 405))
        assertEquals(cfg.buttons, HomeConfig.fromJson(cfg.toJson()).buttons)
    }

    @Test fun `a button alone on its key code matches whatever the scan code`() {
        assertEquals("home", listOf(ButtonMap(183, "home", scan = 12)).forKey(183, 99)?.action)
        assertEquals("home", listOf(ButtonMap(183, "home")).forKey(183, 12)?.action)
    }
}
