# Scarlet Lyrics 0.5.0 plan: your timing

Status: **answers recorded 2026-10-11 (Q15 to Q27 in `LYRICS-DECISIONS.md`); the owner said "build as planned"; slices 1 to 5 built (3 to 5 wait for the owner's device check), slice 6 next.** Lines marked **proposed** are Claude's and are confirmed or changed at the slice that builds them. This plan replaces the earlier 0.4.2 plan (Q27: the release is 0.5.0).

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
   **Done 2026-10-11** (`11f18e2` and the mini-audit fixes). `/finalize --slice`: one review agent, no correctness bugs (the screen matches the old behaviour for a nudge alone: seek, moments, gap dots, end note, current line), 2 low findings fixed (M6, M7). 91/91 unit tests (YourTimingTest 9 new, one with the damaged-anchor check), lint 0 errors, 49 older warnings. Nothing visible yet: the buttons come in slices 3 and 4.

   | Id | Finding | Fix |
   | --- | --- | --- |
   | M6 | A damaged anchors list in a cache file dropped the whole entry (pick and nudge) | A bad pair is skipped on its own; JVM test |
   | M7 | A tap after the song's cache was cleared saved anchors that the next load drops | No-op without a saved version, as Clear my timing |

   Notes for slices 3 and 4: the timing label (now the base nudge only) must say when anchors are set; taps must pass the line's time in the record (`s.lines[i]`), not the shifted time on screen.

3. **The header icon and "This song" sheet.** Drawn icon (brand rule), unsure dot (Q24), the sheet with Other versions, Earlier and Later (moved from More), and the end note without its button (Q21). Emulator and device check.
   **Built 2026-10-11** (`d082a15` and the mini-audit fixes). Wording approved by the owner (heading, TalkBack labels, unsure note, timing line, Clear my timing and its confirm); Other versions only in This song (owner: "Only in This song (Recommended)"). `/finalize --slice`: one review agent, 5 findings fixed (M8 to M12). 92/92 unit tests (the timing line's wording tested), lint 0 errors, 49 older warnings. Not seen on an emulator (no player there shows the button): device check by the owner.

   | Id | Finding | Fix |
   | --- | --- | --- |
   | M8 | The extra header button left the title almost no room in a narrow split | Under 340dp the album thumbnail drops |
   | M9 | Earlier or Clear tapped as the song changed could put the old song's lyrics back | Applied only if the same lyrics still show |
   | M10 | This song stayed open over a new song, or with nothing playing | Closes when the song changes or the button goes (a recreate keeps it) |
   | M11 | A confirm left open could clear the next song's timing | Resets with the song |
   | M12 | The button flickered out while the next song loaded | Stays while loading |
   | M13 | The M9 fix dropped a second quick Earlier or Later (screen and saved timing apart) | Compares the version, not the screen state; re-review clean apart from a sheet that closes during the blank moment between songs (accepted) |

4. **Line up now and Fix lines.** "Now" for the next line (Q19); Fix lines mode (Q22). Device test with a real song.
   **Built 2026-10-11** (`1c53804` and the mini-audit fixes). Wording approved by the owner (both sheet rows; the bar's "Tap Now when you hear:", Now, Cancel, Done, "No more lines to fix.", "Play the song to fix its timing."). Reaction allowance 200 ms (Tuning, personal builds). `/finalize --slice`: one review agent checked the sums with numbers (with and without a headphone delay, with a base and anchors: correct), 4 findings fixed (M14 to M17). 95/95 unit tests, lint 0 errors, 49 older warnings. Device check by the owner.

   | Id | Finding | Fix |
   | --- | --- | --- |
   | M14 | The bar's line moved on by the lyrics' own clock, so with early lyrics a tap pinned the wrong line | The line stays until tapped; the first one is at least 10 s ahead on the lyrics' clock (it can't have been sung yet, however wrong the timing); a line more than 10 s behind was missed and the next one that can still come takes its place |
   | M15 | The bar showed over Settings, Library and the other pages | Only on the lyrics |
   | M16 | In Flex mode the bar covered the controls below the fold | At the bottom of the lyrics area, above the fold |
   | M17 | A brief reload closed the bar | Closes only for words only, a miss or offline, or a song change |
   | M18 | After a missed line the bar recomputed its line on every poll (drifting, possibly to a line already sung) | A missed line is replaced once by a new first target, then frozen again |
   | M19 | The approved row text promised "the next line", but the bar waits 10 to 20 s for a safe one | Owner: "\"...the line shown\" (Recommended)": "Tap Now the moment you hear the line shown. The whole song moves." |

5. **Fit in Other versions.** Each version shows how it fits the song, title-only results show their artist (Q26). Wording to the owner at the slice.
   **Built 2026-10-11** (`3fd8e9c` and the mini-audit fixes). Wording and order approved by the owner ("runs past the end of this song", "same timing as N others" / "as 1 other", "different artist"; best fit first). `/finalize --slice`: one review agent, 5 findings fixed (M20 to M24). 98/98 unit tests, lint 0 errors. Device check by the owner. Not decided by the owner, chosen here: words-only versions sort between fitting synced ones and synced ones that run past the end (words don't claim a timing).

   | Id | Finding | Fix |
   | --- | --- | --- |
   | M20 | "different artist" for the same artists credited another way (slashes, reordered, "Feat.", "Ke$ha"), and false matches by substring ("Earth, Wind & Fire" and "Arcade Fire") | Both credits split the same way; whole names compared; JVM tests for each case |
   | M21 | Every row said "different artist" when the song's artist is blank, unknown or "Various Artists" | No artist note then |
   | M22 | The order put the artist before fit, against the decision | Fitting, agreeing, same artist, then length |
   | M23 | "Showing now" could be cut off at the end of a long row | First in the row |
   | M24 | The sorting ran on the main thread, twice | Once, off the main thread |
   | M25 | The credit split cut inside words ("Featherstone") and split "The X-Ecutioners", leaving a bare "the" that matched anything | Whole words only, " x " only between spaces, no bare "the"; re-review clean |

6. **Fixes in the Library backup** (Q25). Format tests, restore of an old backup.
7. **Share your fix** (Q23). Preview of exactly what's sent, Send, the publish flow. Tests on the payload builder; one real publish only with the owner's OK for that exact song.

Then `/finalize 0.5.0`. Device tests include both songs from 2026-10-11 (held privately).

## Baseline

As at the 0.4.1 release (`7e97e9c`): build passes, 71/71 unit tests, lint 0 errors and 49 older warnings.
