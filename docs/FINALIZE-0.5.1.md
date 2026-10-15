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
| 3 | Cleanup | pending | |
| 4 | QA | pending | |
| 5 | Smoke test | pending | |
| 6 | Ship | pending | |
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

## Questions

| Q | Finding | Answer | Follow-up |
|---|---|---|---|
| Q1 | A1: Missed it | "Keep it in both (Recommended)" | Fix lines and Line up now; nothing moves on by the clock |
| Q2 | A2: the nudge limit after a big move | "Never past 10 s further (Recommended)" | YourTiming.nudged |
| Q3 | A9: verse breaks in a share with skipped lines | "Accept for now (Recommended)" | None |
| Q4 | A10: the new text | "Yes, as written (Recommended)" | As shown |
| Q5 | R9: versions with no title or artist (the owner: "If they have no title or artist, then we can't edit it right? It would have to be new. So I guess it depends how much is missing and whether other records exist that have it and those should be corrected instead") | "Point to a named version (Recommended)" | A missing album alone is fine; no title or artist: no Share, and "This version has no title or artist on LRCLIB, so no one would find a fix shared to it. To share, pick a named version in Other versions and fix that one." |

## Ship

| Step | Offered | Choice | Result |
|---|---|---|---|
| Install on devices | | | |
| Signed public build | | | |
| GitHub release (draft) | | | |
| Website update | | | |

## Wrap-up

- Backups:
- STATUS-AUDIT updated:
- hq-log:
