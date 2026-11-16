package io.github.wingedpanther.cleanmytv.home.customization.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay

/**
 * The setting a search picked. The control with that name scrolls into view and takes focus once its
 * section opens; sections without a matching control just open, as before.
 */
object Find {
    @Volatile var target: String? = null

    /** "Layout: Shelf, Split or Rail" finds the "Layout" control; case and spacing don't matter. */
    fun matches(target: String?, label: String): Boolean {
        val t = target?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return false
        val l = label.trim().lowercase().takeIf { it.isNotEmpty() } ?: return false
        return t == l || t.startsWith("$l:") || l.startsWith(t)
    }
}

/** Marks a control as the one a search for [label] lands on. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.findable(label: String): Modifier = composed {
    val focus = remember { FocusRequester() }
    val bring = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) {
        if (Find.target == null) return@LaunchedEffect
        // After the editor's own focus move into the section (150 ms).
        delay(300)
        if (!Find.matches(Find.target, label)) return@LaunchedEffect
        Find.target = null
        runCatching { bring.bringIntoView(); focus.requestFocus() }
    }
    bringIntoViewRequester(bring).focusRequester(focus)
}
