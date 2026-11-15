package io.github.wingedpanther.cleanmytv.home.customization.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Place(
    val name: String,
    val detail: String,
    val lat: Double,
    val lon: Double
)

suspend fun findPlaces(query: String): List<Place> = withContext(Dispatchers.IO) {
    val q = query.trim()
    if (q.length < 2) return@withContext emptyList()
    val urlStr = "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(q, "UTF-8")}&count=6&language=en&format=json"
    runCatching {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
        }
        if (conn.responseCode != 200) return@runCatching emptyList()
        val jsonStr = conn.inputStream.bufferedReader().use { it.readText().take(64_000) }
        val results = JSONObject(jsonStr).optJSONArray("results") ?: return@runCatching emptyList()
        val list = mutableListOf<Place>()
        for (i in 0 until minOf(results.length(), 6)) {
            val p = results.optJSONObject(i) ?: continue
            val name = p.optString("name").take(40)
            val lat = p.optDouble("latitude", 0.0)
            val lon = p.optDouble("longitude", 0.0)
            val admin1 = p.optString("admin1", "")
            val country = p.optString("country", "")
            val detail = listOf(admin1, country).filter { it.isNotBlank() && it != name }.joinToString(", ")
            if (name.isNotEmpty() && lat != 0.0 && lon != 0.0) {
                list.add(Place(name, detail, lat, lon))
            }
        }
        list
    }.getOrDefault(emptyList())
}

@Composable
fun PlaceSearchModal(
    onDismiss: () -> Unit,
    onPlaceSelected: (Place) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Place>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var inputFocused by remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val inputFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(250)
        runCatching {
            inputFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(searchQuery) {
        val query = searchQuery.trim()
        if (query.length >= 2) {
            isSearching = true
            delay(350)
            searchResults = findPlaces(query)
            isSearching = false
        } else {
            searchResults = emptyList()
            isSearching = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xBB000000)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(520.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DIALOG_BG)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Search City / Location",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    TvSecondaryButton(
                        text = "Close",
                        onClick = onDismiss
                    )
                }

                // Search Input Box directly wrapping BasicTextField with focus and keyboard controller
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (inputFocused) Color.White else Color(0x22FFFFFF))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = if (inputFocused) CUSTOMIZATION_BG else MUTED_COLOR,
                            modifier = Modifier.size(18.dp)
                        )

                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Enter city name (e.g. London, New York, Tokyo)...",
                                    color = if (inputFocused) CUSTOMIZATION_BG.copy(alpha = 0.6f) else MUTED_COLOR,
                                    fontSize = 13.sp
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                textStyle = TextStyle(
                                    color = if (inputFocused) CUSTOMIZATION_BG else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                cursorBrush = SolidColor(if (inputFocused) CUSTOMIZATION_BG else PRIMARY_BUTTON_COLOR),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        keyboardController?.hide()
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(inputFocusRequester)
                                    .onFocusChanged { state ->
                                        inputFocused = state.isFocused
                                        if (state.isFocused) {
                                            keyboardController?.show()
                                        }
                                    }
                            )
                        }
                    }
                }

                // Search Results Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isSearching) {
                        Text(
                            text = "Searching cities...",
                            color = PRIMARY_BUTTON_COLOR,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else if (searchQuery.trim().length >= 2 && searchResults.isEmpty()) {
                        Text(
                            text = "No cities found for '$searchQuery'",
                            color = MUTED_COLOR,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else if (searchResults.isNotEmpty()) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(searchResults) { place ->
                                var itemFocused by remember { mutableStateOf(false) }
                                Surface(
                                    onClick = { onPlaceSelected(place) },
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = Color(0x18FFFFFF),
                                        focusedContainerColor = Color.White
                                    ),
                                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { itemFocused = it.isFocused }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.LocationOn,
                                            contentDescription = null,
                                            tint = if (itemFocused) CUSTOMIZATION_BG else PRIMARY_BUTTON_COLOR,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = place.name,
                                                color = if (itemFocused) CUSTOMIZATION_BG else Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (place.detail.isNotBlank()) {
                                                Text(
                                                    text = place.detail,
                                                    color = if (itemFocused) CUSTOMIZATION_BG.copy(alpha = 0.7f) else MUTED_COLOR,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
