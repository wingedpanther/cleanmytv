package io.github.wingedpanther.cleanmytv.home.customization

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.wingedpanther.cleanmytv.home.ConfigStore
import io.github.wingedpanther.cleanmytv.home.Group
import io.github.wingedpanther.cleanmytv.home.HomeConfig
import io.github.wingedpanther.cleanmytv.home.TvApp
import io.github.wingedpanther.cleanmytv.home.customization.components.Alert
import io.github.wingedpanther.cleanmytv.home.customization.components.MUTED_COLOR
import io.github.wingedpanther.cleanmytv.home.customization.components.ReorderableCheckList
import io.github.wingedpanther.cleanmytv.home.customization.components.TextPrompt
import io.github.wingedpanther.cleanmytv.home.customization.components.TvDestructiveButton
import io.github.wingedpanther.cleanmytv.home.customization.components.TvPrimaryButton
import io.github.wingedpanther.cleanmytv.home.customization.components.TvSecondaryButton
import org.json.JSONArray
import org.json.JSONObject

/** As Config.fromJson keeps them. */
private const val MAX_ROWS = 8
private const val MAX_FOLDERS = 12

/** Named rows and folders, as on the desktop app's Layout tab: make one, name it, fill it, order it, remove it. */
@Composable
fun RowsCustomization(config: HomeConfig, ctx: Context, apps: List<TvApp>) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Alert(text = "Rows show under the dock. Folders open on OK. An app can sit in a row and a folder at once.", modifier = Modifier.fillMaxWidth())
        Groups("Rows", "rows", config.rows, MAX_ROWS, ctx, apps)
        Groups("Folders", "folders", config.folders, MAX_FOLDERS, ctx, apps)
    }
}

@Composable
private fun Groups(title: String, key: String, groups: List<Group>, max: Int, ctx: Context, apps: List<TvApp>) {
    // Which group's apps are open for picking, and which name is being typed (-1: a new one).
    var picking by remember { mutableStateOf<Int?>(null) }
    var naming by remember { mutableStateOf<Int?>(null) }
    val save = { next: List<Group> ->
        ConfigStore.update(ctx, JSONObject().put(key, JSONArray(next.map { JSONObject().put("name", it.name).put("apps", JSONArray(it.apps)) })))
    }
    val singular = title.dropLast(1).lowercase()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (groups.isEmpty()) Text("No $key yet.", color = MUTED_COLOR, fontSize = 13.sp)
        groups.forEachIndexed { i, g ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0x11FFFFFF)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(g.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${g.apps.size} apps", color = MUTED_COLOR, fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvPrimaryButton(text = if (picking == i) "Done" else "Apps", onClick = { picking = if (picking == i) null else i })
                    TvSecondaryButton(text = "Rename", onClick = { naming = i })
                    if (i > 0) TvSecondaryButton(text = "Up", onClick = { save(groups.toMutableList().apply { add(i - 1, removeAt(i)) }); picking = null })
                    if (i < groups.size - 1) TvSecondaryButton(text = "Down", onClick = { save(groups.toMutableList().apply { add(i + 1, removeAt(i)) }); picking = null })
                    TvDestructiveButton(text = "Remove", onClick = { save(groups.filterIndexed { j, _ -> j != i }); picking = null })
                }
                if (picking == i) {
                    val byPkg = apps.associateBy { it.pkg }
                    ReorderableCheckList(
                        title = "",
                        subtitle = "",
                        // Apps that left the TV stay in the saved list, as the desktop app keeps them.
                        allItems = apps.map { it.pkg } + g.apps.filter { it !in byPkg },
                        activeItems = g.apps,
                        getLabel = { pkg -> byPkg[pkg]?.label ?: pkg },
                        onUpdateActive = { picked -> save(groups.mapIndexed { j, o -> if (j == i) o.copy(apps = picked) else o }) }
                    )
                }
            }
        }
        if (groups.size < max) TvSecondaryButton(text = "New $singular", onClick = { naming = -1 })
        else Text("That's the most $key Home shows.", color = MUTED_COLOR, fontSize = 13.sp)
    }

    naming?.let { i ->
        TextPrompt(
            title = if (i < 0) "Name the new $singular" else "Rename ${groups[i].name}",
            initial = if (i < 0) "" else groups[i].name,
            onDismiss = { naming = null },
            onDone = { name ->
                naming = null
                if (name.isEmpty()) return@TextPrompt
                if (i < 0) { save(groups + Group(name, emptyList())); picking = groups.size }
                else save(groups.mapIndexed { j, o -> if (j == i) o.copy(name = name) else o })
            }
        )
    }
}
