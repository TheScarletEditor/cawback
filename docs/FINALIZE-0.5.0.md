# Finalize Scarlet Lyrics 0.5.0

Started 2026-10-12. Base: v0.4.1 (3dde162). Diff: 24 files, +1615 -144 (before the version bump).
Mode: full. Public: yes.

"Your timing": built in seven slices with /prep (`docs/LYRICS-0.5.0-PLAN.md`, answers Q15 to Q27 in `docs/LYRICS-DECISIONS.md`), each with a `/finalize --slice` mini-audit (M1 to M43). The owner's device check on 0.4.1+69b34d2 (both devices, all seven slices): "tests pass".

## Stages

| # | Stage | Status | Notes |
|---|---|---|---|
| 0 | Preflight | done | Tree clean, one commit ahead of origin (the device-check note). Version: 0.5.0 as the owner chose in Q27 of the plan; versionCode 4 to 5. gh signed in; both devices connected; emulators scarlet_phone, scarlet_small, scarlet_tablet. |
| 1 | Audit | done | Release manifest: same permissions as 0.4.1 (INTERNET and the dynamic-receiver permission); manifest source and dependencies unchanged since v0.4.1. 4 agents (decisions, hard rules, manifest and dependencies, data safety). 6 mechanical fixes (A1 to A6), 8 questions (Q1 to Q8) all answered and applied, plus the new wording approved word for word. Build, 114/114 unit tests, lint 0 errors, no em dashes, no real artist names left in the repo. |
| 2 | Code review | pending | |
| 3 | Cleanup | pending | |
| 4 | QA | pending | |
| 5 | Smoke test | pending | |
| 6 | Ship | pending | |
| 7 | Wrap-up | pending | |

## Findings

| Id | Stage | Label | Where | Finding | Done | Commit |
|---|---|---|---|---|---|---|
| A1 | Audit | mechanical | AndroidManifest.xml:4 | The INTERNET comment named the wrong app and left out sharing | "lookups, and sharing a timing fix only after the user taps Send" | stage 1 |
| A2 | Audit | mechanical | MatchingTest.kt, LrcLib.kt comments, LYRICS-0.5.0-PLAN.md | Real artist credits, song lengths and the owner's by-ear check could tie the public repo to the owner's listening | Invented credits and lengths; plan lines generalised; details stay in scarlet-hq | stage 1 |
| A3 | Audit | mechanical | LibrarySnapshot.kt fixes | A hostile or broken backup could hold millions of anchors | 500 per fix, 200,000 in all | stage 1 |
| A4 | Audit | mechanical | LyricsStore.kt fixes() | A fix saved from a file older than the "s" field had no song key, so Forget couldn't reach it after a restore until it played | The song key is taken from the saved version too | stage 1 |
| A5 | Audit | mechanical | Panels.kt Share preview | Length cut to whole seconds (4:08.6 shown 4:08) | Rounded | stage 1 |
| A6 | Audit | mechanical | Chrome.kt, LyricsLines.kt | Three stale comments | Updated | stage 1 |
| A7 | Audit | question | LyricsRepo.kt isCurrent, keptTiming | A re-lookup that picks another record drops the user's nudge or fixed lines (0.4.1 nudges; timing added after an incomplete match) | Q1: applied | stage 1 |
| A8 | Audit | question | Panels.kt SharePanel, ShareFix.kt | The preview doesn't show plainLyrics, which is sent too | Q2: applied | stage 1 |
| A9 | Audit | question | README.md:41, LibraryShare.kt:440, README.md:34 | Privacy texts say picks and nudges go nowhere or are excluded from backups; since Q25 they're in a Library backup | Q3: applied | stage 1 |
| A10 | Audit | question | LibraryShare.kt:362 | "Your timing came back for N songs" counts fixes not applied yet (fetched on next play, maybe gone) | Q4: applied | stage 1 |
| A11 | Audit | question | LyricsRepo.kt:209 | A version picked by hand never shows the unsure dot, even if it runs past the song's end | Q5: applied | stage 1 |
| A12 | Audit | question | Panels.kt:409 | "Reset all timing nudges" also clears fixed lines and the Sent mark | Q6: applied | stage 1 |
| A13 | Audit | question | README.md:8-11 | What it does doesn't describe Your timing, This song, the new matching or Share | Q7: applied | stage 1 |
| A14 | Audit | question | git history (public) | Commits already pushed hold the real credits from A2 | Q8: history left as is | |

## Questions

All asked and answered 2026-10-12.

| Q | Finding | Answer | Follow-up |
|---|---|---|---|
| Q1 | A7: a re-lookup that picks another record drops the user's own timing | "Keep the version (Recommended)" | A song the user has nudged, lined up or fixed keeps its version (`LyricsRepo.isCurrent`); JVM test |
| Q2 | A8: plain lyrics sent but not shown | "Show it too" | The preview shows both texts under "Timed lyrics" and "Plain lyrics" |
| Q3 | A9: privacy texts out of date since Q25 | "Yes, as written (Recommended)" | README Saved lyrics and Backup, What's on this device |
| Q4 | A10: restore count included fixes not yet applied | "Split the count (Recommended)" | "Your timing came back for X songs. Y more come back when they next play." |
| Q5 | A11: no unsure dot on a hand-picked version that runs past the end | "Dot if it runs past (Recommended)" | `LyricsRepo.pickUnsure`; JVM test |
| Q6 | A12: the reset clears more than nudges | "Yes, as written (Recommended)" | "Reset all timing"; "Reset the timing on every song, nudges and fixed lines? Lyrics you picked by hand stay." |
| Q7 | A13: README What it does | "Yes, as written (Recommended)" | Four bullets: This song and timing, matching and the dot, Other versions, Share your fix |
| Q8 | A14: real credits in pushed history | "Leave history (Recommended)" | Fixed going forward only |
| Q9 | The new text from Q2, Q4 and the README, word for word | "Yes, as written (Recommended)" | As shown |

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
