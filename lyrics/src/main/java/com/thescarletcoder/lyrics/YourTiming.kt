package com.thescarletcoder.lyrics

/**
 * One tapped line (0.5.0, owner Q22): [lineMs] is the line's time in the record, [deltaMs] how much further than the
 * song's base nudge this line moves (positive: the lyrics come earlier, like the nudge).
 */
data class Anchor(val lineMs: Long, val deltaMs: Long)

/** Anchors as stored, in the cache and in the Library backup: [[lineMs, deltaMs], ...]. */
internal object AnchorJson {
    fun write(anchors: List<Anchor>) = org.json.JSONArray(anchors.map { org.json.JSONArray().put(it.lineMs).put(it.deltaMs) })

    /** One stored pair, or null when it isn't two numbers: a damaged pair is left out on its own. */
    fun pair(p: org.json.JSONArray?): Anchor? =
        p?.takeIf { it.length() == 2 && it.opt(0) is Number && it.opt(1) is Number }?.let { Anchor(it.getLong(0), it.getLong(1)) }
}

/**
 * The user's timing for a song: a base nudge (Earlier and Later) plus anchors from tapping lines. It wins over the
 * record's own timing. Lines between two anchors follow both, in proportion; before the first and after the last they
 * follow the nearest one. Where two anchors differ a lot (a section cut from the record or the song), the change
 * happens at once, at the longest pause between them, instead (owner, 0.5.1 Q53). Pure, so it is unit tested.
 */
object YourTiming {
    /** The furthest Earlier and Later can move the lyrics either way. */
    const val LIMIT_MS = 10_000L

    /** The furthest a tapped line can move either way (owner, 0.5.1 Q53: up to the song's length; an hour covers any song). */
    const val ANCHOR_LIMIT_MS = 3_600_000L

    /** Two anchors this far apart in their moves mean a cut, not drift: the change is one jump at the longest pause. */
    const val STEP_MS = 2_000L

    /**
     * How much further than the base nudge the line at [lineMs] moves. [times]: the record's line times (sorted), so a
     * big change between two anchors lands at the longest pause between them; without them it's spread evenly.
     */
    fun deltaAt(anchors: List<Anchor>, lineMs: Long, times: List<Long> = emptyList()): Long =
        sortedDelta(anchors.sortedBy { it.lineMs }, lineMs, times)

    /** [deltaAt] for anchors already sorted by line, so [apply] sorts them once per song, not once per line. */
    private fun sortedDelta(a: List<Anchor>, lineMs: Long, times: List<Long>, steps: MutableMap<Long, Long?>? = null): Long {
        if (a.isEmpty()) return 0
        if (lineMs <= a.first().lineMs) return a.first().deltaMs
        if (lineMs >= a.last().lineMs) return a.last().deltaMs
        val i = a.indexOfLast { it.lineMs <= lineMs }
        val l = a[i]; val r = a[i + 1]
        if (Math.abs(r.deltaMs - l.deltaMs) > STEP_MS) {
            val step = if (steps == null) stepAt(times, l.lineMs, r.lineMs) else steps.getOrPut(l.lineMs) { stepAt(times, l.lineMs, r.lineMs) }
            step?.let { return if (lineMs < it) l.deltaMs else r.deltaMs }
        }
        val f = (lineMs - l.lineMs).toDouble() / (r.lineMs - l.lineMs)
        return Math.round(l.deltaMs + (r.deltaMs - l.deltaMs) * f)
    }

    /** The first line after the longest pause between the lines at [fromMs] and [toMs] (null without lines between). */
    fun stepAt(times: List<Long>, fromMs: Long, toMs: Long): Long? {
        // [times] is sorted: find the stretch by binary search, not by scanning every line.
        val from = times.binarySearch(fromMs).let { if (it < 0) -it - 1 else it }.let { i -> var j = i; while (j > 0 && times[j - 1] == fromMs) j--; j }
        var to = from
        while (to < times.size && times[to] <= toMs) to++
        val span = times.subList(from, to)
        if (span.size < 2) return null
        return span.zipWithNext().maxByOrNull { (x, y) -> y - x }!!.second
    }

    /**
     * Earlier and Later (owner, 0.5.1 Q53 and finalize A2): within 10 s either way. A bigger move from Line up now can
     * be stepped back toward zero, never further out, and never more than 10 s past zero on the other side.
     */
    fun nudged(currentMs: Long, deltaMs: Long): Long =
        (currentMs + deltaMs).coerceIn(minOf(-LIMIT_MS, currentMs), maxOf(LIMIT_MS, currentMs))

    /** The whole move for the line at [lineMs]: the base nudge plus its share of the anchors, within the limit. */
    fun offsetAt(baseMs: Long, anchors: List<Anchor>, lineMs: Long, times: List<Long> = emptyList()): Long =
        offset(baseMs, anchors.sortedBy { it.lineMs }, lineMs, times, null)

