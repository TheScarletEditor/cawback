# Finalize Cawback 0.5.1

Started 2026-10-15. Base: v0.5.0 (8530af6). Diff: 30 files, +1077 -183 (before the version bump).
Mode: full. Public: yes.

Built in six slices with /prep (`docs/LYRICS-0.5.1-PLAN.md`, answers Q47 to Q60 in `docs/LYRICS-DECISIONS.md`), each with a `--slice` mini-audit (L1-1 to L6-3).

## Stages

| # | Stage | Status | Notes |
|---|---|---|---|
| 0 | Preflight | done | Tree clean, 8 commits ahead of origin (the plan and the six slices). Version: 0.5.1 as the owner chose (Q55); versionCode 5 to 6. gh signed in; the tablet connected (the phone not); emulators scarlet_phone, scarlet_small, scarlet_tablet. |
| 1 | Audit | done | Merged release manifest the same as 0.5.0 (one INTERNET permission, same components and queries); dependencies unchanged. 4 agents (decisions, hard rules, manifest and dependencies, data safety): every decision Q47 to Q60 in the code; nothing from LATER or open IDEAS built; network only to lrclib.net; no logging of user text; no em dashes; no personal data; upgrade from 0.5.0 keeps everything, nothing deletable on the first run, backups 1 to 3 restore. 5 mechanical fixes (A3 to A5, A7, A8; A6 at hq-log), 4 questions (Q1 to Q4) answered and applied. 142 unit tests, build and the release manifest pass. |
| 2 | Code review | pending | |
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

## Questions

| Q | Finding | Answer | Follow-up |
|---|---|---|---|
| Q1 | A1: Missed it | "Keep it in both (Recommended)" | Fix lines and Line up now; nothing moves on by the clock |
| Q2 | A2: the nudge limit after a big move | "Never past 10 s further (Recommended)" | YourTiming.nudged |
| Q3 | A9: verse breaks in a share with skipped lines | "Accept for now (Recommended)" | None |
| Q4 | A10: the new text | "Yes, as written (Recommended)" | As shown |

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
