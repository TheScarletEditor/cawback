# Finalize Scarlet Lyrics 0.4.0

Started 2026-10-10. Base: v0.2.0 (75718c7). Diff: 37 files, +3735 -162.
Mode: dry run (Stages 0 to 3; the first run of /finalize). Public: yes.

## Stages

| # | Stage | Status | Notes |
|---|---|---|---|
| 0 | Preflight | done | 0.4.0 / versionCode 3 already bumped in 7b7d0e6 (base is 0.2.0 / 2). Tree clean apart from the smoke build type and smoke.ps1, committed as setup. |
| 1 | Audit | done | 4 agents (spec, rules, manifest, data). 11 mechanical fixes, 11 questions answered and applied. Re-check: 52/52 unit tests, R8 release build, androidTest compile, rule greps clean. One regression from the D10 fix was caught by an existing test and fixed. |
| 2 | Code review | running | |
| 3 | Cleanup | pending | |
| 4 | QA | skipped (dry run) | |
| 5 | Smoke test | skipped (dry run) | |
| 6 | Ship | skipped (dry run) | |
| 7 | Wrap-up | skipped (dry run) | |

## Findings

| Id | Stage | Label | Where | Finding | Done | Commit |
|---|---|---|---|---|---|---|
| A1 | Audit | question | Player.kt:90 | No app chosen + Spotify installed: follows only Spotify, against "any music app" in README and release notes | asked (Stage 1 Q1) | |
| A2 | Audit | mechanical | LyricsActivity.kt:436 | Opening "Note and moments" added the song to the Library (even with history Off) | Page opens without a row; the row is made on the first note, favorite or moment | |
| A3 | Audit | question | README.md:39, RELEASE-NOTES.md:30, Panels.kt:93,426 | Copy says only title, artist and position are read; album, length and cover are read too | asked (Q2) | |
| A4 | Audit | mechanical | LibraryShare.kt:302 | Restore message didn't show plays before (device test 7 expects it) | Added "(was N)" | |
| A5 | Audit | mechanical | Panels.kt:201 | Stale Settings comment | Updated | |
| B1 | Audit | question | 21 files | Owner's name in public code comments and scripts | asked (Q3) | |
| B2 | Audit | question | CLAUDE.md | Public CLAUDE.md shows local paths and backup drive labels | asked (Q4) | |
| B3 | Audit | question | Player.kt:42, LyricsActivity.kt:347 | Home details in comments (where a device sits, device models) | asked with Q3 | |
| B4 | Audit | question | LyricsStore.kt | Lyric cache is plain JSON at rest (since 0.1, disclosed in the app) | asked (Q5) | |
| B5 | Audit | question | Panels.kt:93 | Welcome says nothing else leaves; typed searches also go to lrclib.net | asked with Q2 | |
| B6 | Audit | dropped | LibraryDbTest.kt:114 | Templated SQL in a test, constant table names only | Not a rule breach | |
| D1 | Audit | question | LibraryKey.kt:27 | Lost Keystore key: Library, Export and Delete crash every time, no way out | asked (Q6) | |
| D2 | Audit | question | LibraryKey.kt:34 | Key file missing but DB present: writes a key that can't open it (same crash) | asked with Q6 (needs the same recovery path) | |
| D3 | Audit | mechanical | LyricsActivity.kt:450 | Moment sheet said "Saved." even when saving failed | Shows "Saved." only on success, else "Couldn't save that moment. Try again?" | |
| D4 | Audit | question | History.kt:160 | Restoring after Delete the Library or Delete history restores nothing | asked (Q7) | |
| D5 | Audit | mechanical | LibraryShare.kt:255 | A failed backup left a partial file while saying nothing changed | Encrypts fully first; deletes the file on a write failure | |
| D6 | Audit | mechanical | LibraryBackup.kt:80 | Damaged file reported as wrong passphrase | Message says "or the file is damaged or incomplete" | |
| D7 | Audit | mechanical | LibraryBackup.kt:71 | Header could demand 1 GiB for Argon2 | Capped at 256 MiB (writes 64) | |
| D8 | Audit | mechanical | LibraryBackup.kt:51 | GCM IV left to the provider, reader assumes 12 bytes | Explicit random 12-byte IV | |
| D9 | Audit | mechanical | LibraryShare.kt:307 | Raw exception text shown for damaged backups | Dedicated Unreadable type; others say "This backup can't be read." | |
| D10 | Audit | mechanical | Library.kt:252 | Late-arriving length restarted the listen (lost seconds) | Tracker follows the song without its length; new test | |
| D11 | Audit | question | Library.kt:270 | App switch splits a listen; tracks over 8 min can count twice | asked (Q8) | |
| D12 | Audit | question | Library.kt:252 | Repeat-one counts N loops as one play | asked with Q8 | |
| D13 | Audit | question | History.kt:126 | Forget while playing: the song comes back when it ends | asked (Q9) | |
| D14 | Audit | question | History.kt:97 | Forget misses cached lyrics of uncounted versions | asked with Q9 | |
| D15-D18 | Audit | question | LibrarySnapshot.kt, Library.kt, History.kt | Sync-readiness gaps (per-field timestamps, tombstones by uid only, matchKey tied to code, tombstones never pruned) | asked (Q10) | |
| D19 | Audit | question | LibraryShare.kt:214 | Shared export file lingers in cache | asked with Q10 | |
| C1 | Audit | mechanical | licenses/, README.md | SQLCipher (BSD) and Bouncy Castle (MIT) notices missing from a public binary release | Added both license files and Credits lines | |
| C2 | Audit | question | LyricsActivity.kt:179 | Session-only players may be invisible (package visibility) | asked (Q11) | |
| C3 | Audit | question | LyricsActivity.kt:168 | Any app can trigger OPEN_PLAYER (since 0.2, low impact) | asked with Q10 | |

## Questions

All asked and answered 2026-10-11 (Stage 1).

| Q | Findings | Answer (owner's pick, word for word) | Follow-up |
|---|---|---|---|
| Q1 | A1 | "Whatever is playing (Recommended)" | No pick follows whatever plays; Spotify fallback and its `<queries>` entry removed |
| Q2 | A3, B5 | "Yes, list everything (Recommended)" | Copy fixed in README, release notes, Welcome and Notification access; exact text shown to the owner first |
| Q3 | B1, B3 | "Make generic (Recommended)" | Owner name and home details out of comments and scripts; pushed history stays |
| Q4 | B2 | "Make it generic (Recommended)" | CLAUDE.md without paths or drive labels |
| Q5 | B4 | "Encrypt in 0.4.0" | Lyric cache encrypted with a Keystore-wrapped key; existing cache migrated on first start |
| Q6 | D1, D2 | "Explain + start fresh (Recommended)" | "Library key is gone" state; old database renamed aside; restore from backup |
| Q7 | D4 | "Delete = full reset (Recommended)" | Delete the Library and Delete history wipe data and their markers; Forget keeps its marker |
| Q8 | D11, D12 | "Fix split + double (Recommended)" | Returning to the same song continues the listen; repeat-one stays one play |
| Q9 | D13, D14 | "Fix both (Recommended)" | Forget drops the in-progress listen and every cached version by title and artist |
| Q10 | D15-D19, C3 | "File for the sync work (Recommended)" | Filed in scarlet-hq IDEAS as proposed, tagged for the Command link; exports older than a day cleared at start |
| Q11 | C2 | "Check in device tests (Recommended)" | Line added to 0.4.0-DEVICE-TESTS.md |

## Ship

Not offered (dry run).
