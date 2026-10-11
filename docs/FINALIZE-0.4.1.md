# Finalize Scarlet Lyrics 0.4.1

Started 2026-10-11. Base: v0.4.0 (370a5a6). Diff: 15 files, +966 -291 (before the version bump).
Mode: full. Public: yes.

Built in three slices with /prep (`docs/LYRICS-0.4.1-PLAN.md`, answers Q1 to Q14 in `docs/LYRICS-DECISIONS.md`), each with a `/finalize --slice` mini-audit. The owner's device tests on 0.4.0+b8717cf (both devices): "tests pass".

## Stages

| # | Stage | Status | Notes |
|---|---|---|---|
| 0 | Preflight | done | Tree clean, in sync. Version: owner picked "0.4.1 (Recommended)"; versionCode 3 to 4. gh signed in; both devices connected; emulators scarlet_phone, scarlet_small, scarlet_tablet. |
| 1 | Audit | done | Release manifest: same permissions as 0.4.0 (INTERNET and the dynamic-receiver permission), no new components, backups still excluded. 4 agents (decisions, hard rules, manifest and dependencies, data safety): A and C clean; B 3 doc wordings; D 4 findings. 6 fixed, 1 question (Q2). Re-check: build, 71/71 unit tests, lint 0 errors (49 older warnings), greps clean. |
| 2 | Code review | done | 3 agents (lyrics and storage, Settings and work, security). Security clean (one note, no change). 6 findings fixed (R1 to R5, R7), 1 regression from a fix caught by re-review and fixed (R6), re-reviews clean. Emulator (small phone): landscape short window scrolls heading and tabs away; an open confirmation survives a rotation that flips the compact layout; font 2.0 tabs and choice rows wrap without breaking words. Build, 71/71 unit tests, lint 0 errors (49 older). |
| 3 | Cleanup | done | simplify (4 agents: reuse, simplification, efficiency, altitude) and own checks: no TODOs, no commented-out code, TUNING off in release, no em dashes, licenses unchanged (no new dependencies). 6 applied (C1 to C6), 2 skipped (C7, C8), release notes drafted (C9), 2 questions (Q3, Q4). Build, 71/71 unit tests, lint 0 errors (49 older). |
| 4 | QA | pending | |
| 5 | Smoke test | pending | |
| 6 | Ship | pending | |
| 7 | Wrap-up | pending | |

## Findings

