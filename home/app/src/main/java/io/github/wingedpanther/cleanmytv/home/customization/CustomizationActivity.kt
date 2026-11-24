package io.github.wingedpanther.cleanmytv.home.customization

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.wingedpanther.cleanmytv.home.ConfigStore
import io.github.wingedpanther.cleanmytv.home.customization.components.EditorTheme
import io.github.wingedpanther.cleanmytv.home.loadApps

class CustomizationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        ConfigStore.load(this)
        val apps = loadApps(this, glyphs = false)
        val initial = intent.getStringExtra(FIND)?.let { n -> SETTINGS_INDEX.firstOrNull { it.name == n } }

        setContent {
            val config by ConfigStore.config.collectAsState()
            EditorTheme.follow(config.accent, config.background)
            CustomizationDialog(
                config = config,
                apps = apps,
                onDismiss = { finish() },
                initial = initial,
            )
        }
    }

    companion object {
        /** A [Findable]'s English name, to open the editor on that setting. */
        const val FIND = "find"
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
