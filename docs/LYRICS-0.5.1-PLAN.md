# Cawback 0.5.1 plan

Status: **go given 2026-10-14** (the owner: "1-3 now" and the #5 items). Answers Q47 to Q55 in `LYRICS-DECISIONS.md`. Items marked proposed are Claude's and are confirmed or changed with the owner before their slice is committed.

## Baseline (2026-10-14, at 702c029, from /finalize 0.5.0 QA pass 8)

Build passes; 118 of 118 unit tests (debug and release); lintRelease 0 errors, 49 older warnings; labtest 8/8 on the tablet. Repo guard installed. Both devices run 0.5.0+702c029.

## Slices

Each slice: tests in the same commit, then `/finalize --slice`.

### Slice 1: a shared fix replaces the record it fixes (Q47)
Share your fix sends the chosen record's own title, artist, album and length (`Candidate.track`, `artist`, `album`, `durationSec`) instead of the music app's. LRCLIB then adds the fix to that record (its server matches title, artist and album exactly, ignoring case, and the length within 2 s) instead of creating a near-duplicate. The Share preview shows those names, since they are what's sent.
Acceptance: payload test (record names sent, not the player's; length within LRCLIB's 2 s match); the preview text shown to the owner word for word; Send still never tapped in tests.

### Slice 2: licences and the shared controls file (Q47)
Apache 2.0 notice for Compose and AndroidX in `licenses/` and a Credits line (as Keyward 0.2.2). Compare `ScarletControls.kt` with the canonical copy in Keyward; bring the copy in line, or report the difference as a question if the copies changed on purpose.
Acceptance: drift_check clean for this file; README Credits matches `licenses/`.

### Slice 3: small screen changes (Q49, Q51, Q52)
Songs and Stats tabs in the Library use Settings' underline style (one shared tab composable). "Your music app" and "Never follow" no longer list the Bluetooth service or the Google app. The header reads "Back to lyrics".
Acceptance: filter JVM test; tabs: TalkBack says which is selected, 48 dp, wrap at 200% font; screenshots on three emulator shapes, light and dark.

### Slice 4: optional cleanup of unused saved lyrics (Q48)
A setting in Settings > Privacy, off by default: delete saved lyrics not opened for 90 days. A "last used" time per saved song (older files count as used now, so nothing goes on the first run). Runs in the background when Cawback opens, on the existing app-level runner, never on the main thread.
Decided (Q56): a song with your own pick or timing is never deleted by this; only plain cached lookups are.
Acceptance: JVM tests (off: nothing deleted; on: only unused over 90 days; picks and timing kept as decided; a busy key store deletes nothing); What's on this device count updates; text shown to the owner word for word.

### Slice 5: skipped sections (Q53)
- A tapped line may move up to the song's length; Earlier and Later keep their 10 s limit.
- Between two tapped lines whose moves differ by more than about 2 s, the change happens at the biggest pause between lines in that stretch (a step), not spread over every line. Smaller differences still spread evenly.
- In Fix lines, a line can be marked "not in this song": hidden while it plays, saved with the timing, restored by the same mark, left out of Share your fix.
Decided (Q57): the Library backup format goes up one version for the skipped lines, so 0.5.0 says "newer" instead of silently dropping them (BACKUP-SPEC applies: drives or a recorded skip).
Acceptance: JVM tests (a 20 s cut fixed with two taps; the step lands in the gap; skipped lines hidden and absent from the share payload; an older backup restores; the order of lines never changes); emulator walk with the stand-in player; TalkBack labels for the new mark.

### Slice 6: deferred cleanup (Q50)
C9 (the share steps out of the screen, overlay state, edits returning the new entry), C10 (shared anchor JSON and hash helpers, a body text composable, clock and plural reuse) and C11 without parallel lookups (per-line anchor sort, the Fix lines bar redrawing less often).
Acceptance: no behaviour change; all tests pass; review agent finds no regressions.

Then `/finalize 0.5.1`.

## What can go wrong (proposed)

| # | Case | What the code does |
| --- | --- | --- |
| W1 | The record has no album | Send the record's album as it is (empty), as LRCLIB stores it. |
| W2 | A cleanup run is killed half way | Each deletion is one file; a half run just leaves the rest for next time. |
| W3 | The key store is busy during cleanup | Nothing is deleted on that run. |
| W4 | Two tapped lines pull hard against each other | Lines never change order (the existing floor rule). |
| W5 | Every line marked "not in this song" | Shown as no lyrics for this version, with Other versions still reachable. |
| W6 | A restored backup from 0.5.0 | Restores as before; no skipped lines. |

## Not in this release

The tuning log, Fix words (0.6.0), time a plain record yourself, the approved mark, visuals, the resolver.
