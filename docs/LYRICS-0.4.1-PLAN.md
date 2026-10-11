# Scarlet Lyrics 0.4.1 plan

Status: **all answers recorded 2026-10-11 (Q1 to Q8); ready to start with slice 1, waiting for the owner's go.** Nothing here is built. Prepared 2026-10-11 with /prep (resume mode) from the owner's 0.4.0 device-test feedback (scarlet-hq D-042):

> "The settings page is getting log. We should group and tab it. Add a preview to lyric text size in the settings also. the current lyric should be centered, and stay in view when making the split smaller."

Everything below was proposed by Claude and confirmed by the owner on 2026-10-11 (Q1 to Q8, `LYRICS-DECISIONS.md`), so it is **decided**.

## Where things stand

- Released: 0.4.0 (versionCode 3), GitHub release v0.4.0 on `370a5a6`, published and Latest (2026-10-11).
- `main` at `bb9953b`, in sync with `origin/main`, working tree clean.
- `docs/FINALIZE-0.4.0.md`: every stage done; nothing open.
- No repo `*-DECISIONS.md` before this plan (decisions lived in scarlet-hq, last D-043). This plan starts `docs/LYRICS-DECISIONS.md`.
- Owner devices (both): personal build 0.4.0+b4753ed, "up to date" in `versions.ps1` (later commits changed docs only).
- Repo guard: installed, matches the skill's copy. Repo visibility: public.
- Backups: no verified two-drive backup yet (neither drive has passed verification; the drives aren't bought). The 0.4.0 skip doesn't cover 0.4.1: releasing 0.4.1 needs verified backups or a new recorded skip.
- Spot-check of recent decisions: D-037 (end note, `LyricsLines.kt:247`), D-039 (ABI filter and compressed native libraries, `lyrics/build.gradle.kts:31,90`), D-040 (now-playing bar, "Other versions", `Chrome.kt:169`) and D-043 (/finalize stage 6 publishes) all hold. D-038's idea is filed in scarlet-hq IDEAS as proposed. No TODO or FIXME in the source.
- Drift outside this repo: the Lyrics row in Command `docs/STATUS-AUDIT.md` still says 0.4.0 is waiting to be published; it was published (D-043). Corrected in the Next section at the end of this session.

## Baseline (as found, before any 0.4.1 work)

Run 2026-10-11 at `bb9953b` (`:lyrics:assembleDebug :lyrics:testDebugUnitTest :lyrics:lintDebug`, JAVA_HOME set to Android Studio's runtime as the repo scripts do).

- Build: passes. One Kotlin warning: `LocalLifecycleOwner` deprecated (`LyricsLines.kt:96`).
- Unit tests: 56 of 56 pass (LibraryBackupTest 12, LibraryRulesTest 21, LibraryStatsTest 4, LrcTest 7, PanelBackTest 2, RetryTest 3, SessionPickTest 4, TintMathTest 3).
- Lint: 0 errors, 49 warnings, 1 information, all older than this plan: GradleDependency 21, UseKtx 15, UseTomlInstead 7, UseOfNonLambdaOffsetOverload 2, AndroidGradlePluginVersion 2, ReportShortcutUsage 1, RedundantLabel 1, ObsoleteSdkInt 1.
- None of this is fixed by 0.4.1 unless a slice touches the line anyway.

## What the code does today (checked)

- **Settings** is one scrolling page of 11 sections in one composable (`Panels.kt:211-457`). Its inline confirmations (reset nudges, delete history, delete Library, clear saved lyrics) and the backup and restore flow keep their state in `remember`, and their work runs on `rememberCoroutineScope()` (`Panels.kt:217`, `LibraryShare.kt:264`).
- **Found while checking:** that work is tied to the screen. Leaving Settings while "Delete the Library" runs can cancel it after the database transaction and before the saved lyrics are deleted (`History.kt:208-214`: the cache is cleared after the transaction), leaving lyric files behind that Forget should have reached. Backup and restore are safe from half-writes (backup encrypts fully before writing; restore is one transaction), but a cancelled run never shows its result. Today only Back can do this; tabs would add a second way.
- **Settings is opened from** the More sheet (`Chrome.kt:199`), "Choose your music app" on the Nothing playing screen (`LyricsActivity.kt:456,509`) and "Open Settings" on the empty Library (`LibraryUi.kt:130`).
- **The current line** glides to 38% of the list's height (30% when compact) (`LyricsTuning.kt:41-42`), and only when the line changes (`LyricsLines.kt:115-118`). A resize, a fold, the switch to the compact layout (below 560dp tall, `LyricsActivity.kt:416`) or a text size change re-lays the lines without moving the list, so the current line can sit off screen until the next line starts. The header keeps its space while hidden (`LyricsActivity.kt:424`), so its fading doesn't change the list's height.
- **Text sizes** come from `LyricsSettings.TEXT_SCALE` applied to 26sp (18sp compact) in `LyricsLines.kt:211-212`.

## What can go wrong

### Settings tabs

1. **Leaving a tab, or Settings, while a backup, restore or delete runs.** **Decided (Q6):** this work runs on a scope that outlives the screen, so it always finishes; the result shows when the user comes back to that tab. Fixes the existing Back case too.
2. **A passphrase is typed, then the tab changes or the screen rotates.** **Decided (Q7):** the fields clear (as rotation already does today). Passphrases are never put in saved state.
3. **A confirmation is open ("Delete the Library?") when the tab changes or the screen rotates.** **Decided (Q7):** it closes, nothing is deleted. The safe default.
4. **Rotation, fold, font size, Flex mode.** **Decided:** the open tab survives. Coming back from Tuning lands on the tab it was opened from.
5. **A narrow split or large font.** **Decided (Q7):** the four labels fit at 280dp wide with font scale 1.3; where they don't, the tab row scrolls sideways with a swipe (no scrollbar).
6. **A quick second tap** on a tab does nothing extra (choosing the open tab is a no-op).
7. **TalkBack.** **Decided (Q7):** each tab says its name, "tab", and whether it's selected; tabs are at least 48dp tall.
8. **Where Settings opens.** **Decided (Q5):** on the tab that fits the way in: "Choose your music app" opens Player, the Library's "Open Settings" opens Library, the More sheet opens Lyrics.

### Text-size preview

1. **Sample text.** **Decided:** our own words, for example "This is how your lyrics will look." and a second line "The next line waits here." Never real lyrics; nothing stored or sent.
2. **Preview and lyrics drifting apart.** **Decided (Q7):** one shared function gives the size and line height for (compact, size), used by both. JVM test.
3. **Which layout the preview shows.** **Decided (Q7):** the layout the lyrics screen would use in the current window (compact or not), so it matches what the user sees after going back.
4. **Largest size in a narrow window.** The sample lines wrap like real lines; nothing clips (same 6% end room for the scaled line).
5. **Reduce motion.** The size change in the preview follows the same motion rules as the lyrics screen (instant when animations are off).

### Current line centred

1. **Start of a song.** **Decided (Q7):** unchanged: the first lines start right under the header and the current line glides down to the middle as the song goes on.
2. **Resize while the user is scrolling or has scrolled within the follow delay.** **Decided:** no jump; following resumes after the delay as today.
3. **Resize during a glide.** The jump replaces the glide (no fight between the two).
4. **Flex mode.** The middle is the middle of the lyrics area above the fold.
5. **The last lines and the end note** can still reach the middle (bottom padding is half the list's height).
6. **Tuning.** **Decided (Q7):** the anchor defaults change to 0.50 for both layouts; personal builds can still move them in Tuning. The owner hasn't changed any tuning values on the devices, so the new default applies there too.
7. **Plain (unsynced) lyrics** have no current line: unchanged.

## Slices

Each slice is one commit (or a small series), passes the build, unit tests and lint with no new errors, and is checked with `/finalize --slice` before the next starts. **Decided order (Q8):** smallest and most visible first.

### Slice 1: the current line in the middle, and kept there

- Anchor defaults to 0.50 (both layouts).
- Re-centre with no glide when the list's size, the compact layout or the text size changes, unless the user scrolled within the follow delay.
- Pull the scroll-target maths out of `glideTo` into a plain function.
- Checks: JVM test for the target (middle of the viewport, both layouts); JVM test that a resize within the follow delay doesn't move the list (the decision function); emulator: resize the split while a test LRC plays in the labtest build if it can be driven, otherwise a device-test line for the owner: "Play a synced song in split screen; drag the divider narrower and wider; the current line stays in view in the middle."

### Slice 2: Settings in four tabs

- Split `SettingsPanel` into four tab pages; a tab row under the "Settings" heading; the open tab in saved state; Settings opens on the tab for the way in.
- Long work (backup, restore, delete history, delete the Library, clear saved lyrics, reset nudges) runs on a scope that outlives the screen.
- Checks: JVM tests: every section is in exactly one tab; the entry tab for each way in; delete-the-Library finishes deleting the saved lyrics when the screen goes away mid-run (fake repo). Emulator: phone, small phone (font 1.3) and tablet, light and dark, each tab, rotation keeps the tab, TalkBack reads each tab and its state, Tuning back returns to Privacy.

### Slice 3: the text-size preview

- Two sample lines above the size choices in the Lyrics tab.
- One shared size function used by the preview and the lyrics screen.
- Checks: JVM test that both read the same table for every size and layout; emulator: each size, compact and full, light and dark.

### After the slices

- `/finalize` for 0.4.1 (versionName 0.4.1, versionCode 4 per D-042; the owner confirms the version there).
- README's Settings description and the release notes updated in the slice that changes them.
- Device tests: a `0.4.1-DEVICE-TESTS.md` in scarlet-hq with the lines above.
- Then the site: the 0.4.0 copy fixes and new screenshots together, after 0.4.1 ships (Q4).

## Done checklist (per slice)

From `DONE-CHECKLIST.md`. Lines that don't apply say so in the slice's /finalize --slice notes.

- What can go wrong covered and coded as decided; no failure shown as success; writes unchanged (no new file formats).
- Rotation, fold, font size and keyboard changes handled; one action per tap; no disk or key store work on the main thread.
- TalkBack labels and selected states; 48dp tap areas; plurals and messages read right.
- Every screen run once on an emulator before the commit; tests in the same commit; build, tests and lint (no new errors).
- Upgrade from 0.4.0 keeps every setting (no new settings except the anchor defaults, which aren't stored unless tuned).
- README and release notes match the code; each decision named with its check.
- Public repo: no owner name, paths, device models or home details; no new dependency (none planned); no em dashes.

## Open questions

None. Building starts on the owner's go.
