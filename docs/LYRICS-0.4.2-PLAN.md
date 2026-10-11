# Scarlet Lyrics 0.4.2 plan: timing you can trust

Status: **answers recorded 2026-10-11 (Q15 to Q19 in `LYRICS-DECISIONS.md`); waiting for the owner's go.** Nothing here is built. Lines marked **proposed** wait for the owner.

## What happened (verified 2026-10-11)

The owner reported that a timing nudge "doesnt seem to do anything" and that none of the versions in Other versions synced. The song was checked live: the owner read the music app's counter at six lines, against two LRCLIB records with the same 103 lines.

- **Lyrics picked a bad upload.** The first lookup (`/api/get` with the player's exact title, artist, album and length) returned a lone record that is 3.5 to 4.7 s late in the first minute and about 4.5 s early after that. About 20 other records for the same song share one timing, and that timing matched the owner's ears at 0:06, 1:10, 2:00 and 3:00 (and within about 2 s at 2:40).
- **A nudge can't fix it.** A nudge moves every line by the same amount; this record is wrong in both directions. The nudge code itself works (applied to the position at once, saved per song).
- **The nudge follows every version.** The offset belongs to the song, not the version, so a nudge made on the bad record stays when a good version is picked. The good version then looks wrong too.
- **Wrong cuts look like right ones.** For this song, nearly every synced record filed under the radio edit's length (about 3:33) carries the album version's timing, with lyrics running to 3:58. Nothing checks that a record fits the song. How common this is for other songs isn't known.
- **Even the right record ends early.** Its last line is 12 s before the last vocal. The end note (D-037) covers that.

Records are only checked by timestamps here; lyric text is never copied into tests or docs.

## Decided (owner, 2026-10-11)

| # | What | Answer |
|---|---|---|
| Q15 | Plan scope | "Match the right cut", "Show fit in Other versions", "\"Line up now\" nudge" (the multi-point fix for cuts that skip a section stays out) |
| Q16 | Prefer the timing most records agree on | "Yes, add it (Recommended)" |
| Q17 | A nudge when picking another version | "Reset to 0 on a new version (Recommended)" |
| Q18 | Songs matched automatically before 0.4.2 | "Yes, the next time each plays (Recommended)" |
| Q19 | How "Line up now" works | "Tap when you hear the line (Recommended)" |

## What can go wrong (proposed)

### Choosing a record
1. **No agreement** (two records, two timings): **proposed:** keep today's order (exact hit, then closest length), still subject to the cut check.
2. **Agreement on the wrong timing** (many copies of one bad upload): can't be known from data; the owner still has Other versions and Line up now.
3. **The cut check is too strict** (an end marker line after the music, a long outro): **proposed:** a record fits when its last timed line is no later than the song's length plus 5 s.
4. **Nothing fits:** **proposed:** words only (plain lyrics) if a fitting record has them, else the closest synced record as today, with the end note.
5. **The extra search fails** (offline, timeout): the exact hit is used as today; the song is looked up again next time it plays.
6. **More network:** one extra search per song, only on a song's first play (and once after 0.4.2 for songs matched before); same host, same query fields as today.
7. **Rematching on upgrade:** only unpinned matches made before 0.4.2 (a format marker in the cache entry); picks by hand and their nudges stay untouched.

### Nudges
8. **Picking another version** (Q17): the offset goes to 0, and so does "Go back to the automatic match". The output delay (headphones) is separate and stays.
9. **A rematch that finds the same record** keeps the nudge; a different record resets it, as a new version.

### Line up now
10. **Nothing playing or paused:** the button is hidden; it needs a moving position.
11. **Tapped late** (human reaction): **proposed:** subtract a small fixed reaction allowance (about 0.2 s, a Tuning value in personal builds).
12. **Tapped on the wrong line:** a second tap on a later line replaces the offset; Earlier and Later still fine-tune; the limit stays plus or minus 10 s.
13. **Lines with no time** (plain lyrics): the Timing section only shows for synced lyrics, as today.

## Slices

Each is one commit or a small series, with tests in the same commit and `/finalize --slice` before the next.

1. **Choose the record.** A pure `Pick` step in `Matching`: given the exact hit (if any), the search results and the song's length, apply the cut check, group records whose timings agree (same line count and line times within about 0.5 s), and pick from the largest fitting group (closest length, then more lines, folding in D-038). Cache entries get a matching version; unpinned entries from before it are looked up again on play. Tests: synthetic records with made-up words and real-shaped timings for the cases above (lone outlier versus a group of 20; album timing on an edit's length; no agreement; offline extra search).
2. **Nudge reset.** `pin` and `unpin` save offset 0; the More sheet shows the reset at once. Tests: pin after a nudge gives 0; a rematch to the same record keeps it.
3. **Fit in Other versions.** Each row says how its timing fits the song (wording to be shown to the owner word for word at that slice), and records that run past the end are marked. Tests on the pure labels.
4. **Line up now.** In the More sheet's Timing section: the next line to be sung and a "Now" button; tapping sets the offset from the line's time and the position. Tests on the pure offset sum (reaction allowance, limits).

Then `/finalize` for 0.4.2. Device checks include this song's case, held in scarlet-hq (private), not here.

## Baseline

As at the 0.4.1 release (`7e97e9c`): build passes, 71/71 unit tests, lint 0 errors and 49 older warnings.
