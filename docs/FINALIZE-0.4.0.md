# Finalize Scarlet Lyrics 0.4.0

Started 2026-10-10. Base: v0.2.0 (75718c7). Diff: 37 files, +3735 -162.
Mode: dry run (Stages 0 to 3; the first run of /finalize). Public: yes. Dry run complete 2026-10-11. Full run resumed 2026-10-11 at Stage 4.

## Stages

| # | Stage | Status | Notes |
|---|---|---|---|
| 0 | Preflight | done | 0.4.0 / versionCode 3 already bumped in 7b7d0e6 (base is 0.2.0 / 2). Tree clean apart from the smoke build type and smoke.ps1, committed as setup. |
| 1 | Audit | done | 4 agents (spec, rules, manifest, data). 11 mechanical fixes, 11 questions answered and applied. Re-check: 52/52 unit tests, R8 release build, androidTest compile, rule greps clean. One regression from the D10 fix was caught by an existing test and fixed. |
| 2 | Code review | done | 3 review agents by area + 1 security agent. 22 mechanical fixes, 4 questions answered and applied; 53/53 tests, R8 release build and androidTest compile pass. 5 regressions from Stage 1 work caught (R1, R5, R6, R7, S2). |
| 3 | Cleanup | done | Lint (no errors; new warnings fixed, older style warnings left), cleanup agent (15 of 16 applied), no TODOs or commented-out code, TUNING off in release, docs match. 53/53 tests, R8 release build, androidTest compile. |
| 4 | QA | done | Pass 1 (after the QA1 fix): 54/54 unit tests, lint no new issues, release, personal and smoke builds, APK checks (0.4.0 / 3, not debuggable, permissions as 0.2.0), labtest 8/8 on the tablet; QA2 found. Pass 2 (after the QA2 fix): all the same, APK 17.4 MB with arm64-v8a, armeabi-v7a, x86_64; clean. Pass 3 (after the Stage 5 fix SM1): all the same; clean. Reopened for the owner's navigation report (QA3 to QA6, built as a slice: checklist pass, review agent with 3 fixes L-1 to L-3 and 3 nits applied). Pass 4: 56/56 unit tests, lint no new issues, all three builds, APK checks, labtest 8/8; bar run on the phone emulator; clean. |
| 5 | Smoke test | running | Part 1 done: smoke copy on the Pixel 8, small phone (font 1.3) and Pixel tablet emulators, light and dark, rotation, home/back and return; no crash or ANR. The lyrics screen itself can't be driven on an emulator (no player there reads a test file's tags), so it is covered by the owner's device tests, including the end note (QA1). SM1 fixed, QA re-run (pass 3), landscape re-checked. Part 2: personal build 0.4.0+35be215 installed on the phone and tablet with the owner's OK (versions.ps1 confirms); end-note test line added to 0.4.0-DEVICE-TESTS.md 7b (owner: "Yes, as written"); owner checklist and Lyrics motion tuning in progress. |
| 6 | Ship | pending | |
| 7 | Wrap-up | pending | |

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
| R1 | Review | mechanical | LyricsActivity.kt:177 | With no app picked, Command's Listen opened nothing (what's playing wasn't known yet); regression from Q1 | Opens once playback is known (onStart), or at once if already on screen | |
| R2 | Review | mechanical | LyricsStore.kt | Startup sealing could race a nudge or pick on the same file | One process-wide lock for every cache read-modify-write | |
| R3 | Review | question | NowPlaying.kt:59, History.kt:87 | Switching history mode mid-song drops that listen | asked (Q13) | |
| R4 | Review | mechanical | LyricsActivity.kt:262 | Font size or keyboard change reset the open page and moment sheet | Panel, song page and moment state are saveable | |
| R5 | Review | mechanical | LyricsStore.kt:33,95 | A passing Keystore error deleted cache files (picks and nudges lost); regression from Q5 | Only a file that can never be read is deleted | |
| R6 | Review | mechanical | Library.kt startFresh, LibraryKey.kt | Start fresh deleted the old key, so the set-aside Library could never open; a passing error counted as key gone | Key set aside with the database; Gone only for a key that's really gone; stray journal files handled | |
| R7 | Review | mechanical | History.kt forget | Forget's cache scan ran on the main thread; a failure crashed | File work on IO; failure shows a message | |
| R8 | Review | mechanical | LibraryKey.kt, LibraryUi.kt | Corrupt key entry or short key file crashed the Library screen every time | Treated as gone; any other open failure shows "try again" | |
| R9 | Review | question | Library.kt PlayTracker | Continuing a listen after Off/Pause or a failed write; continuity lost when the recorder is recreated | asked with Q13 | |
| R10 | Review | mechanical | LibraryShare.kt saveTo | After process death in the save picker, a backup could be written with an empty passphrase | Refuses without a passphrase, removes the empty file; write() requires one | |
| R11 | Review | mechanical | LibraryUi.kt SongPage | First save on a pending song threw away an unsaved note and could save it empty; brief blank page | Draft keyed to the page; pending row shown until the real one arrives | |
| R12 | Review | mechanical | LibraryUi.kt | Double tap saved a moment twice | One save per tap | |
| R13 | Review | question | LyricsActivity.kt onSave | Moment goes on the next song if the song changes while typing the label | asked (Q14) | |
| R14 | Review | mechanical | LibraryShare.kt Export | Key gone or open failure showed a blank Export page | Says why | |
| R15 | Review | mechanical | LibraryShare.kt OnThisDevice | Unopenable Library listed as "empty" | Says it can't be opened right now | |
| R16 | Review | mechanical | LibraryShare.kt | "1 songs", "1 plays" | Plurals | |
| R17 | Review | mechanical | LibraryUi.kt Stats | "No plays all time." | "No plays yet." | |
| R18 | Review | mechanical | LibraryShare.kt Export save | Failed export left an empty file | Removed on failure | |
| R19 | Review | mechanical | Chrome.kt, Look.kt | Icon buttons had no TalkBack labels | Play, Pause, More | |
| R20 | Review | question | Look.kt Washed(small), header icons | Touch targets under 48dp | asked (Q15) | |
| R21 | Review | mechanical | LyricsLines.kt, Panels.kt Choice | Long-press bookmark not announced; choices don't say which is on | Labels and selected state for TalkBack | |
| R22 | Review | mechanical | LibraryUi.kt | Double tap on Start a new Library set aside twice | One attempt at a time | |
| R23 | Review | question | LyricsActivity.kt:157 | With no app picked, opening Lyrics doesn't bring up the app that's playing | asked (Q12) | |
| S1 | Review | mechanical | LibraryExport.kt | Line breaks in other apps' titles could fake lines in the AI-ready text export | Control characters become spaces | |
| S2 | Review | mechanical | History.kt | Forgotten-song hashes sat in plain prefs (new in Stage 1) | Kept in memory only | |
| S3 | Review | mechanical | LibraryBackup.kt | A crafted backup could unpack to gigabytes | Size caps | |
| C4-C17 | Cleanup | mechanical | LibraryShare.kt, History.kt, LibraryBackup.kt, LibrarySnapshot.kt, Player.kt, LyricsStore.kt, LyricsActivity.kt, Library.kt | Misplaced KDoc, duplicate plural helper, double checks and double work, stale comment, invisible U+0001 in source, duplicated message strings, convoluted checks | Simplified, same behavior | |
| C18 | Cleanup | mechanical | 8 files | Import order broken by this run's own edits (repo uses Android Studio order: java, javax, kotlin last) | Restored | |
| C19 | Cleanup | mechanical | Panels.kt:220,223 | Lint: boxed Int state in new 0.4 code | mutableIntStateOf | |
| C20 | Cleanup | dropped | LyricsStore.kt CacheCipher | Near copy of LibraryKey's Keystore key setup | Kept apart: merging touches key creation for little gain | |
| C21 | Cleanup | question | LibraryShare.kt:431 | "Replaced at the next export" no longer the whole story | asked (Q16) | |
| QA1 | QA | question | LyricsLines.kt SyncedLyrics, Lrc.kt | Owner report (phone, 0.2.0): on a song whose LRCLIB record stops early (last sung line at 2:13 of 3:17, then one blank line at 3:14), the last line stayed current to the end over gap dots that never filled. Not a timing bug: 7 of the 9 synced records for that song stop there; 2 longer ones exist | asked (Q17, Q18); end note built (Lrc.withEnd, EndNote) with a unit test | |
| QA2 | QA | question | lyrics/build.gradle.kts | Release APK 22.4 MB against 2.2 MB in 0.2.0: SQLCipher's native library for four CPU types (about 19 MB); expected dependency (Library encryption) | asked (Q19); 32-bit x86 dropped (abiFilters) | |
| QA3 | QA | question | Chrome.kt:150 | Owner (device tests): "Wrong lyrics?" is the only way to look for a synced version, and the label doesn't fit that use | asked (Q20); renamed "Other versions" in the app, README and release notes | |
| QA4 | QA | question | Panels.kt Picker | Owner: no visible way back from the picker (its Close sat next to Search; no page title) | asked (Q21); now-playing bar on every page, page title "Other versions" | |
| QA5 | QA | question | Chrome.kt Header | Owner: the way back should show the song's name and cover | asked (Q21); the bar shows the cover, title and artist | |
| QA6 | QA | question | LyricsActivity.kt panels | Owner: moving between screens should be easier (Close and Back buttons in a different place on every page) | asked (Q21, Q22); one bar, one step back each tap (Panel.back, unit tested); per-page Close and Back buttons removed; built as a slice | |
| L-1 | QA (slice) | mechanical | LyricsActivity.kt Flex mode | Review: in Flex mode the bar sat above a page that was still fold-height, pushing the page past the hinge and squeezing the controls below | Page gives up the bar's height | |
| L-2 | QA (slice) | mechanical | Chrome.kt Header | Review: with nothing playing the bar said "Back to Lyrics" even where it goes to Settings or the Library | "Back to Settings" / "Back to Library" where that's where it goes | |
| L-3 | QA (slice) | mechanical | Chrome.kt Header | Review: the bar's tap area could be under 48dp | At least 48dp | |
| SM1 | Smoke | mechanical | Panels.kt:69 EmptyState | Phone in landscape: "Nothing playing" and the Library's "Nothing here yet" squeezed their button to a sliver and cut text (small phone at font 1.3 worst) | Empty states scroll, like Welcome; re-checked in landscape | |

