package io.github.wingedpanther.cleanmytv.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ST_TEXT = Color(0xFFF0EBE5)
private val ST_MUTED = Color(0xFFD7CFC4)

/** An app in a screen time list: its name and icon, and how long it was on. */
class UsedApp(val pkg: String, val label: String, val icon: ImageBitmap?, val ms: Long)

/** What the Home screen section shows: the last 7 days, today last, and today's top apps. */
class ScreenSummary(val week: List<DayUse>, val top: List<UsedApp>)

object ScreenTimeApps {
    private val known = HashMap<String, Pair<String, ImageBitmap?>>()

    /** Names and icons, looked up once. Home itself is "Home screen"; an app since removed keeps its package name. */
    @Synchronized
    fun info(ctx: Context, pkg: String): Pair<String, ImageBitmap?> = known.getOrPut(pkg) {
        val pm = ctx.packageManager
        runCatching {
            val ai = pm.getApplicationInfo(pkg, 0)
            val label = if (pkg == ctx.packageName) tr("Home screen") else pm.getApplicationLabel(ai).toString()
            label to runCatching { pm.getApplicationIcon(ai).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }.getOrDefault(pkg to null)
    }

    /** The apps on for a minute or more, longest first. */
    fun top(ctx: Context, apps: Map<String, Long>, n: Int = Int.MAX_VALUE): List<UsedApp> =
        apps.entries.filter { it.value >= 60_000 && it.key.isNotEmpty() }.sortedByDescending { it.value }.take(n)
            .map { (p, ms) -> info(ctx, p).let { (label, icon) -> UsedApp(p, label, icon, ms) } }

    /** Null without usage access. Off the main thread. */
    fun summary(ctx: Context): ScreenSummary? {
        val days = ScreenTime.days(ctx, LocalDate.now().minusDays(6)) ?: return null
        return ScreenSummary(days, top(ctx, days.last().apps, 3))
    }

    fun open(ctx: Context) {
        runCatching { ctx.startActivity(Intent(ctx, ScreenTimeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** Adds up apps over several days. */
private fun merged(days: List<DayUse>): Map<String, Long> = HashMap<String, Long>().also { m -> days.forEach { d -> d.apps.forEach { (p, ms) -> m.merge(p, ms, Long::plus) } } }

/** The top of the chart: the next whole hour, or quarter hour under an hour. */
private fun scaleFor(max: Long): Long {
    val step = if (max >= 3_600_000L) 3_600_000L else 900_000L
    return maxOf(step, (max + step - 1) / step * step)
}

/**
 * Bars for a day's hours or a run of days. [mark] is drawn in the accent, the rest in a softer accent; an
 * empty slot keeps a faint stub so the run of days still reads. With [scaleLabel], the top line says what it is.
 * Each label sits under [span] bars: one per day, or one per 6 hours, starting at its first bar.
 */
@Composable
fun UseBars(values: List<Long>, labels: List<String>, accent: Color, modifier: Modifier, mark: Int = values.lastIndex, scaleLabel: Boolean = true, labelSize: Float = 12f, span: Int = 1) {
    val scale = scaleFor(values.maxOrNull() ?: 0L)
    Column(modifier) {
        if (scaleLabel) Text(ScreenTime.words(scale), color = ST_MUTED.copy(alpha = 0.7f), fontSize = labelSize.sp, modifier = Modifier.padding(bottom = 4.dp))
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val slot = size.width / values.size.coerceAtLeast(1)
            val w = (slot * 0.62f).coerceAtLeast(2f)
            if (scaleLabel) drawLine(ST_MUTED.copy(alpha = 0.18f), Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
            values.forEachIndexed { i, v ->
                val x = i * slot + (slot - w) / 2
                val h = if (v <= 0) 2.dp.toPx() else (size.height * v / scale).coerceAtLeast(3.dp.toPx())
                val c = if (v <= 0) ST_MUTED.copy(alpha = 0.2f) else if (i == mark) accent else accent.copy(alpha = 0.5f)
                drawRoundRect(c, Offset(x, size.height - h), Size(w, h), CornerRadius(minOf(4.dp.toPx(), w / 2)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 5.dp)) {
            labels.forEach { Text(it, color = ST_MUTED.copy(alpha = 0.8f), fontSize = labelSize.sp, maxLines = 1, softWrap = false, textAlign = if (span == 1) TextAlign.Center else TextAlign.Start, modifier = Modifier.weight(span.toFloat())) }
        }
    }
}

/** "18:00" or "6 PM" every 6 hours, as the TV keeps its clock, for bars with a span of 6. */
private fun hourLabels(ctx: Context): List<String> {
    val skeleton = if (android.text.format.DateFormat.is24HourFormat(ctx)) "Hm" else "ha"
    val f = DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton), Locale.getDefault())
    return listOf(0, 6, 12, 18).map { LocalTime.of(it, 0).format(f) }
}

private val WEEKDAY: DateTimeFormatter get() = DateTimeFormatter.ofPattern("EEEEE", Locale.getDefault())

/** The section on the Home screen draws this inside its own focusable card. It names itself, so the section has no title. */
@Composable
fun ScreenTimeCardBody(s: ScreenSummary?, accent: Color) {
    if (s == null) {
        Text(tr("Screen time needs usage access. In CleanMyTV on a computer, tick Screen time under CleanMyTV Home, Layout, Sections."), color = ST_MUTED, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
        return
    }
    val today = s.week.last()
    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(150.dp)) {
            Text(tr("Screen time today"), color = ST_MUTED, fontSize = 13.sp, maxLines = 1, softWrap = false)
            Text(ScreenTime.words(today.total), color = ST_TEXT, fontSize = 26.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
            s.week.getOrNull(s.week.size - 2)?.let { y ->
                Text(tr("Yesterday: {time}", "time" to ScreenTime.words(y.total)), color = ST_MUTED, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        UseBars(s.week.map { it.total }, s.week.map { it.day.format(WEEKDAY) }, accent, Modifier.width(150.dp).fillMaxHeight(), scaleLabel = false, labelSize = 11f)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (s.top.isEmpty()) Text(tr("Nothing on yet today."), color = ST_MUTED, fontSize = 13.sp)
            s.top.forEach { AppLine(it, iconSize = 22.dp, fontSize = 14f) }
        }
    }
}

@Composable
private fun AppLine(a: UsedApp, iconSize: Dp, fontSize: Float, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(iconSize)) { a.icon?.let { Image(it, null, Modifier.fillMaxSize()) } }
        Spacer(Modifier.width(10.dp))
        Text(a.label, color = ST_TEXT, fontSize = fontSize.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Text(ScreenTime.words(a.ms), color = ST_MUTED, fontSize = fontSize.sp, maxLines = 1, softWrap = false)
    }
}

/**
 * The dashboard: Today, the last 7 days or the last 30. Each shows the total (a daily average over several
 * days), the bars by hour or by day, when the TV is usually on, and the apps longest first.
 */
@Composable
private fun ScreenTimeDashboard() {
    val ctx = LocalContext.current
    val cfg by ConfigStore.config.collectAsState()
    val accent = Color(cfg.accent)
    val ground = Color(cfg.background)
    var days by remember { mutableStateOf<List<DayUse>?>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    // Two periods back, for the comparison with the one before.
    LaunchedEffect(Unit) { days = withContext(Dispatchers.IO) { ScreenTime.days(ctx, LocalDate.now().minusDays(59)) }; loaded = true }
    var period by rememberSaveable { mutableStateOf(1) }
    val first = remember { FocusRequester() }
    LaunchedEffect(loaded) { if (loaded) runCatching { first.requestFocus() } }

    Box(Modifier.fillMaxSize().background(ground).padding(horizontal = 48.dp, vertical = 27.dp)) {
        val all = days
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Screen time"), color = ST_TEXT, fontSize = 26.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                listOf(1 to tr("Today"), 7 to tr("Last 7 days"), 30 to tr("Last 30 days")).forEachIndexed { i, (n, label) ->
                    val on = period == n
                    Surface(
                        onClick = { period = n },
                        modifier = Modifier.padding(start = 8.dp).then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (on) accent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f),
                            focusedContainerColor = accent,
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    ) {
                        Text(label, color = ST_TEXT, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp))
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            when {
                !loaded -> {}
                all == null -> Text(tr("Screen time needs usage access. In CleanMyTV on a computer, tick Screen time under CleanMyTV Home, Layout, Sections."), color = ST_MUTED, fontSize = 17.sp)
                else -> Period(all, period, accent, ground)
            }
        }
    }
}

@Composable
private fun Period(all: List<DayUse>, n: Int, accent: Color, ground: Color) {
    val ctx = LocalContext.current
    val cur = all.takeLast(n)
    val before = all.dropLast(n).takeLast(n)
    // Days before the first one with anything on are days before Home could see: they don't pull the average down.
    val firstSeen = all.firstOrNull { it.total > 0 }?.day
    val counted = cur.count { firstSeen != null && !it.day.isBefore(firstSeen) }.coerceAtLeast(1)
    val total = cur.sumOf { it.total }
    val apps = remember(all, n) { ScreenTimeApps.top(ctx, merged(cur)) }
    val hours = remember(ctx) { hourLabels(ctx) }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(0.58f).fillMaxHeight()) {
            if (n == 1) {
                Text(ScreenTime.words(total), color = ST_TEXT, fontSize = 40.sp, fontWeight = FontWeight.Medium)
                before.lastOrNull()?.let { Text(tr("Yesterday: {time}", "time" to ScreenTime.words(it.total)), color = ST_MUTED, fontSize = 15.sp) }
            } else {
                Text(tr("{time} a day", "time" to ScreenTime.words(total / counted)), color = ST_TEXT, fontSize = 40.sp, fontWeight = FontWeight.Medium)
                val prevSeen = before.count { firstSeen != null && !it.day.isBefore(firstSeen) }
                val line = listOfNotNull(
                    tr("{time} in all", "time" to ScreenTime.words(total)),
                    if (prevSeen > 0) tr("the {n} days before: {time} a day", "n" to n, "time" to ScreenTime.words(before.sumOf { it.total } / prevSeen)) else null,
                ).joinToString(" · ")
                Text(line, color = ST_MUTED, fontSize = 15.sp)
            }
            Spacer(Modifier.height(16.dp))
            val panel = Modifier.fillMaxWidth().background(lerp(ground, Color.White, 0.05f), RoundedCornerShape(14.dp)).padding(14.dp)
            if (n == 1) {
                Text(tr("By hour"), color = ST_MUTED, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
                UseBars(cur.single().hours.toList(), hours, accent, panel.weight(1f), mark = java.time.LocalTime.now().hour, span = 6)
            } else {
                Text(tr("By day"), color = ST_MUTED, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
                val labels = cur.mapIndexed { i, d -> if (n == 7) d.day.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())) else if ((cur.size - 1 - i) % 5 == 0) d.day.dayOfMonth.toString() else "" }
                UseBars(cur.map { it.total }, labels, accent, panel.weight(1f))
                Spacer(Modifier.height(12.dp))
                Text(tr("When the TV is on, on average"), color = ST_MUTED, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
                val avg = (0 until 24).map { h -> cur.sumOf { it.hours[h] } / counted }
                UseBars(avg, hours, accent, panel.weight(0.8f), mark = -1, span = 6)
            }
        }
        Spacer(Modifier.width(28.dp))
        Column(Modifier.weight(0.42f).fillMaxHeight()) {
            Text(tr("Apps"), color = ST_MUTED, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
            if (apps.isEmpty()) Text(tr("Nothing on yet."), color = ST_MUTED, fontSize = 15.sp)
            val most = apps.firstOrNull()?.ms ?: 1L
            LazyColumn(Modifier.fillMaxSize()) {
                items(apps, key = { it.pkg }) { a -> AppRow(a, most, accent) }
            }
        }
    }
}

/** A focusable row, so the list scrolls with the remote. The bar is the app's time against the longest. */
@Composable
private fun AppRow(a: UsedApp, most: Long, accent: Color) {
    Surface(
        onClick = {},
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.White.copy(alpha = 0.1f)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            AppLine(a, iconSize = 28.dp, fontSize = 15f)
            Box(Modifier.padding(start = 38.dp, top = 5.dp).fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(2.dp))) {
                Box(Modifier.fillMaxWidth((a.ms.toFloat() / most).coerceIn(0.02f, 1f)).fillMaxHeight().background(if (accent.luminance() < 0.08f) ST_MUTED else accent, RoundedCornerShape(2.dp)))
            }
        }
    }
}

/** Opened from the Screen time section, its top bar icon or a remote button. Back closes it. */
class ScreenTimeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConfigStore.load(this)
        setContent {
            val cfg by ConfigStore.config.collectAsState()
            HomeFont(cfg.font) { ScreenTimeDashboard() }
        }
    }
}
