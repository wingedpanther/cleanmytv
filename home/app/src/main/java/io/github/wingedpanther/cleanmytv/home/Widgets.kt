package io.github.wingedpanther.cleanmytv.home

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** "Good evening": the part of the day, read again each minute, since Home can stay open from morning to night. */
@Composable
internal fun dayPart(): String {
    var hour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    LaunchedEffect(Unit) { while (true) { delay(60_000L - System.currentTimeMillis() % 60_000L); hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) } }
    return when (hour) { in 5..11 -> tr("Good morning"); in 12..17 -> tr("Good afternoon"); else -> tr("Good evening") }
}

/** "Good evening, Sam", or null when the owner left the name blank. The same words every layout shows. */
@Composable
internal fun greetingLine(cfg: HomeConfig): String? {
    val part = dayPart()
    return cfg.greeting.trim().takeIf { it.isNotEmpty() }?.let { "$part, $it" }
}

/** A WMO weather code in a word or two, lower case: "light rain". */
internal fun weatherWords(code: Int): String = when (code) {
    0 -> tr("clear")
    1, 2 -> tr("partly cloudy")
    3 -> tr("cloudy")
    45, 48 -> tr("fog")
    51, 53, 55, 56, 57 -> tr("drizzle")
    61, 80 -> tr("light rain")
    63, 81 -> tr("rain")
    65, 82 -> tr("heavy rain")
    66, 67 -> tr("sleet")
    71, 73, 75, 77, 85, 86 -> tr("snow")
    95, 96, 99 -> tr("thunder")
    else -> tr("cloudy")
}

/** "21:00" or "9 PM", as the TV keeps its clock. */
internal fun hourFormat(ctx: Context): SimpleDateFormat {
    val skeleton = if (android.text.format.DateFormat.is24HourFormat(ctx)) "Hm" else "ha"
    return SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton), Locale.getDefault())
}

/** Teletext's short hour: "22h", or "9pm" on a 12-hour clock, so four hours fit one line. */
internal fun textHour(ctx: Context, at: java.time.Instant): String {
    val t = at.atZone(java.time.ZoneId.systemDefault())
    return if (android.text.format.DateFormat.is24HourFormat(ctx)) "%02dh".format(t.hour)
    else "${(t.hour + 11) % 12 + 1}${if (t.hour < 12) "am" else "pm"}"
}

/** "2 h 05", or "48 min" under an hour: the short form where room is tight. */
internal fun shortTime(minutes: Int): String =
    if (minutes < 60) tr("{n} min", "n" to minutes) else tr("{h} h {mm}", "h" to minutes / 60, "mm" to "%02d".format(minutes % 60))

/** Screen time today and yesterday, in minutes, from the Home summary; null before there is any. */
internal fun screenMinutes(s: ScreenSummary?): Pair<Int, Int?>? {
    val week = s?.week?.takeIf { it.isNotEmpty() } ?: return null
    val today = (week.last().total / 60_000L).toInt()
    val yesterday = week.getOrNull(week.size - 2)?.let { (it.total / 60_000L).toInt() }
    return today to yesterday
}