## Questions

Stage 1: Q1 to Q11. Stage 2: Q12 to Q15. Stage 3: Q16. Stage 4: Q17 to Q22. All answered 2026-10-11. QA findings use ids QA1, QA2... and smoke findings SM1, SM2... so they don't clash with question numbers or the review's S ids.

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
| Q12 | R23 | "Yes, the playing app (Recommended)" | With no app picked, opening Lyrics brings up whatever is playing |
| Q13 | R3, R9 | "Fix all three" | One shared listen for both recorders: a mode switch hands it over, Off or Pause ends it for good, a recreated screen carries on |
| Q14 | R13 | "Save to the pressed song (Recommended)" | The moment goes on the song whose line was long-pressed |
| Q15 | R20 | "Raise to 48dp (Recommended)" | Small buttons and header icons get a 48dp tap area, same look |
| Copy | Stage 2 text | "Yes, as written" | Applied |
| Q16 | C21 | "Update it (Recommended)" | "Replaced at the next export and removed after a day; Android can clear it sooner." (the existing Android clause kept) |
| Q17 | QA1 | "End note + link (Recommended)" | About 10 s after the last sung line (or at a blank line sooner), a soft note "That's all the lyrics this version has." with a "Try another version" button that opens Wrong lyrics? |
| Q18 | QA1 | "Leave for after 0.4.0 (Recommended)" | Preferring the fuller record when several match: filed in scarlet-hq IDEAS as proposed at hq-log |
| Q19 | QA2 | "Drop 32-bit x86 only (Recommended)" | abiFilters arm64-v8a, armeabi-v7a, x86_64 |
| Q20 | QA3 | "Other versions (Recommended)" | Row: "Other versions" / "Find a synced or different version, or search by hand."; page title the same |
| Q21 | QA4, QA5, QA6 | "Now-playing bar (Recommended)" | Back arrow, cover, title and artist at the top of every page; a tap goes back a step; "Back to Lyrics" with nothing playing; scattered Close buttons removed |
| Q22 | QA4 to QA6 | "In 0.4.0 (Recommended)" | Built as a slice (checklist pass, review agent), then QA in full and the owner re-checks the affected device tests |

## Ship

Not offered (dry run).
