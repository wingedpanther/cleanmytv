package io.github.wingedpanther.cleanmytv.home

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class WeatherTest {
    private fun step(time: String, temp: Double, symbol: String?) = """
        {"time":"$time","data":{"instant":{"details":{"air_temperature":$temp}}${symbol?.let { ""","next_1_hours":{"summary":{"symbol_code":"$it"}}""" } ?: ""}}}
    """

    // Trimmed from a real locationforecast/2.0/compact answer: hourly steps in UTC.
    private val forecast = JSONObject("""{"properties":{"timeseries":[
        ${step("2026-11-11T08:00:00Z", -6.0, "cloudy")},
        ${step("2026-11-11T09:00:00Z", -4.2, "lightsnowshowers_day")},
        ${step("2026-11-11T12:00:00Z", -0.6, "partlycloudy_day")},
        ${step("2026-11-11T18:00:00Z", -9.4, "clearsky_night")},
        ${step("2026-11-11T21:00:00Z", -12.0, null)}
    ]}}""")

    @Test fun `MET Norway reads as the current hour with the rest of today's high and low`() {
        val w = metNoReading(forecast, Instant.parse("2026-11-11T09:20:00Z"), ZoneId.of("Asia/Yekaterinburg"), fahrenheit = false)!!
        // 09:00 is the hour under way; 21:00 UTC is already tomorrow in Surgut (UTC+5).
        assertEquals(WeatherNow(temp = -4, code = 85, day = true, high = -1, low = -9, metNo = true), w.copy(hours = emptyList()))
    }

    @Test fun `MET Norway's hours are the ones still to come, with their own sky`() {
        val w = metNoReading(forecast, Instant.parse("2026-11-11T09:20:00Z"), ZoneId.of("UTC"), fahrenheit = false)!!
        assertEquals(
            listOf(
                WeatherHour(Instant.parse("2026-11-11T12:00:00Z"), -1, 2, true),
                WeatherHour(Instant.parse("2026-11-11T18:00:00Z"), -9, 0, false),
                WeatherHour(Instant.parse("2026-11-11T21:00:00Z"), -12, 3, true),
            ),
            w.hours,
        )
    }

    @Test fun `Open-Meteo's hours are in the place's own time and start after now`() {
        // Dublin in summer is UTC+1: 14:00 there is 13:00 UTC.
        val o = JSONObject("""{"utc_offset_seconds":3600,
            "current":{"temperature_2m":18.4,"weather_code":2,"is_day":1},
            "daily":{"temperature_2m_max":[21.0],"temperature_2m_min":[12.6]},
            "hourly":{"time":["2026-07-01T14:00","2026-07-01T15:00","2026-07-01T16:00","2026-07-01T17:00","2026-07-01T18:00","2026-07-01T19:00"],
                "temperature_2m":[18.4,19.1,19.6,18.9,17.2,15.5],"weather_code":[2,3,61,80,3,0],"is_day":[1,1,1,1,1,0]}}""")
        val w = openMeteoReading(o, Instant.parse("2026-07-01T13:25:00Z"))
        assertEquals(18, w.temp)
        assertEquals(21, w.high)
        assertEquals(13, w.low)
        assertEquals(listOf(15, 16, 17, 18).map { Instant.parse("2026-07-01T%02d:00:00Z".format(it - 1)) }, w.hours.map { it.at })
        assertEquals(listOf(19, 20, 19, 17), w.hours.map { it.temp })
        assertEquals(listOf(3, 61, 80, 3), w.hours.map { it.code })
    }

    @Test fun `MET Norway in Fahrenheit, and night from the symbol`() {
        val w = metNoReading(forecast, Instant.parse("2026-11-11T18:10:00Z"), ZoneId.of("UTC"), fahrenheit = true)!!
        assertEquals(15, w.temp)
        assertEquals(false, w.day)
        assertEquals(0, w.code)
    }

    @Test fun `symbols map to the codes the icons know`() {
        val codes = listOf("clearsky_day", "fair_night", "partlycloudy_polartwilight", "cloudy", "fog", "lightrain", "heavyrainshowers_day",
            "sleet", "lightsleetshowers_day", "snow", "heavysnowshowers_night", "rainandthunder", "heavysnowshowersandthunder_day").map { wmoCode(it) }
        assertEquals(listOf(0, 1, 2, 3, 45, 61, 82, 67, 66, 73, 86, 95, 95), codes)
    }
}
