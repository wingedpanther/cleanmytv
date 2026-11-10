package io.github.wingedpanther.cleanmytv.home.customization

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.wingedpanther.cleanmytv.home.ConfigStore
import io.github.wingedpanther.cleanmytv.home.loadApps

class CustomizationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        ConfigStore.load(this)
        val apps = loadApps(this, glyphs = false)

        setContent {
            val config by ConfigStore.config.collectAsState()
            CustomizationDialog(
                config = config,
                apps = apps,
                onDismiss = { finish() }
            )
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
