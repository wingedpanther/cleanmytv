package io.github.wingedpanther.cleanmytv.home

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.util.Log
import java.net.Inet4Address

/**
 * The TV settings the Quick settings panel changes. Most need WRITE_SECURE_SETTINGS (and the system table
 * WRITE_SETTINGS), which CleanMyTV grants over ADB when it installs Home; without them a row only shows
 * its value and OK opens the TV's own screen instead.
 */
object TvSettings {
    fun canWriteSecure(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    fun canWriteSystem(ctx: Context) = Settings.System.canWrite(ctx)

    /** One row: its choices, which one is set now, and how to set another. */
    class Choice(val label: String, val options: List<String>, val current: Int, val writable: Boolean, val set: ((Int) -> Unit)?, val fallback: Intent? = null)

    private val SAVER_MIN = listOf(0, 5, 10, 15, 30, 60)
    private val SLEEP_MIN = listOf(-1, 15, 30, 60, 120, 240)
    private const val ADGUARD = "dns.adguard-dns.com"

    private fun secureInt(ctx: Context, k: String, d: Int) = runCatching { Settings.Secure.getInt(ctx.contentResolver, k, d) }.getOrDefault(d)
    private fun systemInt(ctx: Context, k: String, d: Int) = runCatching { Settings.System.getInt(ctx.contentResolver, k, d) }.getOrDefault(d)
    private fun globalFloat(ctx: Context, k: String, d: Float) = runCatching { Settings.Global.getFloat(ctx.contentResolver, k, d) }.getOrDefault(d)

    private fun guarded(what: String, block: () -> Unit) = runCatching(block).onFailure { Log.w(TAG, "setting $what: $it") }

    private fun nearest(list: List<Int>, v: Int) = list.indices.minBy { kotlin.math.abs(list[it] - v) }

    fun screensaver(ctx: Context): Choice {
        val on = secureInt(ctx, "screensaver_enabled", 1) == 1
        val after = systemInt(ctx, Settings.System.SCREEN_OFF_TIMEOUT, 15 * 60_000) / 60_000
        val ok = canWriteSecure(ctx) && canWriteSystem(ctx)
        return Choice("Screensaver", SAVER_MIN.map { if (it == 0) "Off" else "After $it min" }, if (!on) 0 else nearest(SAVER_MIN, after).coerceAtLeast(1), ok,
            if (!ok) null else { i -> guarded("screensaver") {
                Settings.Secure.putInt(ctx.contentResolver, "screensaver_enabled", if (i == 0) 0 else 1)
                if (i > 0) Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, SAVER_MIN[i] * 60_000)
            } }, Intent(Settings.ACTION_DREAM_SETTINGS))
    }

