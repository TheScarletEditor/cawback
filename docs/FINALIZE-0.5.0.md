# Finalize Scarlet Lyrics 0.5.0

Started 2026-10-12. Base: v0.4.1 (3dde162). Diff: 24 files, +1615 -144 (before the version bump).
Mode: full. Public: yes.

"Your timing": built in seven slices with /prep (`docs/LYRICS-0.5.0-PLAN.md`, answers Q15 to Q27 in `docs/LYRICS-DECISIONS.md`), each with a `/finalize --slice` mini-audit (M1 to M43). The owner's device check on 0.4.1+69b34d2 (both devices, all seven slices): "tests pass".

## Stages

| # | Stage | Status | Notes |
|---|---|---|---|
| 0 | Preflight | done | Tree clean, one commit ahead of origin (the device-check note). Version: 0.5.0 as the owner chose in Q27 of the plan; versionCode 4 to 5. gh signed in; both devices connected; emulators scarlet_phone, scarlet_small, scarlet_tablet. |
| 1 | Audit | done | Release manifest: same permissions as 0.4.1 (INTERNET and the dynamic-receiver permission); manifest source and dependencies unchanged since v0.4.1. 4 agents (decisions, hard rules, manifest and dependencies, data safety). 6 mechanical fixes (A1 to A6), 8 questions (Q1 to Q8) all answered and applied, plus the new wording approved word for word. Build, 114/114 unit tests, lint 0 errors, no em dashes, no real artist names left in the repo. |
| 2 | Code review | done | 3 agents (data and matching; screens and the Android pass; security). 12 mechanical fixes (R1 to R12), 1 question (Q10, answered); three re-reviews: 5 more (R14 to R18), then clean. R13 dropped (owner's earlier choice). Android pass clean apart from R8: R8 rules, light and dark, touch targets, rotation and recreate. Build, 115/115 unit tests, lint 0 errors. |
| 3 | Cleanup | done | simplify (4 agents: reuse, simplification, efficiency, altitude) and own checks: no TODOs, no commented-out code, TUNING off in release, no em dashes, licenses unchanged (no new dependencies), nothing unused. 8 applied (C1 to C8), larger changes skipped (C9 to C11), release notes drafted (C12, Q11). Build, release build, 115/115 unit tests, lint 0 errors. |
| 4 | QA | done | Pass 1 (at af78909), clean: 115/115 unit tests; lintRelease 0 errors, warnings the same set as before the base (GradleDependency 21, UseKtx 15, UseTomlInstead 7, and 7 others); release, personal and smoke builds pass (R8); release APK 0.5.0 / 5, no suffix, not debuggable, permissions as 0.4.1, 9.63 MB unsigned (0.4.1: 9.59 MB signed), ABIs arm64-v8a, armeabi-v7a, x86_64; labtest 8/8 on the tablet (test copy removed). Re-run after S1 (pass 2, at 397d5ff) and after S2 (pass 3, at 387f316): every step again, both clean. |
| 5 | Smoke test | running | Part 1 done (smoke copy at 75c8901; each emulator first given the personal build 0.5.0+75c8901): phone, small phone at font 1.3 and tablet, light and dark: every Settings tab, the Library page, the Library's Open Settings opens Library; no crash, no ANR. The scripted rotation step didn't run (the app had left the front); rotation is unchanged since 0.4.1 and in the owner's checklist. The lyrics screen, This song, Fix lines and Share can't be driven on an emulator (no player feeds a song): covered by the owner's device tests. Smoke copy removed everywhere. Emulators then matched to the devices' 0.5.0+397d5ff. Part 2: personal build installed with the owner's OK (tablet 75c8901, then both devices 397d5ff); checklist scarlet-hq projects/scarlet-lyrics/0.5.0-DEVICE-TESTS.md; waiting for the owner. |
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
| R1 | Review | mechanical | LyricsStore.kt load | A busy key store read as "nothing saved": a nudge, pick, fix or lookup could write over the user's pick and timing | load throws Busy; lookups show offline and write nothing; the user's changes save nothing and the screen stays as it was | stage 2 |
| R2 | Review | mechanical | LyricsRepo.kt nudge, setAnchor, lineUp, pin, markSent, clearTiming | Read then write without one lock: two quick taps could count once on disk | One edit at a time (Mutex) | stage 2 |
| R3 | Review | mechanical | History.kt restore, LibrarySnapshot.toRestore | A fix for a forgotten song under a version the Library never counted came back | Also filtered by the song keys of forgotten songs; JVM test | stage 2 |
| R4 | Review | mechanical | LyricsRepo.kt load (restored fix) | One garbled reply dropped a restored fix for good | Treated like no connection; only a version that's gone drops it | stage 2 |
| R5 | Review | mechanical | LyricsActivity.kt Line up now | A quick second Now lined up against the next line | The bar closes at the first Now | stage 2 |
| R6 | Review | question | LyricsStore.kt fixes, restoreFixes | A busy key store left timing out of a backup, or unwritten on restore, and both said they worked | Q10 | stage 2 |
| R7 | Review | mechanical | LyricsActivity.kt share, LyricsRepo.markSent | "Sent on" could mark timing changed while it was sending | Marked only if the saved timing is what was sent | stage 2 |
| R8 | Review | mechanical | Chrome.kt FixBar | TalkBack read the bar as one silent button, after every lyric line | Taps swallowed without a click action; the bar is read first; the target line is announced when it changes | stage 2 |
| R9 | Review | mechanical | LyricsActivity.kt share | A screen recreated during a send didn't show "Sent on" | The lyrics screen reads the song again after the mark | stage 2 |
| R10 | Review | mechanical | ShareFix.kt ProofOfWork.solve | A broken or hostile challenge could keep the device hashing for hours (and from another song's page, without Cancel) | Over 32 leading zero bits refused; 3 minutes at most; either ends as "That didn't finish. Try again."; JVM tests | stage 2 |
| R11 | Review | mechanical | LrcLib.kt requestChallenge | The challenge prefix wasn't checked before going into a header | 1 to 128 letters and digits, or the reply makes no sense | stage 2 |
| R12 | Review | mechanical | LyricsActivity.kt share | verify() was never called before sending | Called before the publish | stage 2 |
| R14 | Review | mechanical | LyricsRepo.kt load | (re-review) A lookup could save over a nudge made while it was out | Saved only if nothing changed meanwhile, as one edit; otherwise the song is read again | stage 2 |
| R15 | Review | mechanical | LyricsStore.kt read | (re-review) A file failing the same way every time was "busy" forever: its song always offline, every backup failing | Still failing an hour after its first failure, it counts as unreadable (never deleted for that; the next lookup writes over it, as in 0.4.1). A first try (3 failures in a row) could delete during a short outage: caught by the second re-review | stage 2 |
| R18 | Review | mechanical | LyricsRepo.kt load | (second re-review) The re-read after a change could repeat without end if a save kept failing | Read again once; then shown without saving | stage 2 |
| R16 | Review | mechanical | LyricsStore.kt restoreFix | (re-review) A file that can't be decoded counted as failed, so "Restore again" could never help | Kept, not failed | stage 2 |
| R17 | Review | mechanical | LyricsActivity.kt picker | (re-review) A pick that couldn't be saved closed the list silently | The list stays open to try again | stage 2 |
| S1 | Smoke | question | Panels.kt SharePanel | Owner: the share preview should also confirm what will not be sent | Q12: "Not sent: ..." under the intro | 397d5ff |
| S2 | Smoke | question | Panels.kt:577 Privacy | "picks and timing nudges" leaves out fixed lines | Q13: "picks and timing" | 387f316 |
| C1 | Cleanup | mechanical | LyricsStore.kt, LibraryShare.kt | Fully qualified TimingFix and LibrarySnapshot names | Imports | stage 3 |
| C2 | Cleanup | mechanical | LyricsStore.kt forgetSong, fixes | The song-key fallback written twice | One `songOf` | stage 3 |
| C3 | Cleanup | mechanical | LyricsActivity.kt | The same-version screen update written four times | One `updateIf` | stage 3 |
| C4 | Cleanup | mechanical | LyricsRepo.kt, Panels.kt | Song length in seconds worked out by hand in three places | `Track.seconds()` | stage 3 |
| C5 | Cleanup | mechanical | LrcLib.kt choose, fitOf | Each record's lyrics parsed again for every comparison | Parsed once per lookup; one fit rule | stage 3 |
| C6 | Cleanup | mechanical | LyricsStore.kt, SettingsWork.kt, LyricsRepo.kt | Stacked or stale comments (a title-only fallback that's gone) | Merged and updated | stage 3 |
| C7 | Cleanup | mechanical | Panels.kt SharePanel | `busy` read twice | Once | stage 3 |
| C8 | Cleanup | mechanical | LyricsActivity.kt share | The app-level send job held the whole song's lyrics | Keeps only the version id and timing it checks | stage 3 |
| C9 | Cleanup | skipped | LyricsActivity.kt, LyricsRepo.kt, SettingsWork.kt | Altitude: the share steps in the screen, `sharing` beside the runner, overlay booleans, edits returning the new entry | Larger restructuring this late; proposed for a later release | |
| C10 | Cleanup | skipped | LyricsStore.kt, LibrarySnapshot.kt, Look.kt | Shared anchor JSON and HASH helpers, a BodyText composable, `clock`/plural reuse | Small gains across older code; proposed for later | |
| C11 | Cleanup | skipped | LyricsRepo.kt lookup, YourTiming.kt, Chrome.kt FixBar | Efficiency: parallel lookups, per-line anchor sort, FixBar recomposing 10 times a second | Would change timing of work; measured cost small; proposed for later | |
| C12 | Cleanup | question | docs/release-0.5.0/RELEASE-NOTES.md | Release notes draft | Q11: approved | stage 3 |
| R13 | Review | dropped | Panels.kt SharePanel | Suggested: cancel the send when its panel closes | The owner chose "In the background" (slice 7); R10's limit bounds it | |
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
| Q10 | R6: a busy key store during a backup or restore | "Fail and say so (Recommended)" | Backup fails with its usual "The backup didn't finish. Nothing was changed."; restore adds "Some of your timing couldn't be restored. Restore again to bring it back." |
| Q11 | C12: the 0.5.0 release notes draft | "Yes, as written (Recommended)" | Used for the GitHub release |
| Q12 | S1: what Share does not send | "Yes, as written (Recommended)" | "Not sent: who you are (Lyrics has no account), your Library, your listening history, notes and moments, or anything about other songs. As with every lookup, lrclib.net sees your internet address." |
| Q13 | S2: Privacy text | "Yes, change it (Recommended)" | "Includes your \"Other versions\" picks and timing." |
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