    private fun offset(baseMs: Long, sorted: List<Anchor>, lineMs: Long, times: List<Long>, steps: MutableMap<Long, Long?>?): Long =
        (baseMs + sortedDelta(sorted, lineMs, times, steps)).coerceIn(-ANCHOR_LIMIT_MS, ANCHOR_LIMIT_MS)

    /**
     * The lines with the user's timing applied: each line's time minus its move. A line never comes before the one
     * above it, so the order on screen never changes even when two anchors pull hard against each other.
     */
    fun apply(lines: List<LyricLine>, baseMs: Long, anchors: List<Anchor>): List<LyricLine> {
        if (baseMs == 0L && anchors.isEmpty()) return lines
        val times = lines.mapNotNull { it.timeMs }.sorted()
        val sorted = anchors.sortedBy { it.lineMs }
        // Each jump point worked out once per pair of anchors, not once per line (finalize 0.5.1 R3: a huge record).
        val steps = HashMap<Long, Long?>()
        var floor = Long.MIN_VALUE
        return lines.map { line ->
            val t = line.timeMs ?: return@map line
            val offset = offset(baseMs, sorted, t, times, steps)
            val moved = maxOf((t - offset).coerceAtLeast(0), floor)
            floor = moved
            line.copy(timeMs = moved)
        }
    }

    /** The anchors with the line at [lineMs] set to move [offsetMs] in all (replacing an earlier tap on that line). */
    fun withAnchor(anchors: List<Anchor>, baseMs: Long, lineMs: Long, offsetMs: Long): List<Anchor> =
        (anchors.filter { it.lineMs != lineMs } + Anchor(lineMs, offsetMs.coerceIn(-ANCHOR_LIMIT_MS, ANCHOR_LIMIT_MS) - baseMs)).sortedBy { it.lineMs }

    /**
     * This line only (owner, 2026-11-05): the line at [lineMs] moves [offsetMs] in all, and the lines either side of it
     * stay where they show now, so a line sung a little early or late doesn't pull the rest of the song with it. The
     * neighbours are pinned with their current move (a tap of their own stays as it is). [times]: the record's line
     * times as on screen (sorted); lines sharing a time count as one.
     */
    fun withOnlyLine(anchors: List<Anchor>, baseMs: Long, lineMs: Long, offsetMs: Long, times: List<Long>): List<Anchor> {
        val sorted = anchors.sortedBy { it.lineMs }
        val neighbours = listOfNotNull(times.lastOrNull { it < lineMs }, times.firstOrNull { it > lineMs })
        val pins = neighbours.filter { n -> sorted.none { it.lineMs == n } }.map { Anchor(it, sortedDelta(sorted, it, times)) }
        return withAnchor(sorted + pins, baseMs, lineMs, offsetMs)
    }

    /**
     * The move for a line from a tap (owner Q19, Q22): the line at [lineMs] in the record was heard at [tapPositionMs]
     * of the song, less [reactionMs] (people tap a little late). [outputDelayMs] is the headphones' own delay, applied
     * separately, so it's taken out here.
     */
    fun offsetFromTap(lineMs: Long, tapPositionMs: Long, outputDelayMs: Long, reactionMs: Long): Long =
        (lineMs - (tapPositionMs - reactionMs) - outputDelayMs).coerceIn(-ANCHOR_LIMIT_MS, ANCHOR_LIMIT_MS)

    /** The base nudge that gives the line at [lineMs] a move of [offsetMs] in all, keeping the anchors' shape (Line up now). */
    fun baseFor(anchors: List<Anchor>, lineMs: Long, offsetMs: Long, times: List<Long> = emptyList()): Long =
        (offsetMs.coerceIn(-ANCHOR_LIMIT_MS, ANCHOR_LIMIT_MS) - deltaAt(anchors, lineMs, times)).coerceIn(-ANCHOR_LIMIT_MS, ANCHOR_LIMIT_MS)

    /**
     * The line to tap next: the first line with words that the lyrics, as timed now ([timed]), haven't reached at
     * [positionMs]. If the timing is late it's the line being sung; if early, the coming one. Null when none is left.
     */
    fun nextLine(timed: List<LyricLine>, positionMs: Long): Int? =
        timed.indices.firstOrNull { i -> timed[i].text.isNotBlank() && (timed[i].timeMs ?: return@firstOrNull false) > positionMs }

    /**
     * The first line to tap: one the lyrics put at least [LIMIT_MS] ahead. A nudge never puts the timing off by more than
     * that, so this line can't have been sung yet; the user waits for it and taps when it comes. After a cut in the song
     * it may have gone already: "Missed it" moves on to the next line (0.5.1).
     */
    fun firstTarget(timed: List<LyricLine>, positionMs: Long): Int? = nextLine(timed, positionMs + LIMIT_MS)

    /** After a tap on [tapped] (Fix lines): the next line with words. */
    fun afterTap(timed: List<LyricLine>, tapped: Int): Int? =
        (tapped + 1 until timed.size).firstOrNull { timed[it].text.isNotBlank() && timed[it].timeMs != null }
}
