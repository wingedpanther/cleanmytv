package io.github.wingedpanther.cleanmytv.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * A row cut by the top or bottom of a scrolling list fades out, and comes back once it is all on screen, so the
 * list shows whole rows or none. The focused row is always whole: scrolling brings it in first.
 */
internal fun Modifier.whole(shown: Boolean): Modifier = composed {
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(140), label = "whole")
    graphicsLayer { alpha = a }
}

/** The keys of the items [list] shows from edge to edge, content padding included. */
@Composable
internal fun wholeItems(list: LazyListState): State<Set<Any>> = remember(list) {
    derivedStateOf(structuralEqualityPolicy()) {
        val i = list.layoutInfo
        val top = -i.beforeContentPadding
        i.visibleItemsInfo.filter { it.offset >= top && it.offset + it.size <= top + i.viewportSize.height }.mapTo(HashSet()) { it.key }
    }
}

@Composable
internal fun wholeItems(grid: LazyGridState): State<Set<Any>> = remember(grid) {
    derivedStateOf(structuralEqualityPolicy()) {
        val i = grid.layoutInfo
        val top = -i.beforeContentPadding
        i.visibleItemsInfo.filter { it.offset.y >= top && it.offset.y + it.size.height <= top + i.viewportSize.height }.mapTo(HashSet()) { it.key }
    }
}
