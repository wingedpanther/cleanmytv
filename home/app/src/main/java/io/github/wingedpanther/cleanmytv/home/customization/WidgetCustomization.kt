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
import io.github.wingedpanther.cleanmytv.home.tr
import org.json.JSONObject

@Composable
fun WidgetCustomization(config: HomeConfig, ctx: Context) {
    var showSearchModal by remember { mutableStateOf(false) }
    val hasLocation = config.weatherPlace.isNotBlank()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // --- Weather Location Section ---
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tr("Weather Location"), color = TEXT_COLOR, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                text = if (hasLocation) tr("📍 Active location: {place}", "place" to config.weatherPlace) else tr("📍 Active location: Not set (Weather widget is hidden)"),
                color = if (hasLocation) Color(0xFF4CAF50) else MUTED_COLOR,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvPrimaryButton(
                    text = tr("Search City / Location"),
                    onClick = { showSearchModal = true },
                    icon = { Icon(imageVector = Icons.Outlined.Search, contentDescription = tr("Search"), modifier = Modifier.size(16.dp)) }
                )

                if (hasLocation) {
                    TvDestructiveButton(
                        text = tr("Clear Location"),
                        onClick = {
                            ConfigStore.update(
                                ctx,
                                JSONObject()
                                    .put("weather_place", "")
                                    .put("weather_lat", 0.0)
                                    .put("weather_lon", 0.0)
                            )
                        },
                        icon = { Icon(imageVector = Icons.Outlined.Delete, contentDescription = tr("Clear"), modifier = Modifier.size(14.dp)) }
                    )
                }
            }
        }

        TvRadioGroup(
            title = tr("Clock"),
            options = listOf("time_date" to tr("Time & Date"), "time" to tr("Time Only"), "off" to tr("Off")),
            current = config.clock,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("clock", it)) }
        )

        TvRadioGroup(
            title = tr("Temperature"),
            options = listOf("c" to tr("Celsius (°C)"), "f" to tr("Fahrenheit (°F)")),
            current = config.weatherUnit,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("weather_unit", it)) }
        )

        TvSwitch(
            title = tr("Hourly forecast"),
            subtitle = tr("The next 4 hours under the weather"),
            checked = config.weatherHours,
            onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("weather_hours", it)) }
        )

        TvSlider(
            title = tr("Idle screen after"),
            value = config.idleMinutes,
            min = 0, max = 60, unit = tr(" min (0=Off)"),
            onChange = { ConfigStore.update(ctx, JSONObject().put("idle_minutes", it)) }
        )

        TvRadioGroup(
            title = tr("Idle screen"),
            options = listOf("corner" to tr("Clock in the corner"), "center" to tr("Big clock"), "photos" to tr("Photos")),
            current = config.idleStyle,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("idle_style", it)) }
        )
        if (config.idleStyle == "photos") Text(tr("The slideshow's photos, or the themes' pictures when there are none, a new one every 20 seconds."), color = MUTED_COLOR, fontSize = 13.sp)
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
