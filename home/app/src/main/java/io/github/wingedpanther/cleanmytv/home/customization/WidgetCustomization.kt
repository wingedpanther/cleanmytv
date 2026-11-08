package io.github.wingedpanther.cleanmytv.home.customization

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import io.github.wingedpanther.cleanmytv.home.ConfigStore
import io.github.wingedpanther.cleanmytv.home.HomeConfig
import org.json.JSONObject

@Composable
fun WidgetCustomization(config: HomeConfig, ctx: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TvRadioGroup(
            title = "Clock Format",
            options = listOf("time_date" to "Time & Date", "time" to "Time Only", "off" to "Off"),
            current = config.clock,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("clock", it)) }
        )

        TvRadioGroup(
            title = "Weather Unit",
            options = listOf("c" to "Celsius (°C)", "f" to "Fahrenheit (°F)"),
            current = config.weatherUnit,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("weather_unit", it)) }
        )

        TvSlider(
            title = "Idle Screen Timer",
            value = config.idleMinutes,
            min = 0, max = 60, unit = " min (0=Off)",
            onChange = { ConfigStore.update(ctx, JSONObject().put("idle_minutes", it)) }
        )
    }
}
