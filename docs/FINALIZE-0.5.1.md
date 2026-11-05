# Finalize Cawback 0.5.1

Started 2026-10-15. Base: v0.5.0 (8530af6). Diff: 30 files, +1077 -183 (before the version bump).
Mode: full. Public: yes.

Built in six slices with /prep (`docs/LYRICS-0.5.1-PLAN.md`, answers Q47 to Q60 in `docs/LYRICS-DECISIONS.md`), each with a `--slice` mini-audit (L1-1 to L6-3).

## Stages

| # | Stage | Status | Notes |
|---|---|---|---|
| 0 | Preflight | done | Tree clean, 8 commits ahead of origin (the plan and the six slices). Version: 0.5.1 as the owner chose (Q55); versionCode 5 to 6. gh signed in; the tablet connected (the phone not); emulators scarlet_phone, scarlet_small, scarlet_tablet. |
| 1 | Audit | done | Merged release manifest the same as 0.5.0 (one INTERNET permission, same components and queries); dependencies unchanged. 4 agents (decisions, hard rules, manifest and dependencies, data safety): every decision Q47 to Q60 in the code; nothing from LATER or open IDEAS built; network only to lrclib.net; no logging of user text; no em dashes; no personal data; upgrade from 0.5.0 keeps everything, nothing deletable on the first run, backups 1 to 3 restore. 5 mechanical fixes (A3 to A5, A7, A8; A6 at hq-log), 4 questions (Q1 to Q4) answered and applied. 142 unit tests, build and the release manifest pass. |
| 2 | Code review | done | 4 agents (data; screens; security; the Android pass). 12 mechanical fixes (R1 to R8, R10 to R13), 1 question (Q5, answered); a re-review found 3 more (R14 to R16), a second re-review clean. R17 noted. 145 unit tests, build passes. |
| 3 | Cleanup | done | simplify (4 agents: reuse, simplification, efficiency, altitude) and own checks: no TODOs, no commented-out code, TUNING off in release, no em dashes, licences match the dependencies. 9 applied (C1 to C9; C3 was a real gap: Clear my timing didn't show for skipped lines alone), others skipped (C10). Release notes drafted (C11, Q6). Build, release build, 145 unit tests. |
| 4 | QA | done | Pass 1 (at e7418e5): 145/145 unit tests (debug and release); lintRelease 0 errors but two new UseKtx warnings (Q1-1, fixed). Pass 2 (at 625259b), clean: 145/145 (debug and release); lintRelease 0 errors, 38 warnings, all older than the base; release, personal and smoke builds pass (R8); release APK 0.5.1 / 6, label Cawback, no suffix, not debuggable, permissions as 0.5.0, 9.68 MB unsigned (0.5.0: 9.65 MB signed); labtest 8/8 on the tablet (test copy removed). Pass 3 (at ba6502e, after the owner's "Clear my changes" rename, D-064, a code change after QA), clean: 145/145 (debug and release); lintRelease 0 errors, 39 warnings (the one more than pass 2 is a dependency-version notice, nothing in the changed files); release, personal and smoke builds pass; release APK 0.5.1 / 6, label Cawback, no suffix, not debuggable, permissions as 0.5.0, 9.68 MB unsigned; labtest 8/8 on the tablet (test copy removed). |
| 5 | Smoke test | done (Part 2 pending: it gates only Publish, the owner 2026-11-04) | Part 1 (rule 11), smoke copy 810653d with the emulator-only stand-in player, light and dark, on phone (with the upgrade from a 0.5.0 smoke copy built from its tag: a 0.50 s nudge kept), small phone at font 1.3, tablet, and phone at 200%: Fix lines shows Now, Not in this song, Missed it and Done; Missed it moves to the next line; Not in this song hides the line and moves past it; the place in Fix lines survives the app being killed and restored; This song shows the timing, Clear my timing and Share (rows below the fold at large fonts, the sheet scrolls); the Share preview carries the version's names; Player: no Bluetooth or Google, "More apps" folds and unfolds; Privacy: the cleanup setting and its note, On and Off; About: Open source licenses lists six parts, the Apache text opens, Back returns to the list. No crash or ANR. Not covered on emulators: the Library's Songs and Stats tabs (need real plays), TalkBack speech for the new buttons (labels and roles checked in code and the UI tree), the note for a version with no title or artist (needs such a record), a real cut song by ear. Part 2: 0.5.1-DEVICE-TESTS.md.. Part 1 again for the rename (2026-11-04, smoke copy at ba6502e with an emulator-only stand-in player, phone): with no change the button is not shown; after Later it reads "Clear my changes", the confirm line is word for word D-064, Cancel keeps the change, Clear puts the timing back and the button goes; the same at 200% font in dark. No crash. Part 2 handed to the owner in the chat 2026-11-04. |
| 6 | Ship | done (Publish and the site push wait on Part 2, the owner 2026-11-04) | Install: both devices already 0.5.1+ba6502e (dac54fa adds only this report). Pushed, signed by the owner, signature and upgrade checked, tag v0.5.1 on dac54fa, draft release, merged to main (PR #4, 5577a7a). |
| 7 | Wrap-up | pending | |

## Findings

| Id | Stage | Label | Where | Finding | Done | Commit |
|---|---|---|---|---|---|---|
| A1 | Audit | question | Chrome.kt FixBar | "Missed it" was added while building (L5-1) and replaces 0.5.0's move past a missed line in Line up now too; never the owner's decision | Q1: kept in both | stage 1 |
| A2 | Audit | question | LyricsRepo.kt nudge | Earlier and Later worked within plus or minus a big Line up now move, against "Earlier and Later stay at 10 s" | Q2: never further out, never more than 10 s past zero (YourTiming.nudged; JVM test) | stage 1 |
| A3 | Audit | mechanical | README.md:40 | Said a share sends "that song's" details; since Q47 it's the chosen version's | "the chosen version's title, artist, album and length, and its lyrics with your timing (lines you marked \"Not in this song\" left out)" | stage 1 |
| A4 | Audit | mechanical | LibraryShare.kt Sent to lrclib.net | Same as A3 in What's on this device | "the chosen version's details" | stage 1 |
| A5 | Audit | mechanical | LibraryShare.kt Saved lyrics | The cleanup sentence left out that the user's picks and timing are always kept (the README says it) | Added | stage 1 |
| A6 | Audit | mechanical | scarlet-hq IDEAS.md | Five ideas built in 0.5.1 still read proposed or parked | At hq-log | |
| A7 | Audit | mechanical | FINALIZE-0.5.1.md, LYRICS-LOGGER-PLAN.md | A device model named in a public doc | "the phone" | stage 1 |
| A8 | Audit | mechanical | AndroidManifest.xml comment | Still said Lyrics | Cawback | stage 1 |
| A9 | Audit | question | ShareFix.kt body | With skipped lines the shared plain lyrics lose the plain record's verse breaks | Q3: accepted for now | |
| A10 | Audit | question | the new text | All new on-screen and privacy wording, word for word | Q4: as written | |
| R1 | Review | mechanical | LyricsStore.kt decode | Songs saved by 0.5.0 kept a missing LRCLIB name as the word "null"; a share would send it and miss the version (Q47) | Read as empty; JVM test | stage 2 |
| R2 | Review | mechanical | LyricsRepo.kt nudge | Earlier or Later at the limit changed nothing but cleared "Sent on" | Nothing saved at the limit | stage 2 |
| R3 | Review | mechanical | YourTiming.kt stepAt, apply | Finding each jump point scanned every line for every line: a huge record could freeze the app | Binary search; each jump point once per pair of tapped lines; JVM test | stage 2 |
| R4 | Review | mechanical | Panels.kt MusicAppSettings | Asking Android about every app ran on the main thread, again on each rotation | Loaded off the main thread; playing apps read on resume | stage 2 |
| R5 | Review | mechanical | Panels.kt MusicAppSettings | The apps kept in view during a visit were lost on rotation, and added during composition | Saved, added on tap | stage 2 |
| R6 | Review | mechanical | LibraryUi.kt, LyricsActivity.kt | The Library tab went back to Songs after opening a song | The tab lives with the screen | stage 2 |
| R7 | Review | mechanical | Panels.kt AppChoices, Look.kt Washed | "More apps" didn't say it folds; Washed buttons had no button role for TalkBack | Expanded / Collapsed; Role.Button | stage 2 |
| R8 | Review | mechanical | Chrome.kt FixBar | Skipping a line that shares its time with another jumped one line too far | Resolved by the skipped line's time | stage 2 |
| R9 | Review | question | ShareFix.kt, Chrome.kt SongSheet | A version with no title or artist would be shared to a record no lookup finds | Q5: Share not offered; a note points to a named version | stage 2 |
| R10 | Review | mechanical | Chrome.kt FixBar | A Now save landing during a skip brought the skipped line back on screen | The skip resolves only when its own line is among the skipped | stage 2 |
| R11 | Review | mechanical | Chrome.kt FixBar | A rotation or font change in Fix lines lost the place (after a cut the clock can't find it) | Kept per version | stage 2 |
| R12 | Review | mechanical | Chrome.kt FixBar | A skip saved while the version changed left the buttons hidden | State keyed on the version | stage 2 |
| R13 | Review | mechanical | LyricsActivity.kt, Panels.kt | (re-review) Two comments out of place | Moved | stage 2 |
| R14 | Review | mechanical | Panels.kt MusicAppSettings | (re-review) The app chosen before a new choice could drop into "More apps" mid-visit | The old choice is kept too | stage 2 |
| R15 | Review | mechanical | LyricsRepo.kt nudge | (re-review) At the limit the screen still dropped "Sent on" until a reload | null at the limit; the screen isn't touched | stage 2 |
| R16 | Review | mechanical | Chrome.kt FixBar | (re-review) A font change during a skip save could leave the buttons hidden | Not saved across a rebuild | stage 2 |
| R17 | Review | noted | LyricsStore.kt | Whole-store rewrites (Reset all timing, a restore) mark every song as used | Keeps more, never less; left as is | |
| C1 | Cleanup | mechanical | Chrome.kt FixBar | The same four-part guard written three times; a copied state | One canTap; YourTiming.nextLine reused to find the line after a skip | stage 3 |
| C2 | Cleanup | mechanical | Chrome.kt SongSheet | tapped checked twice | Once | stage 3 |
| C3 | Cleanup | mechanical | Chrome.kt SongSheet | Clear my timing only showed with a nudge or tapped lines: lines hidden on their own could never be brought back | Shows for skipped lines too | stage 3 |
| C4 | Cleanup | mechanical | Licenses.kt | reflow's doc above the regex | Moved | stage 3 |
| C5 | Cleanup | mechanical | YourTiming.kt | apply and offsetAt each had the base-plus-move clamp | One private offset() | stage 3 |
| C6 | Cleanup | mechanical | LyricsRepo.kt found, linesOf | Each song's lyrics parsed twice to know if they're synced | One parse | stage 3 |
| C7 | Cleanup | mechanical | Panels.kt, LibraryUi.kt | clock fully qualified twice and a forwarding wrapper; chosen.ifEmpty written three times; one label not using SoftText | Imported; chosenPkg; SoftText | stage 3 |
| C8 | Cleanup | mechanical | LyricsStore.kt load | Every read wrote the file's time (also right before a save) | Only when the mark is over a day old (90-day cleanup) | stage 3 |
| C9 | Cleanup | mechanical | LyricsStore.kt | The "has the user's timing" test written four times, slightly differently | Entry.hasTiming used by isYours, fixOf and merged | stage 3 |
| C10 | Cleanup | skipped | LyricsActivity.kt, Chrome.kt, Panels.kt, LyricsStore.kt | Skip and clear results copied onto the screen instead of returned whole; the Fix lines target kept by time instead of index (would change lines sharing a time); one null-name helper for two different jobs; the share job's copied fields (kept on purpose, finalize 0.5.0 C8); one store instance at startup; lazy times in apply; the share body built twice | Larger restructuring or a behaviour change this late; proposed for later | |
| C11 | Cleanup | question | docs/release-0.5.1/RELEASE-NOTES.md | Release notes draft | Q6: approved | stage 3 |
| Q1-1 | QA | mechanical | Player.kt | lintRelease: two new UseKtx warnings since the base (the two new settings writes) | Every settings write in the file uses the KTX edit { } (same behaviour; 13 older warnings gone too) | QA |

## Questions

| Q | Finding | Answer | Follow-up |
|---|---|---|---|
| Q1 | A1: Missed it | "Keep it in both (Recommended)" | Fix lines and Line up now; nothing moves on by the clock |
| Q2 | A2: the nudge limit after a big move | "Never past 10 s further (Recommended)" | YourTiming.nudged |
| Q3 | A9: verse breaks in a share with skipped lines | "Accept for now (Recommended)" | None |
| Q4 | A10: the new text | "Yes, as written (Recommended)" | As shown |
| Q5 | R9: versions with no title or artist (the owner: "If they have no title or artist, then we can't edit it right? It would have to be new. So I guess it depends how much is missing and whether other records exist that have it and those should be corrected instead") | "Point to a named version (Recommended)" | A missing album alone is fine; no title or artist: no Share, and "This version has no title or artist on LRCLIB, so no one would find a fix shared to it. To share, pick a named version in Other versions and fix that one." |
| Q6 | C11: the 0.5.1 release notes, title "Cawback 0.5.1: Cuts and corrections" | "Yes, as written (Recommended)" | Used for the GitHub release |

## Ship

| Step | Offered | Choice | Result |
|---|---|---|---|
| Install on devices | 2026-10-15, in Stage 5 | "phone and tablet now, I plugged in both" | Both devices 0.5.1+4905b0a (the personal APK install.ps1 builds; the first install.ps1 run stalled at the phone install and was stopped, then adb install -r on each device); 2026-11-04, laptop install session after the independent review: in-app Library backups from both devices copied off and checked, a 2026-10-11 backup restored with the new build on an emulator, then both devices 0.5.1+ba6502e (adb install -r of the personal APK) |
| Signed public build | 2026-11-04: push, then release.ps1 by the owner | "Yes, push it (Recommended)" (release-0.5.1 at dac54fa) | The owner signed with release.ps1. dist/cawback-0.5.1.apk (and the same file as cawback.apk): v3 signature verifies; certificate SHA-256 b2388659...1acacd, the same as 0.5.0; 0.5.1 / 6, label Cawback, no suffix, not debuggable, INTERNET only; both .sha256 files match (80e43d5f...ee22a1); 9.70 MB. Upgrade test on the phone emulator: signed 0.5.0 with a 0.25 s nudge, then signed 0.5.1 over it: the nudge and notification access kept, Clear my changes shown, no crash; both removed after |
| GitHub release (draft) | 2026-11-04: the notes as approved in Q6 plus the D-064 line | "Yes, as written (Recommended)"; merge: "PR, merge commit (Recommended)" | Tag v0.5.1 on dac54fa pushed; draft "Cawback 0.5.1: Cuts and corrections" with cawback-0.5.1.apk, cawback.apk and their .sha256; 0.5.0 stays Latest. PR #4 merged release-0.5.1 into main (5577a7a). Publish waits on the owner's device tests (Stage 5 Part 2) |
| Website update | | | |

## Wrap-up

- Backups:
- STATUS-AUDIT updated:
- hq-log:
