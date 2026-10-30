package io.github.wingedpanther.cleanmytv.home

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** The weather now, in the unit the desktop app chose. `code` is a WMO weather code. */
data class WeatherNow(val temp: Int, val code: Int, val day: Boolean, val high: Int, val low: Int)

/** Open-Meteo: free, no key, no account. One small request every 30 minutes while Home is open. */
object Weather {
    suspend fun fetch(cfg: HomeConfig): WeatherNow? = withContext(Dispatchers.IO) {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto&temperature_unit=%s",
            cfg.weatherLat, cfg.weatherLon, if (cfg.weatherUnit == "f") "fahrenheit" else "celsius",
        )
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            try {
                if (conn.responseCode != 200) return@runCatching null
                val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText().take(64_000) })
                val now = o.getJSONObject("current")
                val daily = o.getJSONObject("daily")
                WeatherNow(
                    temp = Math.round(now.getDouble("temperature_2m")).toInt(),
                    code = now.getInt("weather_code"),
                    day = now.optInt("is_day", 1) == 1,
                    high = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)).toInt(),
                    low = Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)).toInt(),
                )
            } finally {
                conn.disconnect()
            }
        }.onFailure { Log.i(TAG, "weather: $it") }.getOrNull()
    }
}
