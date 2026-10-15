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
    /** Harder than this (leading zero bits) is not a real LRCLIB challenge: refused rather than run for hours. */
    const val MAX_ZERO_BITS = 32

    /** How long the device works on one challenge before giving up. */
    const val MAX_MS = 180_000L

    fun solve(
        prefix: String, targetHex: String, cancelled: () -> Boolean = { false },
        now: () -> Long = System::currentTimeMillis,
    ): String? {
        val target = hex(targetHex) ?: return null
        if (zeroBits(target) > MAX_ZERO_BITS) return null
        val until = now() + MAX_MS
        val md = MessageDigest.getInstance("SHA-256")
        val p = prefix.toByteArray(Charsets.UTF_8)
        var n = 0L
        while (true) {
            if (n and 0xFFF == 0L && (cancelled() || now() > until)) return null
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

    fun zeroBits(target: ByteArray): Int {
        var bits = 0
        for (b in target) {
            val v = b.toInt() and 0xFF
            if (v == 0) { bits += 8; continue }
            return bits + Integer.numberOfLeadingZeros(v) - 24
        }
        return bits
    }

    private fun hex(s: String): ByteArray? {
        if (s.length != 64 || s.any { it.digitToIntOrNull(16) == null }) return null
        return ByteArray(32) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}

/**
 * Share your fix (0.5.0, owner Q23): what goes to LRCLIB, built here so the preview shows exactly what's sent. The song
 * as the fixed version names it (owner, 0.5.1 Q47): LRCLIB files a publish under the record with the same title, artist
 * and album (ignoring case) and a length within 2 s, so the fix replaces that version instead of adding a near-duplicate.
 * Then the version's lyrics with the user's timing.
 */
object ShareFix {
    /**
     * A version LRCLIB can be told about usefully (owner, finalize 0.5.1 Q5): one with a title and an artist. A fix shared
     * to a nameless version would land on it, but no lookup ever finds it. A missing album alone is fine.
     */
    fun shareable(c: Candidate) = c.track.isNotBlank() && c.artist.isNotBlank()

    /** "[01:02.35] words": the lines with their (user-timed) times; blank lines keep their time (instrumental gaps). */
    fun lrc(timed: List<LyricLine>): String = timed.filter { it.timeMs != null }.joinToString("\n") { l ->
        // Whole hundredths first, so 59.995 s is [01:00.00], never [00:60.00].
        val cs = (l.timeMs!!.coerceAtLeast(0) + 5) / 10
        String.format(Locale.ROOT, "[%02d:%02d.%02d] %s", cs / 6000, (cs % 6000) / 100, cs % 100, l.text).trimEnd()
    }

    /** [skipped]: lines were marked "not in this song" (0.5.1): the plain lyrics leave them out too. */
    fun body(c: Candidate, timed: List<LyricLine>, skipped: Boolean = false): JSONObject = JSONObject()
        .put("trackName", c.track)
        .put("artistName", c.artist)
        .put("albumName", c.album)
        .put("duration", c.durationSec)   // as stored, even 0, so it still matches that version
        .put("plainLyrics", c.plain?.takeIf { !skipped } ?: timed.joinToString("\n") { it.text })
        .put("syncedLyrics", lrc(timed))
}
