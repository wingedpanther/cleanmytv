package io.github.wingedpanther.cleanmytv.home

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Air mice and plain mice (D#101): pointing at a tile moves the focus there, a click opens it. TV surfaces
 * only answer the remote's OK button on their own. Goes before the surface's own modifiers.
 */
fun Modifier.pointer(onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier = composed {
    val focus = remember { FocusRequester() }
    val click by rememberUpdatedState(onClick)
    val long by rememberUpdatedState(onLongClick)
    focusRequester(focus)
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) if (awaitPointerEvent().type == PointerEventType.Enter) runCatching { focus.requestFocus() }
            }
        }
        .pointerInput(Unit) { detectTapGestures(onTap = { click() }, onLongPress = { long?.invoke() }) }
}
