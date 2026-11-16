package io.github.wingedpanther.cleanmytv.home

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

/** How much memory the TV has free, in bytes. */
data class MemoryNow(val free: Long, val total: Long)

/**
 * Free memory, and Clean memory: apps left running in the background are closed, the way Android closes
 * them itself when memory runs short. They start again when opened. Playing music and other foreground
 * work is left alone. Android 14 stopped apps closing other apps (killBackgroundProcesses then only
 * reaches the caller's own), so the button is only offered up to Android 13.
 */
object Memory {
    val canClean: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    fun read(ctx: Context): MemoryNow {
        val mi = ActivityManager.MemoryInfo()
        ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
        return MemoryNow(mi.availMem, mi.totalMem)
    }

    /** Closes background apps, and returns how much memory came back (never below 0). */
    suspend fun clean(ctx: Context): Long = withContext(Dispatchers.IO) {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val before = read(ctx).free
        // The keyboard would only start again on the next text box; persistent system apps can't be closed.
        val keyboard = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')
        val apps = ctx.packageManager.getInstalledApplications(0)
            .filter { it.packageName != ctx.packageName && it.packageName != keyboard && it.flags and ApplicationInfo.FLAG_PERSISTENT == 0 }
        for (a in apps) runCatching { am.killBackgroundProcesses(a.packageName) }.onFailure { Log.w(TAG, "clean ${a.packageName}: $it") }
        // Android hands the pages back over the next moment, not at once.
        delay(1500)
        (read(ctx).free - before).coerceAtLeast(0)
    }

    /** Cleans, then says what came back, for the top bar button and a remote button. */
    suspend fun cleanAndSay(ctx: Context) {
        val freed = clean(ctx)
        val now = read(ctx)
        withContext(Dispatchers.Main) {
            Toast.makeText(ctx, tr("Memory cleaned: {back} back, {free} free", "back" to size(freed), "free" to size(now.free)), Toast.LENGTH_SHORT).show()
        }
    }

    /** 820 MB, 1.4 GB. */
    fun size(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb < 1000) tr("{n} MB", "n" to mb.toInt()) else tr("{n} GB", "n" to String.format(Locale.US, "%.1f", mb / 1024.0))
    }

    /** "1.4 of 2 GB free", "820 MB of 2 GB free": the total rounded up to the size the box is sold as. */
    fun line(m: MemoryNow): String {
        val gb = 1024.0 * 1024.0 * 1024.0
        val sold = (m.total / gb).let { if (it < 1) String.format(Locale.US, "%.1f", it) else kotlin.math.ceil(it).toInt().toString() }
        val mb = m.free / (1024.0 * 1024.0)
        val free = if (mb < 1000) size(m.free) else String.format(Locale.US, "%.1f", mb / 1024.0)
        return tr("{free} of {total} GB free", "free" to free, "total" to sold)
    }
}
