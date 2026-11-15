package io.github.wingedpanther.cleanmytv.home

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager

/**
 * Some TVs go back to their own keyboard at every restart (#98). Home notes the keyboard in use once the TV
 * has been up a while, and in the first minutes after a restart puts that one back, with the secure-settings
 * permission CleanMyTV granted. A keyboard that was uninstalled is left alone. The desktop app also says which
 * keyboard was in use before Clean up, so Home knows the right one even if the TV had already switched.
 */
object Keyboard {
    /** Before this, a keyboard change is the TV's restart, not the owner. */
    private const val SETTLE_MS = 5 * 60_000L
    /** Google's phone-remote keyboard, which TVs fall back to: it only types from a phone, so never the owner's pick. */
    private const val BRIDGE = "com.google.android.tv.remote.service/"

    fun ownersPick(ime: String?): Boolean = !ime.isNullOrBlank() && '/' in ime && !ime.startsWith(BRIDGE)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("keyboard", Context.MODE_PRIVATE)

    /** Remembers the owner's keyboard, or puts it back while the TV is still starting up. */
    fun check(ctx: Context) {
        if (SystemClock.elapsedRealtime() < SETTLE_MS) restore(ctx) else remember(ctx)
    }

    private fun remember(ctx: Context) {
        val now = runCatching { Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) }.getOrNull()
        if (!ownersPick(now)) { restore(ctx); return }
        if (prefs(ctx).getString("ime", null) != now) prefs(ctx).edit().putString("ime", now).apply()
    }

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
}
