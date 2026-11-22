package io.github.wingedpanther.cleanmytv.home

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The sleep timer: the TV goes to sleep after so many minutes, as the power button would. Home's button
 * service does the sleeping, so it's switched on for it. Android's alarm wakes Home for it even if the TV
 * closed Home in the meantime; a restart of the TV forgets it.
 */
object SleepTimer {
    val CHOICES = listOf(15, 30, 60, 90, 120)

    private val _at = MutableStateFlow<Long?>(null)
    /** When the TV goes to sleep (wall clock), or null when no timer is set. */
    val at: StateFlow<Long?> = _at

    val pending: Boolean get() = (_at.value ?: 0L) > System.currentTimeMillis()

    private fun alarm(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 0, Intent(ctx, SleepReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** Sleep in [minutes], or no timer with null. */
    fun set(ctx: Context, minutes: Int?) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(alarm(ctx))
        if (minutes == null) { _at.value = null; return }
        val at = System.currentTimeMillis() + minutes * 60_000L
        _at.value = at
        KeyService.ensureOn(ctx, force = true)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm(ctx))
    }

    /** For a remote button: off, then each choice in turn, then off again. Says what it set. */
    fun next(ctx: Context) {
        val left = _at.value?.let { ((it - System.currentTimeMillis()) / 60_000L).toInt() } ?: -1
        val pick = CHOICES.firstOrNull { it > left + 1 }
        set(ctx, pick)
        Toast.makeText(ctx, pick?.let { tr("Sleep in {time}", "time" to Limits.words(it)) } ?: tr("Sleep timer off"), Toast.LENGTH_SHORT).show()
    }

    internal fun fire(ctx: Context) {
        _at.value = null
        if (!KeyService.sleep(ctx)) Log.w(TAG, "sleep timer: the button service is off, so the TV stays on")
    }
}

class SleepReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = SleepTimer.fire(c)
}
