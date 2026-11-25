package io.github.wingedpanther.cleanmytv.home

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.DateFormat
import java.util.Date

/** A line left on the TV from the phone page or the desktop app. Home shows it until someone presses OK. */
object TvNote {
    data class Note(val text: String, val at: Long)
    const val MAX = 200

    private val _note = MutableStateFlow<Note?>(null)
    val note: StateFlow<Note?> = _note

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("note", Context.MODE_PRIVATE)

    /** Straight from storage: the provider can be asked before Home has drawn anything. */
    fun current(ctx: Context): Note? {
        val p = prefs(ctx)
        return p.getString("text", null)?.takeIf { it.isNotBlank() }?.let { Note(it, p.getLong("at", 0L)) }
    }

    fun load(ctx: Context) { _note.value = current(ctx) }

    fun set(ctx: Context, text: String) {
        val t = text.trim().take(MAX)
        if (t.isEmpty()) return clear(ctx)
        val n = Note(t, System.currentTimeMillis())
        prefs(ctx).edit().putString("text", n.text).putLong("at", n.at).apply()
        _note.value = n
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
        _note.value = null
    }
}

/** The note over Home. Only OK takes it down: Back leaves it for whoever comes next. */
@Composable
fun NoteDialog(note: TvNote.Note, accent: Color, onOk: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(note.at))
    val left = if (DateUtils.isToday(note.at)) tr("Left at {time}", "time" to time)
    else tr("Left on {day} at {time}", "day" to DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(note.at)), "time" to time)
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            Column(Modifier.width(620.dp).background(HomeColours.panel.copy(alpha = 0.95f), RoundedCornerShape(20.dp)).padding(26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("A note"), color = accent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(note.text, color = Color(0xFFF0EBE5), fontSize = 28.sp, lineHeight = 36.sp)
                Text(left, color = Color(0xFFD7CFC4), fontSize = 14.sp)
                UpdateButton(tr("OK"), accent, Modifier.focusRequester(first).padding(top = 4.dp)) { onOk() }
            }
        }
    }
}
