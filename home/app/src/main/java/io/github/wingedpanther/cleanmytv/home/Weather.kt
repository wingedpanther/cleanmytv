package io.github.wingedpanther.cleanmytv.home

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** The weather now, in the unit the desktop app chose. `code` is a WMO weather code. `metNo` asks for MET Norway's credit. */
data class WeatherNow(val temp: Int, val code: Int, val day: Boolean, val high: Int, val low: Int, val metNo: Boolean = false)

/**
 * Open-Meteo: free, no key, no account. One small request every 30 minutes while Home is open.
 * Some networks block Open-Meteo's addresses (#76, #80), so MET Norway answers when it doesn't, and
 * Home keeps asking whichever answered last.
 */
object Weather {
    @Volatile private var metNoFirst = false

    suspend fun fetch(cfg: HomeConfig): WeatherNow? = withContext(Dispatchers.IO) {
        val sources = if (metNoFirst) listOf(::metNo, ::openMeteo) else listOf(::openMeteo, ::metNo)
        for (source in sources) {
            val w = runCatching { source(cfg) }.onFailure { Log.i(TAG, "weather: $it") }.getOrNull() ?: continue
            metNoFirst = w.metNo
            return@withContext w
        }
        null
    }

    private fun openMeteo(cfg: HomeConfig): WeatherNow? {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto&temperature_unit=%s",
            cfg.weatherLat, cfg.weatherLon, if (cfg.weatherUnit == "f") "fahrenheit" else "celsius",
        )
        val o = get(url) ?: return null
        val now = o.getJSONObject("current")
        val daily = o.getJSONObject("daily")
        return WeatherNow(
            temp = Math.round(now.getDouble("temperature_2m")).toInt(),
            code = now.getInt("weather_code"),
            day = now.optInt("is_day", 1) == 1,
            high = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)).toInt(),
            low = Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)).toInt(),
        )
    }

    /** MET Norway asks for a User-Agent that names the app, and coordinates to 4 decimals. */
    private fun metNo(cfg: HomeConfig): WeatherNow? {
        val url = String.format(Locale.US, "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f", cfg.weatherLat, cfg.weatherLon)
        val o = get(url) ?: return null
        return metNoReading(o, Instant.now(), ZoneId.systemDefault(), cfg.weatherUnit == "f")
    }

    private fun get(url: String): JSONObject? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("User-Agent", "CleanMyTV-Home github.com/wingedpanther/cleanmytv")
        try {
            if (conn.responseCode != 200) return null
            // MET Norway's forecast runs about 10 days ahead; 512 KB covers it.
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText().take(512_000) })
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * A MET Norway forecast as a reading: the first hour that isn't over yet, and today's high and low from
 * the hours left in the TV's own day. Temperatures come in Celsius.
 */
fun metNoReading(o: JSONObject, now: Instant, zone: ZoneId, fahrenheit: Boolean): WeatherNow? {
    val series = o.getJSONObject("properties").getJSONArray("timeseries")
    val today = now.atZone(zone).toLocalDate()
    val hourAgo = now.minusSeconds(3600)
    var current: JSONObject? = null
    val temps = mutableListOf<Double>()
    for (i in 0 until series.length()) {
        val step = series.getJSONObject(i)
        val at = Instant.parse(step.getString("time"))
        if (at.isBefore(hourAgo)) continue
        if (current == null) current = step
        val t = step.getJSONObject("data").getJSONObject("instant").getJSONObject("details").optDouble("air_temperature")
        if (at.atZone(zone).toLocalDate() == today && !t.isNaN()) temps += t
    }
    val step = current ?: return null
    val data = step.getJSONObject("data")
    val temp = data.getJSONObject("instant").getJSONObject("details").getDouble("air_temperature")
    val symbol = data.optJSONObject("next_1_hours")?.optJSONObject("summary")?.optString("symbol_code")
        ?: data.optJSONObject("next_6_hours")?.optJSONObject("summary")?.optString("symbol_code") ?: ""
    fun unit(c: Double) = Math.round(if (fahrenheit) c * 9 / 5 + 32 else c).toInt()
    return WeatherNow(
        temp = unit(temp),
        code = wmoCode(symbol),
        day = !symbol.endsWith("_night") && !symbol.endsWith("_polartwilight"),
        high = unit(temps.maxOrNull() ?: temp),
        low = unit(temps.minOrNull() ?: temp),
        metNo = true,
    )
}

/** MET Norway's symbol names, like "lightrainshowers_day", to the WMO codes the icons know. */
fun wmoCode(symbol: String): Int {
    val s = symbol.substringBefore('_')
    return when {
        "thunder" in s -> 95
        "sleet" in s -> if (s.startsWith("light")) 66 else 67
        "snow" in s -> when {
            s.endsWith("showers") -> if (s.startsWith("heavy")) 86 else 85
            s.startsWith("light") -> 71
            s.startsWith("heavy") -> 75
            else -> 73
        }
        "rain" in s -> when {
            s.endsWith("showers") -> if (s.startsWith("light")) 80 else if (s.startsWith("heavy")) 82 else 81
            s.startsWith("light") -> 61
            s.startsWith("heavy") -> 65
            else -> 63
        }
        s == "fog" -> 45
        s == "clearsky" -> 0
        s == "fair" -> 1
        s == "partlycloudy" -> 2
        else -> 3
    }
}
