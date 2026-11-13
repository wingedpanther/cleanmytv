package io.github.wingedpanther.cleanmytv.home

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager

/**
 * Some TVs go back to their own keyboard at every restart (#98). Home notes the keyboard in use once the TV
 * has been up a while, and in the first minutes after a restart puts that one back, with the secure-settings
 * permission CleanMyTV granted. A keyboard that was uninstalled is left alone.
 */
object Keyboard {
    /** Before this, a keyboard change is the TV's restart, not the owner. */
    private const val SETTLE_MS = 5 * 60_000L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("keyboard", Context.MODE_PRIVATE)

    /** Remembers the owner's keyboard, or puts it back while the TV is still starting up. */
    fun check(ctx: Context) {
        if (SystemClock.elapsedRealtime() < SETTLE_MS) restore(ctx) else remember(ctx)
    }

    private fun remember(ctx: Context) {
        val now = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: return
        if (prefs(ctx).getString("ime", null) != now) prefs(ctx).edit().putString("ime", now).apply()
    }

    fun restore(ctx: Context) {
        val want = prefs(ctx).getString("ime", null) ?: return
        val r = ctx.contentResolver
        if (Settings.Secure.getString(r, Settings.Secure.DEFAULT_INPUT_METHOD) == want) return
        val enabled = Settings.Secure.getString(r, Settings.Secure.ENABLED_INPUT_METHODS).orEmpty().split(':').filter { it.isNotBlank() }
        val installed = ctx.getSystemService(InputMethodManager::class.java).inputMethodList.map { it.id }
        if (want !in installed && enabled.none { it.substringBefore(';') == want }) return
        runCatching {
            if (enabled.none { it.substringBefore(';') == want }) Settings.Secure.putString(r, Settings.Secure.ENABLED_INPUT_METHODS, (enabled + want).joinToString(":"))
            Settings.Secure.putString(r, Settings.Secure.DEFAULT_INPUT_METHOD, want)
            Log.i(TAG, "keyboard back to $want")
        }.onFailure { Log.w(TAG, "keyboard: $it") }
    }
}
