package com.thescarletcoder.lyrics

import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

/**
 * LRCLIB's publish token (their own scheme, checked against their server code): a challenge gives a prefix and a
 * target, and the answer is a nonce whose SHA-256 of prefix + nonce is no greater than the target, byte by byte. It
 * costs CPU time on purpose (spam protection), so it runs off the main thread and can be cancelled. Pure.
 */
object ProofOfWork {
    fun solve(prefix: String, targetHex: String, cancelled: () -> Boolean = { false }): String? {
        val target = hex(targetHex) ?: return null
        val md = MessageDigest.getInstance("SHA-256")
        val p = prefix.toByteArray(Charsets.UTF_8)
        var n = 0L
        while (true) {
            if (n and 0xFFF == 0L && cancelled()) return null
            md.reset(); md.update(p)
            val nonce = n.toString()
            if (atMost(md.digest(nonce.toByteArray(Charsets.UTF_8)), target)) return nonce
            n++
        }
    }

    /** The server's own check, for tests and a last look before sending. */
    fun verify(prefix: String, targetHex: String, nonce: String): Boolean {
        val target = hex(targetHex) ?: return false
        return atMost(MessageDigest.getInstance("SHA-256").digest((prefix + nonce).toByteArray(Charsets.UTF_8)), target)
    }

    /** Unsigned, byte by byte, equal allowed: as LRCLIB compares. */
    fun atMost(hash: ByteArray, target: ByteArray): Boolean {
        if (hash.size != target.size) return false
        for (i in hash.indices) {
            val h = hash[i].toInt() and 0xFF; val t = target[i].toInt() and 0xFF
            if (h < t) return true
            if (h > t) return false
        }
        return true
    }

    private fun hex(s: String): ByteArray? {
        if (s.length != 64 || s.any { it.digitToIntOrNull(16) == null }) return null
        return ByteArray(32) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}

/**
 * Share your fix (0.5.0, owner Q23): what goes to LRCLIB, built here so the preview shows exactly what's sent. The song
 * as the player names it (so others with the same player get it), and the version's lyrics with the user's timing.
 */
object ShareFix {
    /** "[01:02.35] words": the lines with their (user-timed) times; blank lines keep their time (instrumental gaps). */
    fun lrc(timed: List<LyricLine>): String = timed.filter { it.timeMs != null }.joinToString("\n") { l ->
        val ms = l.timeMs!!.coerceAtLeast(0)
        String.format(Locale.ROOT, "[%02d:%05.2f] %s", ms / 60_000, (ms % 60_000) / 1000.0, l.text).trimEnd()
    }

    fun body(t: Track, c: Candidate, timed: List<LyricLine>): JSONObject = JSONObject()
        .put("trackName", t.title)
        .put("artistName", t.artist)
        .put("albumName", t.album?.ifBlank { null } ?: c.album)
        .put("duration", (t.durationMs?.takeIf { it > 0 }?.div(1000.0)) ?: c.durationSec)
        .put("plainLyrics", c.plain ?: timed.joinToString("\n") { it.text })
        .put("syncedLyrics", lrc(timed))
}
