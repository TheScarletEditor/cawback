package com.thescarletcoder.lyrics

/**
 * One tapped line (0.5.0, owner Q22): [lineMs] is the line's time in the record, [deltaMs] how much further than the
 * song's base nudge this line moves (positive: the lyrics come earlier, like the nudge).
 */
data class Anchor(val lineMs: Long, val deltaMs: Long)

/**
 * The user's timing for a song: a base nudge (Earlier and Later) plus anchors from tapping lines. It wins over the
 * record's own timing. Lines between two anchors follow both, in proportion; before the first and after the last they
 * follow the nearest one. Pure, so it is unit tested.
 */
object YourTiming {
    /** The furthest a line can move either way, as for the nudge. */
    const val LIMIT_MS = 10_000L

    /** How much further than the base nudge the line at [lineMs] moves. */
    fun deltaAt(anchors: List<Anchor>, lineMs: Long): Long {
        if (anchors.isEmpty()) return 0
        val a = anchors.sortedBy { it.lineMs }
        if (lineMs <= a.first().lineMs) return a.first().deltaMs
        if (lineMs >= a.last().lineMs) return a.last().deltaMs
        val i = a.indexOfLast { it.lineMs <= lineMs }
        val l = a[i]; val r = a[i + 1]
        val f = (lineMs - l.lineMs).toDouble() / (r.lineMs - l.lineMs)
        return Math.round(l.deltaMs + (r.deltaMs - l.deltaMs) * f)
    }

    /** The whole move for the line at [lineMs]: the base nudge plus its share of the anchors, within the limit. */
    fun offsetAt(baseMs: Long, anchors: List<Anchor>, lineMs: Long): Long = (baseMs + deltaAt(anchors, lineMs)).coerceIn(-LIMIT_MS, LIMIT_MS)

    /**
     * The lines with the user's timing applied: each line's time minus its move. A line never comes before the one
     * above it, so the order on screen never changes even when two anchors pull hard against each other.
     */
    fun apply(lines: List<LyricLine>, baseMs: Long, anchors: List<Anchor>): List<LyricLine> {
        if (baseMs == 0L && anchors.isEmpty()) return lines
        var floor = Long.MIN_VALUE
        return lines.map { line ->
            val t = line.timeMs ?: return@map line
            val moved = maxOf((t - offsetAt(baseMs, anchors, t)).coerceAtLeast(0), floor)
            floor = moved
            line.copy(timeMs = moved)
        }
    }

    /** The anchors with the line at [lineMs] set to move [offsetMs] in all (replacing an earlier tap on that line). */
    fun withAnchor(anchors: List<Anchor>, baseMs: Long, lineMs: Long, offsetMs: Long): List<Anchor> =
        (anchors.filter { it.lineMs != lineMs } + Anchor(lineMs, offsetMs.coerceIn(-LIMIT_MS, LIMIT_MS) - baseMs)).sortedBy { it.lineMs }

    /**
     * The move for a line from a tap (owner Q19, Q22): the line at [lineMs] in the record was heard at [tapPositionMs]
     * of the song, less [reactionMs] (people tap a little late). [outputDelayMs] is the headphones' own delay, applied
     * separately, so it's taken out here.
     */
    fun offsetFromTap(lineMs: Long, tapPositionMs: Long, outputDelayMs: Long, reactionMs: Long): Long =
        (lineMs - (tapPositionMs - reactionMs) - outputDelayMs).coerceIn(-LIMIT_MS, LIMIT_MS)

    /** The base nudge that gives the line at [lineMs] a move of [offsetMs] in all, keeping the anchors' shape (Line up now). */
    fun baseFor(anchors: List<Anchor>, lineMs: Long, offsetMs: Long): Long =
        (offsetMs.coerceIn(-LIMIT_MS, LIMIT_MS) - deltaAt(anchors, lineMs)).coerceIn(-LIMIT_MS, LIMIT_MS)

    /**
     * The line to tap next: the first line with words that the lyrics, as timed now ([timed]), haven't reached at
     * [positionMs]. If the timing is late it's the line being sung; if early, the coming one. Null when none is left.
     */
    fun nextLine(timed: List<LyricLine>, positionMs: Long): Int? =
        timed.indices.firstOrNull { i -> timed[i].text.isNotBlank() && (timed[i].timeMs ?: return@firstOrNull false) > positionMs }

    /**
     * The first line to tap: one the lyrics put at least [LIMIT_MS] ahead. The timing is never off by more than that,
     * so this line can't have been sung yet however wrong it is; the user waits for it and taps when it comes.
     */
    fun firstTarget(timed: List<LyricLine>, positionMs: Long): Int? = nextLine(timed, positionMs + LIMIT_MS)

    /** After a tap on [tapped] (Fix lines): the next line with words. */
    fun afterTap(timed: List<LyricLine>, tapped: Int): Int? =
        (tapped + 1 until timed.size).firstOrNull { timed[it].text.isNotBlank() && timed[it].timeMs != null }

    /** The target stays until it's tapped, unless the lyrics put it more than [LIMIT_MS] behind: then it was missed. */
    fun keepTarget(timed: List<LyricLine>, target: Int, positionMs: Long): Int? {
        val t = timed.getOrNull(target)?.timeMs ?: return null
        return if (t + LIMIT_MS < positionMs) nextLine(timed, positionMs - LIMIT_MS) else target
    }
}
