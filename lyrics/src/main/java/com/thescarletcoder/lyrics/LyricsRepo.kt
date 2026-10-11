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
    /** [unsure]: Lyrics isn't sure of the timing (the records disagreed, or none fits the song). */
    data class Found(
        val candidate: Candidate, val lines: List<LyricLine>, val synced: Boolean, val pinned: Boolean, val offsetMs: Long,
        val unsure: Boolean = false,
    ) : Shown
}

/** Cache first, then LRCLIB: exact lookup, then a cleaned-up title, then a search. */
class LyricsRepo(private val store: LyricsStore) {
    private val retryMissAfterMs = 24 * 3_600_000L

    companion object {
        /** When matching last improved (2026-10-08 17:00 PDT): misses remembered before this are tried again. */
        private const val MATCHING_CHANGED_AT = 1_791_504_000_000L

        /**
         * The matching rules' version (1: 0.5.0, fit and agreement). Automatic matches made with older rules are looked
         * up again the next time the song plays (owner Q18); picks by hand never are.
         */
        const val MATCHING = 1

        /** A cached automatic match is used as it is only when it was made with today's rules. */
        fun isCurrent(e: LyricsStore.Entry) = e.pinned || e.matchVersion >= MATCHING

        /** A new match keeps the song's nudge only when it is the same record (owner Q17: a new version starts at 0). */
        fun keptOffset(old: LyricsStore.Entry?, newId: Long?) = if (old?.candidate != null && old.candidate.id == newId) old.offsetMs else 0L

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
            if (cached.candidate != null && isCurrent(cached)) return@withContext found(cached.candidate, cached.pinned, cached.offsetMs, cached.unsure)
            // A remembered miss counts only if it was found with today's matching rules (title-only fallback added).
            if (cached.candidate == null && System.currentTimeMillis() - cached.fetchedAt < retryMissAfterMs && cached.fetchedAt > MATCHING_CHANGED_AT) return@withContext Shown.NotFound
        }
        val old = cached?.candidate
        fun stale() = if (cached != null && old != null) found(old, false, cached.offsetMs, cached.unsure) else Shown.Offline
        try {
            // A dropped connection gets two more tries before Lyrics says it can't reach LRCLIB (2026-10-09:
            // on a phone the connection dropped often, sometimes mid-song).
            val m = withRetries(RETRY_DELAYS_MS) { lookup(t) }
            val c = m.choice?.candidate
            val offset = keptOffset(cached, c?.id)
            store.save(t.key, LyricsStore.Entry(
                c, pinned = false, offsetMs = offset, fetchedAt = System.currentTimeMillis(),
                matchVersion = if (m.complete) MATCHING else 0, unsure = m.choice?.unsure ?: false,
            ))
            if (c == null) Shown.NotFound else found(c, false, offset, m.choice!!.unsure)
        } catch (_: IOException) {
            stale()   // an older match still shows while LRCLIB can't be reached; it's looked up again next time
        } catch (_: org.json.JSONException) {
            stale()
        }
    }

    /** [complete]: false when the search failed and only an exact hit was used (looked up again next time). */
    private class Lookup(val choice: Matching.Choice?, val complete: Boolean)

    /**
     * Exact lookups first (the player's title, then a cleaned-up one), then always one search by title and artist, so
     * the exact hit is weighed against the other records (0.5.0). Never a title-only search: another artist's song
     * with the same title is not shown automatically (owner Q26); "Other versions" still lists those.
     */
    private fun lookup(t: Track): Lookup {
        val dur = t.durationMs?.let { it / 1000.0 }
        val clean = Matching.cleanTitle(t.title)
        val artist = t.artist.split(",", " & ", " feat", " ft.").first().trim()
        val hit = exact { LrcLib.get(t.title, t.artist, t.album, dur) } ?: exact { LrcLib.get(clean, artist, null, dur) }
        val results = try {
            LrcLib.search(clean, artist)
        } catch (e: IOException) {
            if (hit == null) throw e
            return Lookup(Matching.Choice(hit, unsure = false), complete = false)
        }
        return Lookup(Matching.choose(hit, results, dur), complete = true)
    }

    private inline fun exact(block: () -> Candidate): Candidate? =
        try { block().takeIf { it.synced != null || it.plain != null || it.instrumental } } catch (_: LrcLib.NotFound) { null }

    /** "Other versions": the user's pick sticks to this track. */
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
    suspend fun resetNudges(): Boolean = withContext(Dispatchers.IO) { store.resetOffsets() }

    /** Settings: "Clear saved lyrics". Wipes the cache, picks and nudges. */
    suspend fun clearSaved(): Boolean = withContext(Dispatchers.IO) { store.clear() }

    suspend fun search(q: String): List<Candidate> = withContext(Dispatchers.IO) { LrcLib.search(q) }
    suspend fun searchTitle(title: String): List<Candidate> = withContext(Dispatchers.IO) { LrcLib.searchTitle(title) }

    private fun found(c: Candidate, pinned: Boolean, offset: Long, unsure: Boolean = false): Shown {
        val synced = c.synced?.let { Lrc.parseSynced(it) }?.takeIf { it.isNotEmpty() }
        val lines = synced ?: c.plain?.let { Lrc.parsePlain(it) } ?: emptyList()
        return Shown.Found(c, lines, synced != null, pinned, offset, unsure = unsure && !pinned)
    }
}
