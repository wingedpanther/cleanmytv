package io.github.wingedpanther.cleanmytv.home.customization

import android.content.Context
import io.github.wingedpanther.cleanmytv.home.customization.components.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import io.github.wingedpanther.cleanmytv.home.ConfigStore
import io.github.wingedpanther.cleanmytv.home.HomeConfig
import org.json.JSONObject

@Composable
fun WidgetCustomization(config: HomeConfig, ctx: Context) {
    var showSearchModal by remember { mutableStateOf(false) }
    val hasLocation = config.weatherPlace.isNotBlank()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // --- Weather Location Section ---
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Weather Location", color = TEXT_COLOR, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                text = if (hasLocation) "📍 Active location: ${config.weatherPlace}" else "📍 Active location: Not set (Weather widget is hidden)",
                color = if (hasLocation) Color(0xFF4CAF50) else MUTED_COLOR,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvPrimaryButton(
                    text = "Search City / Location",
                    onClick = { showSearchModal = true },
                    icon = { Icon(imageVector = Icons.Outlined.Search, contentDescription = "Search", modifier = Modifier.size(16.dp)) }
                )

                if (hasLocation) {
                    TvDestructiveButton(
                        text = "Clear Location",
                        onClick = {
                            ConfigStore.update(
                                ctx,
                                JSONObject()
                                    .put("weather_place", "")
                                    .put("weather_lat", 0.0)
                                    .put("weather_lon", 0.0)
                            )
                        },
                        icon = { Icon(imageVector = Icons.Outlined.Delete, contentDescription = "Clear", modifier = Modifier.size(14.dp)) }
                    )
                }
            }
        }

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

    if (showSearchModal) {
        PlaceSearchModal(
            onDismiss = { showSearchModal = false },
            onPlaceSelected = { place ->
                val label = if (place.detail.isNotBlank()) "${place.name}, ${place.detail}" else place.name
                ConfigStore.update(
                    ctx,
                    JSONObject()
                        .put("weather_place", label)
                        .put("weather_lat", place.lat)
                        .put("weather_lon", place.lon)
                )
                showSearchModal = false
            }
        )
    }
}
