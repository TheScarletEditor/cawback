# Scarlet Lyrics: tuning log plan (personal builds)

Status: **planned, waiting for the owner's go.** Prepared 2026-10-14 (/prep resume). Starts only after /finalize 0.5.0 has shipped (Q35). The owner's answers are Q35 to Q40 in `LYRICS-DECISIONS.md`; the scope came from the planning chat and is mirrored in scarlet-hq.

## Why

To see where the effort and the errors are, with numbers instead of memory: how often the automatic match is wrong, what the user does per song, which offsets are applied, and whether timing errors are the same on every song or differ per song.

## Decided

- **Personal builds only, in public source (Q36).** Behind `BuildConfig.TUNING`, like the Tuning screen: on in debug, personal and labtest; off in release and smoke. A release build never runs a log code path.
- **No lyric text, ever.** Not in an event, the export or a test fixture.
- **On the device only.** A separate encrypted file (Q37), the same pattern as the lyric cache (AES-256-GCM with a Keystore key). No new permission, no network, never sent anywhere.
- **Excluded from every backup,** like the lyric cache. No backup format changes.
- **Capped and clearable:** 90 days or 5 MB, whichever comes first; a Clear log button.
- **Honest:** listed in Settings > Privacy > What's on this device, with a real count.
- **Export only on the user's tap:** CSV through the existing export provider and share sheet.
- **"This song is right / Still wrong":** one optional tap in This song, personal builds only.
- **Timing families are counted for the log only (Q40).** Which version Lyrics picks does not change.

## What it records (one row per event)

Each row: time, the song key (`SongKey`, as the Library keeps it), the event type, and numbers, ids or fixed choices only.

1. **Lookup:** records returned, synced records, distinct timing families, chosen record id, unsure or not, `matchVersion`, exact hit or search.
2. **Fit of the chosen record:** line count, first and last timestamp, song length, fits or not.
3. **The user's actions:** pick another version (record id), nudge (change and new total), Line up now (resulting offset), Fix lines (anchor count, offsets), Clear my timing, Share fix (sent or cancelled).
4. **Playback context:** output type (speaker, wired, Bluetooth) and the delay applied.
5. **Outcome:** right or still wrong.

Not recorded: lyric text, album art, the music app, anything from other apps, location, notification content.

## Baseline (2026-10-14, at 7b7dace plus two uncommitted doc edits)

- `assembleDebug`: passes.
- Unit tests: 118 of 118 pass.
- `lintDebug`: 0 errors, 49 warnings (the same older set /finalize 0.5.0 recorded).
- Repo guard: installed, matches the current hook.
- Devices: the tablet runs 0.5.0+cb6f88d-dirty and the Flip phone 0.5.0+397d5ff-dirty, both behind main (7b7dace) and built from uncommitted trees. /finalize's ship step brings both up to date.

## What can go wrong (proposed, for the owner to confirm)

| # | Case | What the code does |
| --- | --- | --- |
| W1 | A log write fails (disk full, Keystore busy) | The event is dropped, lyrics carry on, nothing is shown mid-song. The count in What's on this device stays true. |
| W2 | The file is damaged, truncated or from a newer format | It's not read. What's on this device says the log couldn't be read and offers Clear log. Never deleted silently. |
| W3 | The app is killed mid-write | Appends are atomic (temp file and checked rename, or length-prefixed records where a torn tail is dropped on read). No half row is ever read. |
| W4 | The Keystore key is lost or replaced | Same as the lyric cache: the old log can't be read; it's reported and can be cleared. |
| W5 | The cap is reached | The oldest rows go first (age, then size). Pruning never runs on the main thread. |
| W6 | Text sneaks in | Event fields are numbers, ids and enums only; the song key is the only string. A JVM test checks every event type and the CSV header. |
| W7 | A release build | `TUNING` false: the logger is never created. A JVM test plus a check that the release APK has no log file after a lookup (smoke copy). |
| W8 | Forget this song, full reset | Forget removes that song's rows; full reset deletes the log. "Forgotten" only after it is. |
| W9 | Clear log tapped twice, or during an export | One job at a time on the existing app-level runner. |
| W10 | The export reads while the log writes | The export reads a snapshot; a later row isn't half-included. |

## Slices

Each slice: tests in the same commit, then `/finalize --slice`.

**Slice 1: the log store.** Event model (no text fields), encrypted append-only file, cap and prune, Clear, Forget per song, full reset, read snapshot. Created only when `TUNING` is true.
Acceptance: W1 to W8 and W10 covered by JVM tests; release build has the logger off (test); writes off the main thread; build, tests and lint as baseline or better.

**Slice 2: recording.** Hooks in the lookup path (`LrcLib.choose` results, fit, families count as a pure function with JVM tests), in This song's actions and in Share, with output type and delay.
Acceptance: one event per action under quick double taps (the edit lock already in place); a failed write never changes what the screen shows; the families count tested on synthetic records only; no change to the pick (the existing matching tests pass unchanged).

**Slice 3: the screens.** "Right / Still wrong" in This song (TUNING only, TalkBack labels, 48dp, says which is set), the log count and Clear log in What's on this device, CSV export through the share sheet.
Acceptance: rotation, font 200% and recreate on three emulators, light and dark; plurals ("1 event"); Clear log reports a failure honestly; export has no text column (test); the owner's short scripted session on a device gives a matching row count.

## Not in this plan

- Changing which version Lyrics picks by counting timing families (Q40: a separate public change).
- Fix words, the next public feature (Q39), and the visuals plan: each gets its own /prep.
- Any resolver or bundle import work.

## Open

- W1 to W10 are proposed; the owner confirms or changes them with the go.
- Building starts on the owner's go, after 0.5.0 ships.