    /** The screensavers on this TV (Google's Ambient mode, the TV maker's, the basic clock). */
    fun saverApp(ctx: Context): Choice {
        val pm = ctx.packageManager
        val dreams = pm.queryIntentServices(Intent("android.service.dreams.DreamService"), 0).map {
            ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) to it.loadLabel(pm).toString()
        }
        val now = Settings.Secure.getString(ctx.contentResolver, "screensaver_components")?.split(",")?.firstOrNull()
        val cur = dreams.indexOfFirst { it.first.flattenToString() == now }.coerceAtLeast(0)
        val ok = canWriteSecure(ctx) && dreams.size > 1
        return Choice("Screensaver app", dreams.map { it.second }.ifEmpty { listOf("None on this TV") }, cur, ok,
            if (!ok) null else { i -> guarded("screensaver app") { Settings.Secure.putString(ctx.contentResolver, "screensaver_components", dreams[i].first.flattenToString()) } },
            Intent(Settings.ACTION_DREAM_SETTINGS))
    }

    fun sleep(ctx: Context): Choice {
        val ms = secureInt(ctx, "sleep_timeout", -1)
        val cur = if (ms <= 0) 0 else nearest(SLEEP_MIN, ms / 60_000).coerceAtLeast(1)
        val ok = canWriteSecure(ctx)
        return Choice("Turn the TV off", SLEEP_MIN.map { if (it < 0) "Never" else if (it < 60) "After $it min" else "After ${it / 60} h" }, cur, ok,
            if (!ok) null else { i -> guarded("sleep") { Settings.Secure.putInt(ctx.contentResolver, "sleep_timeout", if (i == 0) -1 else SLEEP_MIN[i] * 60_000) } })
    }

    fun dns(ctx: Context): Choice {
        val on = Settings.Global.getString(ctx.contentResolver, "private_dns_mode") == "hostname"
        val host = Settings.Global.getString(ctx.contentResolver, "private_dns_specifier")?.takeIf { it.isNotBlank() } ?: ADGUARD
        val ok = canWriteSecure(ctx)
        return Choice("Ad-blocking DNS", listOf("Off", "On ($host)"), if (on) 1 else 0, ok,
            if (!ok) null else { i -> guarded("dns") {
                if (i == 1) Settings.Global.putString(ctx.contentResolver, "private_dns_specifier", host)
                Settings.Global.putString(ctx.contentResolver, "private_dns_mode", if (i == 1) "hostname" else "opportunistic")
            } })
    }

    private val SCALES = listOf(0f, 0.5f, 1f)
    fun animations(ctx: Context): Choice {
        val v = globalFloat(ctx, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val ok = canWriteSecure(ctx)
        return Choice("Animations", listOf("Off", "Fast", "Normal"), SCALES.indices.minBy { kotlin.math.abs(SCALES[it] - v) }, ok,
            if (!ok) null else { i -> guarded("animations") {
                for (k in listOf(Settings.Global.WINDOW_ANIMATION_SCALE, Settings.Global.TRANSITION_ANIMATION_SCALE, Settings.Global.ANIMATOR_DURATION_SCALE)) {
                    Settings.Global.putFloat(ctx.contentResolver, k, SCALES[i])
                }
            } })
    }

    fun clock(ctx: Context): Choice {
        val h24 = android.text.format.DateFormat.is24HourFormat(ctx)
        val ok = canWriteSystem(ctx)
        return Choice("Clock", listOf("12-hour", "24-hour"), if (h24) 1 else 0, ok,
            if (!ok) null else { i -> guarded("clock") { Settings.System.putString(ctx.contentResolver, Settings.System.TIME_12_24, if (i == 1) "24" else "12") } })
    }

    fun volume(ctx: Context): Choice {
        val am = ctx.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val muted = am.isStreamMute(AudioManager.STREAM_MUSIC)
        val opts = (0..max).map { if (it == 0) "Muted" else "$it of $max" }
        return Choice("Volume", opts, if (muted) 0 else am.getStreamVolume(AudioManager.STREAM_MUSIC), true, set = { i ->
            guarded("volume") {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (i == 0) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, 0)
                if (i > 0) am.setStreamVolume(AudioManager.STREAM_MUSIC, i, AudioManager.FLAG_SHOW_UI)
            }
        })
    }

    /** "Ethernet · 192.168.0.8" or "Wi-Fi · …", read only; OK opens the network screen. */
    fun network(ctx: Context): String = runCatching { networkLine(ctx) }.getOrDefault("Open")

    private fun networkLine(ctx: Context): String {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val net = cm.activeNetwork ?: return "Not connected"
        val caps = cm.getNetworkCapabilities(net)
        val kind = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            else -> "Connected"
        }
        val ip = cm.getLinkProperties(net)?.linkAddresses?.firstOrNull { it.address is Inet4Address }?.address?.hostAddress
        return listOfNotNull(kind, ip).joinToString(" · ")
    }

    fun debuggingOn(ctx: Context) = runCatching { Settings.Global.getInt(ctx.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1 }.getOrDefault(false)

    /** CleanMyTV's Finish step, from the TV: ADB and wireless debugging off. CleanMyTV can't connect after this. */
    fun debuggingOff(ctx: Context) = guarded("debugging") {
        runCatching { Settings.Global.putInt(ctx.contentResolver, "adb_wifi_enabled", 0) }
        Settings.Global.putInt(ctx.contentResolver, Settings.Global.ADB_ENABLED, 0)
    }
}
