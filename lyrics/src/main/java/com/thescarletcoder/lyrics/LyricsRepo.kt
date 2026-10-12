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
    /**
     * [unsure]: Lyrics isn't sure of the timing (the records disagreed, or none fits the song). [offsetMs] and
     * [anchors]: the user's timing (0.5.0), applied to [lines] on screen with YourTiming.apply.
     */
    data class Found(
        val candidate: Candidate, val lines: List<LyricLine>, val synced: Boolean, val pinned: Boolean, val offsetMs: Long,
        val unsure: Boolean = false, val anchors: List<Anchor> = emptyList(),
        /** Lined up by ear, so it can be shared; and when it was shared (0 if not). */
        val tapped: Boolean = false, val sentAt: Long = 0,
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

        /**
         * A cached automatic match is used as it is when it was made with today's rules, or when the user has timed it
         * (a nudge, Line up now, Fix lines): their version stays, never swapped by a re-lookup (owner, finalize 0.5.0 Q1).
         */
        fun isCurrent(e: LyricsStore.Entry) = e.pinned || e.matchVersion >= MATCHING ||
            (e.candidate != null && (e.offsetMs != 0L || e.anchors.isNotEmpty() || e.tapped))

        /** A new match keeps the user's timing only when it is the same record (owner Q17: a new version starts at 0). */
        fun keptTiming(old: LyricsStore.Entry?, newId: Long?): Pair<Long, List<Anchor>> =
            if (old?.candidate != null && old.candidate.id == newId) old.offsetMs to old.anchors else 0L to emptyList()

        /**
         * The unsure dot (owner Q24): a version picked by hand shows it only when its lyrics run past the song's end
         * (finalize 0.5.0 Q5), never because other records disagree: the user chose it.
         */
        fun pickUnsure(pinned: Boolean, unsure: Boolean, c: Candidate, durationSec: Double?) =
            if (pinned) !Matching.fits(c, durationSec) else unsure

        private fun Track.seconds() = durationMs?.let { it / 1000.0 }

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
        var cached = store.load(t.key)
        // A restored fix (0.5.0): fetch its version by id, with the user's timing on it.
        val pid = cached?.pendingId
        if (cached != null && cached.candidate == null && pid != null) {
            try {
                val c = withRetries(RETRY_DELAYS_MS) { LrcLib.byId(pid) }
                val e = cached.copy(candidate = c, pendingId = null, fetchedAt = System.currentTimeMillis(), matchVersion = MATCHING)
                store.save(t.key, e)
                return@withContext found(c, e.pinned, e.offsetMs, anchors = e.anchors, entry = e, durationSec = t.seconds())
            } catch (e: Exception) {
                if (e is IOException && e !is LrcLib.NotFound) return@withContext Shown.Offline   // fetched next time
                if (e !is LrcLib.NotFound && e !is org.json.JSONException) throw e
                // The version is gone from LRCLIB (or its reply makes no sense): match again; its timing belonged to
                // it (owner: drop the fix).
                cached = LyricsStore.Entry(null, false, 0, 0)
                store.save(t.key, cached)
            }
        }
        if (cached != null) {
            if (cached.candidate != null && isCurrent(cached)) return@withContext found(cached.candidate, cached.pinned, cached.offsetMs, cached.unsure, cached.anchors, cached, t.seconds())
            // A remembered miss counts only if it was found with today's matching rules (title-only fallback added).
            if (cached.candidate == null && System.currentTimeMillis() - cached.fetchedAt < retryMissAfterMs && cached.fetchedAt > MATCHING_CHANGED_AT) return@withContext Shown.NotFound
        }
        val old = cached?.candidate
        fun stale() = if (cached != null && old != null) found(old, false, cached.offsetMs, cached.unsure, cached.anchors, cached) else Shown.Offline
        try {
            // A dropped connection gets two more tries before Lyrics says it can't reach LRCLIB (2026-10-09:
            // on a phone the connection dropped often, sometimes mid-song).
            // Looking up an older saved match again gets one try: offline, the saved lyrics show at once, not after the retries.
            val m = withRetries(if (old != null) emptyList() else RETRY_DELAYS_MS) { lookup(t) }
            val c = m.choice?.candidate
            val (offset, anchors) = keptTiming(cached, c?.id)
            val same = cached?.takeIf { it.candidate != null && it.candidate.id == c?.id }
            val e = LyricsStore.Entry(
                c, pinned = false, offsetMs = offset, fetchedAt = System.currentTimeMillis(),
                matchVersion = if (m.complete) MATCHING else 0, unsure = m.choice?.unsure ?: false, anchors = anchors,
                tapped = same?.tapped ?: false, sentAt = same?.sentAt ?: 0,
            )
            store.save(t.key, e)
            if (c == null) Shown.NotFound else found(c, false, offset, m.choice!!.unsure, anchors, e)
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
        } catch (e: Exception) {
            // A failed or garbled search still lets an exact hit show; the song is looked up again next time.
            if (hit == null || (e !is IOException && e !is org.json.JSONException)) throw e
            return Lookup(Matching.Choice(hit, unsure = false), complete = false)
        }
        return Lookup(Matching.choose(hit, results, dur), complete = true)
    }

    private inline fun exact(block: () -> Candidate): Candidate? =
        try { block().takeIf { it.synced != null || it.plain != null || it.instrumental } } catch (_: LrcLib.NotFound) { null }

    /** "Other versions": the user's pick sticks to this track; a new version starts with its own timing (owner Q17). */
    suspend fun pin(t: Track, c: Candidate): Shown = withContext(Dispatchers.IO) {
        val old = store.load(t.key)
        val (offset, anchors) = keptTiming(old, c.id)   // picking the same version again keeps it
        val same = old?.takeIf { it.candidate?.id == c.id }
        val e = LyricsStore.Entry(c, pinned = true, offsetMs = offset, fetchedAt = System.currentTimeMillis(), anchors = anchors,
            tapped = same?.tapped ?: false, sentAt = same?.sentAt ?: 0)
        store.save(t.key, e)
        found(c, true, offset, anchors = anchors, entry = e, durationSec = t.seconds())
    }

    /** Forget the pick (and the cache) and match again; the nudge belonged to the pick (owner Q17). */
    suspend fun unpin(t: Track): Shown {
        withContext(Dispatchers.IO) { store.save(t.key, LyricsStore.Entry(null, false, 0, 0)) }
        return load(t)
    }

    suspend fun nudge(t: Track, deltaMs: Long): Long = withContext(Dispatchers.IO) {
        val e = store.load(t.key) ?: LyricsStore.Entry(null, false, 0, 0)
        val next = (e.offsetMs + deltaMs).coerceIn(-YourTiming.LIMIT_MS, YourTiming.LIMIT_MS)
        store.save(t.key, e.copy(offsetMs = next, sentAt = 0))   // anchors move with it: they're relative to the base
        next
    }

    /** A tapped line (owner Q22): the line at [lineMs] moves [offsetMs] in all. Returns the anchors now saved. */
    suspend fun setAnchor(t: Track, lineMs: Long, offsetMs: Long): List<Anchor> = withContext(Dispatchers.IO) {
        // Nothing saved for the song (cleared meanwhile): anchors without a version would be dropped on the next load.
        val e = store.load(t.key)?.takeIf { it.candidate != null } ?: return@withContext emptyList()
        val anchors = YourTiming.withAnchor(e.anchors, e.offsetMs, lineMs, offsetMs)
        store.save(t.key, e.copy(anchors = anchors, tapped = true, sentAt = 0))
        anchors
    }

    /** Line up now (owner Q19): the base nudge that puts the line at [lineMs] where it was heard. Returns the new base. */
    suspend fun lineUp(t: Track, lineMs: Long, offsetMs: Long): Long? = withContext(Dispatchers.IO) {
        val e = store.load(t.key)?.takeIf { it.candidate != null } ?: return@withContext null
        val base = YourTiming.baseFor(e.anchors, lineMs, offsetMs)
        store.save(t.key, e.copy(offsetMs = base, tapped = true, sentAt = 0))
        base
    }

    /** Share your fix sent: when, so This song can say so. */
    suspend fun markSent(t: Track, versionId: Long, at: Long) = withContext(Dispatchers.IO) {
        store.load(t.key)?.takeIf { it.candidate?.id == versionId }?.let { store.save(t.key, it.copy(sentAt = at)) }
    }

    /** "Clear my timing": no nudge and no anchors for this song; the version stays. */
    suspend fun clearTiming(t: Track) = withContext(Dispatchers.IO) {
        store.load(t.key)?.let { store.save(t.key, it.copy(offsetMs = 0, anchors = emptyList(), tapped = false, sentAt = 0)) }
    }

    /** Settings: how many tracks have something saved (lyrics, a pick or a timing nudge). */
    suspend fun savedCount(): Int = withContext(Dispatchers.IO) { store.count() }

    /** Settings: "Reset all timing". */
    suspend fun resetNudges(): Boolean = withContext(Dispatchers.IO) { store.resetOffsets() }

    /** Settings: "Clear saved lyrics". Wipes the cache, picks and nudges. */
    suspend fun clearSaved(): Boolean = withContext(Dispatchers.IO) { store.clear() }

    suspend fun search(q: String): List<Candidate> = withContext(Dispatchers.IO) { LrcLib.search(q) }
    suspend fun searchTitle(title: String): List<Candidate> = withContext(Dispatchers.IO) { LrcLib.searchTitle(title) }

    private fun found(
        c: Candidate, pinned: Boolean, offset: Long, unsure: Boolean = false, anchors: List<Anchor> = emptyList(), entry: LyricsStore.Entry? = null,
        durationSec: Double? = null,
    ): Shown {
        val synced = c.synced?.let { Lrc.parseSynced(it) }?.takeIf { it.isNotEmpty() }
        val lines = synced ?: c.plain?.let { Lrc.parsePlain(it) } ?: emptyList()
        return Shown.Found(c, lines, synced != null, pinned, offset, unsure = pickUnsure(pinned, unsure, c, durationSec), anchors = anchors,
            tapped = entry?.tapped ?: false, sentAt = entry?.sentAt ?: 0)
    }
}
