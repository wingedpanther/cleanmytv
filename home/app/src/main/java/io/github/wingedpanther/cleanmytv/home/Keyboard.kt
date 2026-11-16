package io.github.wingedpanther.cleanmytv.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager

/**
 * Some TVs go back to their own keyboard at every restart (#98), and some switch to the phone-remote keyboard
 * every time they wake from standby. Home notes the keyboard in use once the TV has been up a while, and puts
 * that one back with the secure-settings permission CleanMyTV granted: in the first minutes after a restart,
 * and right after the TV wakes. A keyboard that was uninstalled is left alone. The desktop app also says which
 * keyboard was in use before Clean up, so Home knows the right one even if the TV had already switched.
 */
object Keyboard {
    /** Before this, a keyboard change is the TV's restart, not the owner. */
    private const val SETTLE_MS = 5 * 60_000L
    /** A switch this soon after waking is the TV's doing too. */
    private const val WAKE_MS = 2 * 60_000L
    /** Google's phone-remote keyboard, which TVs fall back to: it only types from a phone, so never the owner's pick. */
    private const val BRIDGE = "com.google.android.tv.remote.service/"

    fun ownersPick(ime: String?): Boolean = !ime.isNullOrBlank() && '/' in ime && !ime.startsWith(BRIDGE)

    /** Whether a switch to [now] should be undone: only the bridge, only right after a restart or a wake, once per wake. */
    fun shouldUndo(now: String?, sinceBoot: Long, sinceWake: Long, undoneThisWake: Boolean, guard: Boolean): Boolean =
        guard && !undoneThisWake && now?.startsWith(BRIDGE) == true && (sinceBoot < SETTLE_MS || sinceWake < WAKE_MS)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("keyboard", Context.MODE_PRIVATE)

    /** Remembers the owner's keyboard, or puts it back while the TV is still starting up. */
    fun check(ctx: Context) {
        if (SystemClock.elapsedRealtime() < SETTLE_MS) restore(ctx) else remember(ctx)
    }

    private fun remember(ctx: Context) {
        val now = current(ctx)
        if (!ownersPick(now)) { restore(ctx); return }
        if (prefs(ctx).getString("ime", null) != now) prefs(ctx).edit().putString("ime", now).apply()
    }

    private fun current(ctx: Context) = runCatching { Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) }.getOrNull()

    /** The keyboard the desktop app saw before Clean up. */
    fun note(ctx: Context, ime: String) {
        if (ownersPick(ime)) prefs(ctx).edit().putString("ime", ime).apply()
    }

    fun restore(ctx: Context) {
        // Never in the way of Home starting: Android 14 and newer won't let apps read the enabled list (#98).
        runCatching {
            val want = prefs(ctx).getString("ime", null) ?: return
            val r = ctx.contentResolver
            if (Settings.Secure.getString(r, Settings.Secure.DEFAULT_INPUT_METHOD) == want) return
            val imm = ctx.getSystemService(InputMethodManager::class.java)
            if (imm.inputMethodList.none { it.id == want }) return
            val enabled = imm.enabledInputMethodList.map { it.id }
            if (want !in enabled) Settings.Secure.putString(r, Settings.Secure.ENABLED_INPUT_METHODS, (enabled + want).joinToString(":"))
            Settings.Secure.putString(r, Settings.Secure.DEFAULT_INPUT_METHOD, want)
            Log.i(TAG, "keyboard back to $want")
        }.onFailure { Log.w(TAG, "keyboard: $it") }
    }

    // The guard: catches the switch when it happens, not only when Home comes to the front.

    @Volatile private var watching = false
    @Volatile private var wokeAt = Long.MIN_VALUE / 2
    @Volatile private var undoneThisWake = false
    private val main by lazy { Handler(Looper.getMainLooper()) }

    /**
     * Watches the keyboard setting while Home's process runs, which for the TV's Home is nearly always. A switch
     * to the phone-remote keyboard just after a restart or a wake is undone once; later ones are left alone, as
     * someone may be typing from their phone. "Keep my keyboard" in the settings turns this off.
     */
    fun watch(context: Context) {
        if (watching) return
        watching = true
        val ctx = context.applicationContext
        runCatching {
            ctx.contentResolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD), false, object : ContentObserver(main) {
                override fun onChange(selfChange: Boolean) = guard(ctx)
            })
            ctx.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    wokeAt = SystemClock.elapsedRealtime()
                    undoneThisWake = false
                    // Some TVs switch before the screen-on broadcast, some a few seconds after it.
                    for (ms in listOf(0L, 4_000L, 15_000L)) main.postDelayed({ guard(ctx) }, ms)
                }
            }, IntentFilter(Intent.ACTION_SCREEN_ON))
        }.onFailure { watching = false; Log.w(TAG, "keyboard guard: $it") }
    }

    private fun guard(ctx: Context) {
        val now = current(ctx)
        val t = SystemClock.elapsedRealtime()
        if (!shouldUndo(now, t, t - wokeAt, undoneThisWake, ConfigStore.config.value.keyboardGuard)) return
        if (prefs(ctx).getString("ime", null) == null) return
        undoneThisWake = true
        Log.i(TAG, "keyboard switched to $now after a restart or wake")
        restore(ctx)
    }
}