| Id | Stage | Label | Where | Finding | Done | Commit |
|---|---|---|---|---|---|---|
| A1 | Audit | mechanical | docs/LYRICS-0.4.1-PLAN.md:17 | The state of the owner's backup drives in a public repo (D-034) | Now "Backups: not yet verified." | stage 1 |
| A2 | Audit | mechanical | docs/FINALIZE-0.4.0.md, Stage 7 row | Same, in the quoted skip | Quote shortened to the skip itself | stage 1 |
| A3 | Audit | mechanical | docs/FINALIZE-0.4.0.md, Stage 5 row | Emulator device model names | "phone, small phone and tablet emulators" | stage 1 |
| A4 | Audit | dropped | README, code comments, install.ps1 | "flip phone" (feature wording), "a tablet as a bedside display" (use case), the standard Android Studio path | Generic, already reviewed in the 0.4.0 run; kept | |
| A5 | Audit | question | Panels.kt:463, LibraryShare.kt:300 | A delete could start during a restore and the other way round; the end state depended on which finished last, with contradicting messages | Q2: one Library job at a time (buttons hidden, and the runner refuses) | stage 1 |
| A6 | Audit | mechanical | LyricsStore.kt clear, forgetHashed | Deleted without the store's lock, so a reset running at the same moment could write a cleared file back | Each delete under the lock | stage 1 |
| A7 | Audit | mechanical | LibraryShare.kt saveTo | The "enter your passphrase again" path returned without wiping the passphrase array | Wiped | stage 1 |
| A8 | Audit | mechanical | LyricsStore.kt resetOffsets, clear; Panels.kt | Reset and Clear reported success when some files failed (checklist: a failure never shows success) | Both return whether every file succeeded; a partial failure shows the approved "That didn't finish. Try again." (Q9 of the plan) | stage 1 |
| R1 | Review | mechanical | LyricsStore.kt resetOffsets | A file forgotten or cleared during a reset counted as a failure ("That didn't finish. Try again." after a reset that worked) | Files gone meanwhile are skipped | stage 2 |
| R2 | Review | mechanical | LyricsLines.kt follow delay | Used the wall clock; a clock set back stopped the lyrics following until it caught up | Device uptime (elapsedRealtime) | stage 2 |
| R3 | Review | mechanical | Panels.kt SettingsPanel | In a short window (landscape split screen) the fixed heading and tabs left no room: no setting reachable | In the compact layout the heading and tabs scroll with the page | stage 2 |
| R4 | Review | mechanical | LibraryShare.kt saveTo | A backup refused (another Library job running) left an empty file and said nothing | The file is deleted and the approved "That didn't finish. Try again." shows | stage 2 |
| R5 | Review | mechanical | Panels.kt tab layout | At a very large font "Privacy" broke mid-word in two rows | One tab per row when a label doesn't fit half a row (extends Q12 of the plan); JVM test | stage 2 |
| R6 | Review | mechanical | Panels.kt (from the R3 fix) | Two different trees for the two layouts: a rotation with a file picker open lost the picker's result | One tree; only the scrolling modifiers change; a new tab starts at the top | stage 2 |
| R7 | Review | mechanical | Panels.kt choice rows (older than this release) | At font 2.0 "Light" (Theme) was squeezed to one letter per line; the same risk for other two- and three-choice rows | All choice rows wrap (FlowRow) | stage 2 |
| R8 | Review | dropped | LibraryShare.kt restore | Security note: the restore job keeps a reference to the screen's passphrase field for its few seconds | Informational: already in memory as the field's text, not reachable from outside; no change | |
| C1 | Cleanup | mechanical | SettingsWork.kt, Panels.kt, LibraryShare.kt | "One Library job at a time" was an exclusive list plus hand-written message clears in two places | Jobs have a group: busy per group, and starting one clears the group's older messages | 5bb1e56 |
| C2 | Cleanup | mechanical | SettingsWork.kt, Panels.kt | Success or failure was told apart by comparing the text with the failure message; TimingSettings and SavedLyricsSettings repeated the same confirm-and-run block | Results carry an ok flag; one ConfirmedWork | 5bb1e56 |
| C3 | Cleanup | mechanical | Panels.kt, LibraryShare.kt | Counts, the Library check and What's on this device reloaded when a job started too, and the whole tab redrew | Refresh only when a job finishes (SettingsWork.finished), read inside the section; no redraw when there's nothing to forget | 5bb1e56 |
| C4 | Cleanup | mechanical | Player.kt, LyricsLines.kt | The text size clamp was repeated where it's read | Clamped once at load; LyricsSettings.scale for both readers | 5bb1e56 |
| C5 | Cleanup | mechanical | LyricsStore.kt | The hash pattern was built for every file | One constant | 5bb1e56 |
| C6 | Cleanup | mechanical | Panels.kt tab layout | The one-row check written twice | One name | 5bb1e56 |
| C7 | Cleanup | skipped | LyricsStore.kt | Moving the lyrics-reload signal into the store | Would also reload the lyrics after "Forget this song" (a behaviour change) | |
| C8 | Cleanup | skipped | LyricsLines.kt | Folding the three placement flags into one effect | Works, reviewed twice and device-tested; a rewrite here is risk without a gain | |
| C9 | Cleanup | doc | docs/release-0.4.1/RELEASE-NOTES.md | No release notes for 0.4.1 | Drafted; owner OK'd (Q3) | 5bb1e56 |

## Questions

### Q1 (preflight)
Version: 0.4.1 (D-042) or 0.5.0 (the minor-bump rule).
**Answer (2026-10-11):** "0.4.1 (Recommended)"
**Follow-up:** versionName 0.4.1, versionCode 4.

### Q2 (audit)
A5: on the Library tab a delete could run during a restore (and the reverse); no corruption, but the result depended on timing and the messages could contradict each other. Options: one at a time (recommended), or leave it.
**Answer (2026-10-11):** "One at a time (Recommended)"
**Follow-up:** `SettingsWork.LIBRARY` (delete, backup, restore): while one runs the others' buttons are hidden and `WorkRunner.run(..., exclusive)` refuses them (since C1: the Library jobs share a group). JVM test `libraryJobsRunOneAtATime`. No new text.

### Q3 (cleanup)
Release notes draft for 0.4.1, shown word for word.
**Answer (2026-10-11):** "Yes, as written (Recommended)"
**Follow-up:** docs/release-0.4.1/RELEASE-NOTES.md as drafted.

### Q4 (cleanup)
Two tab styles: Settings (text with an underline) and the Library's Songs and Stats (pill buttons). Leave both, or change one in 0.4.1.
**Answer (2026-10-11):** "Leave both for now (Recommended)"
**Follow-up:** none in 0.4.1; "one tab style" filed for later.

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
