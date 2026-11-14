package io.github.wingedpanther.cleanmytv.home

/**
 * Moving an app from the remote (long press, Move): the arrow keys shift it through the dock and All apps,
 * or along its row. What is on screen is what moves; the saved lists keep everything else they hold.
 */
object Reorder {
    /** [seq] with [pkg] moved [by] places, stopping at either end. */
    fun move(seq: List<String>, pkg: String, by: Int): List<String> {
        val from = seq.indexOf(pkg)
        if (from < 0) return seq
        val to = (from + by).coerceIn(0, seq.lastIndex)
        if (to == from) return seq
        return seq.toMutableList().apply { removeAt(from); add(to, pkg) }
    }

    /**
     * The new saved order once [pkg] moved in [shown], the dock and All apps as they appear. Every app on
     * screen gets its place, so a new app no longer jumps between them by name; apps in folders keep theirs.
     */
    fun order(saved: List<String>, shown: List<String>, pkg: String, by: Int): List<String> {
        val moved = move(shown, pkg, by)
        return moved + saved.filter { it !in moved }
    }

    /** A row's apps once [pkg] moved among the [shown] ones; apps the row holds but doesn't show stay at the end. */
    fun row(saved: List<String>, shown: List<String>, pkg: String, by: Int): List<String> = order(saved, shown, pkg, by)
}
