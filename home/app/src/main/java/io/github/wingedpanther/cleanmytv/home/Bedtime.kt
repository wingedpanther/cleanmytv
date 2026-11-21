package io.github.wingedpanther.cleanmytv.home

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Bedtime: on the chosen days the TV goes to sleep at a set time, as the sleep timer does. A minute before,
 * a note over whatever is on says so, and any button keeps the TV on for the night. A TV already asleep is
 * left alone. Home's button service shows the note and does the sleeping, so it stays on while bedtime is.
 */
object Bedtime {
    const val WARN_MS = 60_000L

    /** Until when the note is up and the next button press keeps the TV on. */
    @Volatile var warningUntil = 0L
    val warning: Boolean get() = System.currentTimeMillis() < warningUntil

    /** The next bedtime whose note is still ahead of [now], on one of [days] (1 Monday to 7 Sunday). */
    fun next(now: ZonedDateTime, at: Int, days: List<Int>): ZonedDateTime? {
        if (days.isEmpty()) return null
        val today = now.truncatedTo(ChronoUnit.DAYS).plusMinutes(at.toLong())
        return (0L..7L).map { today.plusDays(it) }
            .firstOrNull { it.dayOfWeek.value in days && it.minusNanos(WARN_MS * 1_000_000).isAfter(now) }
    }

    private fun alarm(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 1, Intent(ctx, BedtimeReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** Sets the alarm for the next note, or clears it when bedtime is off. */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(alarm(ctx))
        val cfg = ConfigStore.config.value
        if (!cfg.bedtimeOn) return
        val at = next(ZonedDateTime.now(), cfg.bedtimeAt, cfg.bedtimeDays) ?: return
        val warnAt = at.toInstant().toEpochMilli() - WARN_MS
        // Without the exact-alarm permission Android may stretch a window alarm to ten minutes late.
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, warnAt, alarm(ctx))
        else am.setWindow(AlarmManager.RTC_WAKEUP, warnAt, 60_000L, alarm(ctx))
        Log.i(TAG, "bedtime at $at")
    }

    internal fun due(ctx: Context) {
        val on = ctx.getSystemService(PowerManager::class.java)?.isInteractive ?: true
        val service = KeyService.running
        when {
            !ConfigStore.config.value.bedtimeOn -> Unit
            !on -> Log.i(TAG, "bedtime: the TV is already asleep")
            service == null -> Log.w(TAG, "bedtime: the button service is off, so the TV stays on")
            else -> { service.bedtimeNote(); return }
        }
        schedule(ctx)
    }
}

class BedtimeReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        ConfigStore.load(c)
        Bedtime.due(c)
    }
}
