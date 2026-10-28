package io.github.wingedpanther.cleanmytv.home

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File

/** What the spike lets CleanMyTV change. The real launcher will carry the whole layout. */
data class HomeConfig(
    val accent: Long = 0xFFFF3000,
    val focus: String = "glow", // glow, outline, none
    val visualiser: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("accent", "#%06X".format(accent and 0xFFFFFF))
        .put("focus", focus)
        .put("visualiser", visualiser)

    companion object {
        private val FOCUS = setOf("glow", "outline", "none")

        /** Unknown keys are ignored and bad values keep the default, so an old desktop app can't break Home. */
        fun fromJson(o: JSONObject, base: HomeConfig = HomeConfig()): HomeConfig {
            var c = base
            o.optString("accent").takeIf { Regex("^#[0-9A-Fa-f]{6}$").matches(it) }?.let {
                c = c.copy(accent = 0xFF000000 or it.substring(1).toLong(16))
            }
            o.optString("focus").takeIf { it in FOCUS }?.let { c = c.copy(focus = it) }
            if (o.has("visualiser")) c = c.copy(visualiser = o.optBoolean("visualiser", c.visualiser))
            return c
        }
    }
}

/**
 * Settings live in the app's private storage, which is there as soon as Home starts. That is the
 * fix for upstream's reboot reset (#21) and the folder Android won't let ADB create (#20).
 */
object ConfigStore {
    private val _config = MutableStateFlow(HomeConfig())
    val config: StateFlow<HomeConfig> = _config
    private var loaded = false

    private fun file(ctx: Context) = File(ctx.filesDir, "config.json")

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val f = file(ctx)
        if (f.exists()) runCatching { _config.value = HomeConfig.fromJson(JSONObject(f.readText())) }
    }

    /** Merges a partial update, saves it atomically, and the UI follows through the flow. */
    @Synchronized
    fun update(ctx: Context, json: JSONObject): HomeConfig {
        load(ctx)
        val next = HomeConfig.fromJson(json, _config.value)
        val tmp = File(ctx.filesDir, "config.json.tmp")
        tmp.writeText(next.toJson().toString(2))
        tmp.renameTo(file(ctx))
        _config.value = next
        return next
    }
}
