package com.thescarletcoder.lyrics

/** One lyric line. [timeMs] is null for plain (unsynced) lyrics. */
data class LyricLine(val timeMs: Long?, val text: String)

/** Parses LRC text (as LRCLIB returns it) into time-ordered lines. */
object Lrc {
    private val stamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val wordStamp = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")
    private val offsetTag = Regex("""^\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

    fun parseSynced(lrc: String): List<LyricLine> {
        var offset = 0L
        val out = mutableListOf<LyricLine>()
        lrc.lineSequence().forEach { raw ->
            val line = raw.trim()
            offsetTag.find(line)?.let { offset = it.groupValues[1].toLong(); return@forEach }
            // A line can carry several timestamps ("[00:12.00][01:30.00]chorus").
            var rest = line
            val times = mutableListOf<Long>()
            while (true) {
                val m = stamp.matchAt(rest, 0) ?: break
                times += toMs(m)
                rest = rest.substring(m.range.last + 1)
            }
            if (times.isEmpty()) return@forEach
            // Enhanced LRC word timings ("<00:12.34>") are dropped; lines are highlighted whole.
            val text = rest.replace(wordStamp, "").replace(Regex("\\s+"), " ").trim()
            // A positive LRC offset means lyrics should appear earlier.
            times.forEach { out += LyricLine((it - offset).coerceAtLeast(0), text) }
        }
        return out.sortedBy { it.timeMs }
    }

    fun parsePlain(text: String): List<LyricLine> = text.lines().map { LyricLine(null, it.trim()) }

    private fun toMs(m: MatchResult): Long {
        val (min, sec, frac) = m.destructured
        val fracMs = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
        return min.toLong() * 60_000 + sec.toLong() * 1000 + fracMs
    }

    /** Index of the line being sung at [positionMs], or -1 before the first line. */
    fun indexAt(lines: List<LyricLine>, positionMs: Long): Int {
        var lo = 0; var hi = lines.size - 1; var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if ((lines[mid].timeMs ?: Long.MAX_VALUE) <= positionMs) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }
}
