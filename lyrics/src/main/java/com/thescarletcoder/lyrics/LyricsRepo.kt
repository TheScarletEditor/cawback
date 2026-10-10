package com.thescarletcoder.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface Shown {
    data object NoAccess : Shown
    data object NothingPlaying : Shown
    data object NotFound : Shown
    data object Offline : Shown
    data class Found(val candidate: Candidate, val lines: List<LyricLine>, val synced: Boolean, val pinned: Boolean, val offsetMs: Long) : Shown
}

/** Cache first, then LRCLIB: exact lookup, then a cleaned-up title, then a search. */
class LyricsRepo(private val store: LyricsStore) {
    private val retryMissAfterMs = 24 * 3_600_000L

    companion object {
        /** When matching last improved (2026-10-08 17:00 PDT): misses remembered before this are tried again. */
        private const val MATCHING_CHANGED_AT = 1_791_504_000_000L

        /** Waits before the second and third tries. */
        val RETRY_DELAYS_MS = listOf(1_500L, 4_000L)

        /**
         * Run [block], trying again after each delay if the network fails. "Not found" is an answer, not a failure,
         * so it is never retried. Pure apart from the waits, so it is unit tested.
         */
        suspend fun <T> withRetries(delaysMs: List<Long>, block: () -> T): T {
            for (wait in delaysMs) {
                try {
                    return block()
                } catch (e: LrcLib.NotFound) {
                    throw e
                } catch (_: IOException) {
                    delay(wait)
                }
            }
            return block()
        }
    }

    suspend fun load(t: Track): Shown = withContext(Dispatchers.IO) {
        val cached = store.load(t.key)
        if (cached != null) {
            if (cached.candidate != null) return@withContext found(cached.candidate, cached.pinned, cached.offsetMs)
            // A remembered miss counts only if it was found with today's matching rules (title-only fallback added).
            if (System.currentTimeMillis() - cached.fetchedAt < retryMissAfterMs && cached.fetchedAt > MATCHING_CHANGED_AT) return@withContext Shown.NotFound
        }
        try {
            // A dropped connection gets two more tries before Lyrics says it can't reach LRCLIB (Sam, 2026-10-09:
            // on the phone the connection dropped often, sometimes mid-song).
            val c = withRetries(RETRY_DELAYS_MS) { lookup(t) }
            store.save(t.key, LyricsStore.Entry(c, pinned = false, offsetMs = cached?.offsetMs ?: 0, fetchedAt = System.currentTimeMillis()))
            if (c == null) Shown.NotFound else found(c, false, cached?.offsetMs ?: 0)
        } catch (_: IOException) {
            Shown.Offline
        } catch (_: org.json.JSONException) {
            Shown.Offline
        }
    }

    private fun lookup(t: Track): Candidate? {
        val dur = t.durationMs?.let { it / 1000.0 }
        exact { LrcLib.get(t.title, t.artist, t.album, dur) }?.let { return it }
        val clean = Matching.cleanTitle(t.title)
        val artist = t.artist.split(",", " & ", " feat", " ft.").first().trim()
        exact { LrcLib.get(clean, artist, null, dur) }?.let { return it }
        Matching.best(LrcLib.search(clean, artist), dur)?.let { return it }
        // Last try: the title alone (e.g. "Operation Pyrite" is filed under its label, not the singer Spotify shows).
        // Only with a known length, so Matching.best can insist on a match within 10 seconds.
        return if (dur != null && dur > 0) Matching.best(LrcLib.searchTitle(clean), dur) else null
    }

    private inline fun exact(block: () -> Candidate): Candidate? =
        try { block().takeIf { it.synced != null || it.plain != null || it.instrumental } } catch (_: LrcLib.NotFound) { null }

    /** "Wrong lyrics": Sam's pick sticks to this track. */
    suspend fun pin(t: Track, c: Candidate): Shown = withContext(Dispatchers.IO) {
        val offset = store.load(t.key)?.offsetMs ?: 0
        store.save(t.key, LyricsStore.Entry(c, pinned = true, offsetMs = offset, fetchedAt = System.currentTimeMillis()))
        found(c, true, offset)
    }

    /** Forget the pick (and the cache) and match again. */
    suspend fun unpin(t: Track): Shown {
        withContext(Dispatchers.IO) { store.save(t.key, LyricsStore.Entry(null, false, store.load(t.key)?.offsetMs ?: 0, 0)) }
        return load(t)
    }

    suspend fun nudge(t: Track, deltaMs: Long): Long = withContext(Dispatchers.IO) {
        val e = store.load(t.key) ?: LyricsStore.Entry(null, false, 0, 0)
        val next = (e.offsetMs + deltaMs).coerceIn(-10_000, 10_000)
        store.save(t.key, e.copy(offsetMs = next))
        next
    }

    /** Settings: how many tracks have something saved (lyrics, a pick or a timing nudge). */
    suspend fun savedCount(): Int = withContext(Dispatchers.IO) { store.count() }

    /** Settings: "Reset all timing nudges". */
    suspend fun resetNudges() = withContext(Dispatchers.IO) { store.resetOffsets() }

    /** Settings: "Clear saved lyrics". Wipes the cache, picks and nudges. */
    suspend fun clearSaved() = withContext(Dispatchers.IO) { store.clear() }

    suspend fun search(q: String): List<Candidate> = withContext(Dispatchers.IO) { LrcLib.search(q) }
    suspend fun searchTitle(title: String): List<Candidate> = withContext(Dispatchers.IO) { LrcLib.searchTitle(title) }

    private fun found(c: Candidate, pinned: Boolean, offset: Long): Shown {
        val synced = c.synced?.let { Lrc.parseSynced(it) }?.takeIf { it.isNotEmpty() }
        val lines = synced ?: c.plain?.let { Lrc.parsePlain(it) } ?: emptyList()
        return Shown.Found(c, lines, synced != null, pinned, offset)
    }
}
