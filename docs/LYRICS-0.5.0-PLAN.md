# Scarlet Lyrics 0.5.0 plan: your timing

Status: **answers recorded 2026-10-11 (Q15 to Q27 in `LYRICS-DECISIONS.md`); the owner said "build as planned"; slice 1 done, slice 2 next.** Nothing here is built yet. Lines marked **proposed** are Claude's and are confirmed or changed at the slice that builds them. This plan replaces the earlier 0.4.2 plan (Q27: the release is 0.5.0).

## Why

Lyrics and timing come from LRCLIB, a volunteer database: anyone can upload, nobody checks. Two checks on 2026-10-11 showed what that means:

1. **A mistimed upload won.** For one song, the first lookup (`/api/get` with the player's exact title, artist, album and length) returned a lone record that is 3.5 to 4.7 s late in the first minute and about 4.5 s early after that. About 20 other records share one timing, and the owner's ears matched it at 0:06, 1:10, 2:00 and 3:00. A nudge moves every line by the same amount, so it couldn't fix the lone record; and the nudge belongs to the song, so it followed the owner to every other version, which then looked wrong too. For the same song, nearly every record filed under the radio edit's length carries the album version's timing.
2. **Another artist's lyrics.** For a brand-new release with no lyrics on LRCLIB, the title-only fallback (any artist, length within 10 s) showed a different band's song with the same title.

Official, licensed lyrics would need a commercial agreement, an API key a public app can't hide, and sending every play to that company; Spotify's own lyrics can't be reached without reverse engineering. So 0.5.0 makes Lyrics careful about what it shows, makes the user's own timing win, and lets users give fixes back to LRCLIB.

Song-specific details of both checks are kept privately, not in this repo.

## Decided (owner, 2026-10-11)

| # | What | Answer |
|---|---|---|
| Q15 | Scope | Match the right cut; show fit in Other versions; "Line up now" (the multi-point fix came back as Q22) |
| Q16 | Prefer the timing most records agree on | Yes |
| Q17 | A nudge when another version is picked | Reset to 0 |
| Q18 | Songs matched automatically before 0.5.0 | Looked up again the next time each plays; picks by hand stay |
| Q19 | "Line up now" | Tap "Now" when you hear the next line |
| Q20 | Adjusting | A header icon, always there while lyrics show, opens a "This song" sheet: Other versions, Line up now, Earlier and Later, Fix lines, Share your fix. The More sheet's Timing section moves there |
| Q21 | The end note | Keep the note, drop its button |
| Q22 | "Your timing wins" | Fix lines: as the song plays, tap "Now" when a line starts; tapped lines are anchors, the lines between follow the nearest anchors; saved per song; wins over LRCLIB |
| Q23 | Sharing a fix with LRCLIB | Ask each time: show exactly what's sent; nothing goes until the user taps Send |
| Q24 | "Unsure" hint | A small dot on the header icon when records disagree or the lyrics run past the song's end; TalkBack says the timing may be off |
| Q25 | Fixes in the Library backup | Yes: song, version id, offset and anchor times, never lyric text; a new backup format version; older backups still restore |
| Q26 | Title-only matches (another artist) | Never automatically: "No lyrics found", and they're listed in Other versions with their artist shown |
| Q27 | Version | 0.5.0 |

## What can go wrong (proposed, confirmed per slice)

### Choosing a record
1. **No agreement** (two records, two timings): today's order (exact hit, then closest length), still subject to the cut check, and the unsure dot shows.
2. **Agreement on the wrong timing:** can't be known from data; Fix lines and Share cover it.
3. **The cut check is too strict** (an end marker after the music, a long outro): a record fits when its last timed line is no later than the song's length plus 5 s.
4. **Nothing fits:** words only if a fitting record has them, else the closest synced record with the unsure dot and the end note.
5. **The extra search fails** (offline, timeout): the exact hit is used as today, and the song is looked up again next time it plays.
6. **More network:** one extra search per song on its first play (and once after 0.5.0 for songs matched before); same host, same query fields.
7. **Title-only results** (Q26) never show automatically; a song filed under a label or composer needs one pick in Other versions, which then sticks.

### Your timing
8. **Picking another version** (Q17): offset and anchors go to 0, and so does "Go back to the automatic match". The headphone delay stays.
9. **A rematch that finds the same record** keeps offset and anchors; a different record starts at 0.
10. **Anchors:** the offset for a line is interpolated between the anchors before and after it; before the first anchor it uses the first one's, after the last the last one's. An anchor tap with nothing playing or paused does nothing (the buttons are hidden). A tap replaces an earlier anchor on the same line. A reaction allowance (about 0.2 s, a Tuning value in personal builds) is subtracted. Limits stay plus or minus 10 s per anchor.
11. **"Clear my timing"** in the sheet removes offset and anchors for this song (wording shown to the owner at that slice).
12. **Format:** cache entries gain a version and an anchors list; old entries read as version 0 with no anchors; a newer entry is never overwritten by an older app.

### Sharing (Q23)
13. **What's sent:** title, artist, album and length from the player, the version's plain lyrics, and synced lyrics rebuilt with the user's timing. Shown in full before Send. Never anything from the Library.
14. **LRCLIB's publish flow:** to be checked against LRCLIB's documentation at that slice (expected: a challenge solved on the device, then the publish request). The work runs off the main thread, can be cancelled, and a failure says so plainly. Only lrclib.net, as now.
15. **Sending twice:** the button is disabled while sending; after success it says when the fix was sent.
16. **Only fixed songs:** Share appears only when the user has an offset or anchors for this song.

### Backup (Q25)
17. **New format version:** restoring a 0.4.x backup still works; a 0.5.0 backup restored on 0.4.x says it's from a newer version (as the format already does).
18. **Merging:** a restored fix replaces the one on the device only if the device has none for that song (proposed; asked at that slice).

## Slices

Each is a commit or small series, tests in the same commit, `/finalize --slice` before the next.

1. **Choose the record.** A pure step in `Matching`: cut check, agreement groups (same line count, line times within about 0.5 s), closest length, then more lines (D-038); title-only results never chosen (Q26). Cache entries get a matching version; unpinned entries from before it are looked up again on play (Q18). Unsure flag for the dot. JVM tests with synthetic records (made-up words, real-shaped timings): lone outlier versus a group, album timing on an edit's length, no agreement, title-only, offline extra search.
   **Done 2026-10-11** (`32578e3` and the mini-audit fixes). `/finalize --slice`: one review agent, 1 crash and 3 small bugs, all fixed (M1 to M4 below), 1 more from the fixes (M5), re-check clean. 82/82 unit tests (MatchingTest 11 new), lint 0 errors, 49 older warnings. Checked against the real LRCLIB data of both 2026-10-11 cases (replicated off the device): the mistimed song now gets the timing the owner heard (a group of 10 agreeing records over the lone exact hit), the new release gets "No lyrics found". Not yet on a device (the next personal build).

   | Id | Finding | Fix |
   | --- | --- | --- |
   | M1 | A record with a synced field but no timed lines could crash the matcher | Counts as words only; the last fallback can't see an empty list |
   | M2 | A garbled search reply threw away an exact hit | Falls back to the exact hit, looked up again next time |
   | M3 | Re-looking up an older saved match waited through every retry offline | One try; the saved lyrics show at once |
   | M4 | "Go back to the automatic match" kept a nudge that was then dropped anyway | Saves 0 (Q17) |
   | M5 | A tags-only record could beat real words | Real words first |

2. **Your timing model.** Offset plus anchors per song in the cache entry (format version), the interpolation, reset on a new version (Q17), clear. JVM tests.
3. **The header icon and "This song" sheet.** Drawn icon (brand rule), unsure dot (Q24), the sheet with Other versions, Earlier and Later (moved from More), and the end note without its button (Q21). Emulator and device check.
4. **Line up now and Fix lines.** "Now" for the next line (Q19); Fix lines mode (Q22). Device test with a real song.
5. **Fit in Other versions.** Each version shows how it fits the song, title-only results show their artist (Q26). Wording to the owner at the slice.
6. **Fixes in the Library backup** (Q25). Format tests, restore of an old backup.
7. **Share your fix** (Q23). Preview of exactly what's sent, Send, the publish flow. Tests on the payload builder; one real publish only with the owner's OK for that exact song.

Then `/finalize 0.5.0`. Device tests include both songs from 2026-10-11 (held privately).

## Baseline

As at the 0.4.1 release (`7e97e9c`): build passes, 71/71 unit tests, lint 0 errors and 49 older warnings.
