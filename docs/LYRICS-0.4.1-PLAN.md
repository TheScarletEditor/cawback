# Scarlet Lyrics 0.4.1 plan

Status: **answers recorded 2026-10-11 (Q1 to Q14); the owner said go. Slices 1 to 3 done; next: device tests on the owner's devices, then /finalize 0.4.1.** Prepared 2026-10-11 with /prep (resume mode) from the owner's 0.4.0 device-test feedback (scarlet-hq D-042):

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
5. **A narrow split or large font.** **Decided (Q12, replacing Q7's swipe):** the tabs sit on one row when they fit and wrap into two rows of two when they don't, so no tab is hidden. (At 16sp the four labels can't fit 280dp at font 1.3.)
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

**Done 2026-10-11** (start `ed6ef11`; `78b733b`, mini-audit fixes in the next commit). `/finalize --slice`: checklist pass clean; one review agent, two findings, both mechanical and fixed, re-review clean. Build passes, 61/61 unit tests (FollowTest 5 new), lint 0 errors, 49 older warnings.

| Id | Finding | Fix |
| --- | --- | --- |
| L1-1 | A text size change happens in Settings, while the lyrics aren't on screen; coming back rebuilt the list and the line glided down from the top instead of jumping to the middle. | The first follow after the lyrics open (once the real position is known) jumps; later lines glide. At the start of a song line 0 can't scroll above the top, so the lines still start under the header. Also means opening the lyrics mid-song lands the line in the middle at once. |
| L1-2 | The follow delay counted from the start of a hand drag, so a long drag followed by a resize could lose the user's place. | `Follow.touched`: the touch time is stamped when a drag starts and when it ends. JVM test. |

Not run on a screen: an emulator can't feed Lyrics a playing synced song (as in 0.4.0). Device-test line for the owner, on the personal build: "Play a synced song in split screen; drag the divider narrower and wider, and rotate; the current line stays in view in the middle. Scroll by hand, let go, and drag the divider within the follow delay: the list stays where you left it. Change the text size in Settings and go back: the line is in the middle, not sliding down."

### Slice 2: Settings in four tabs

- Split `SettingsPanel` into four tab pages; a tab row under the "Settings" heading; the open tab in saved state; Settings opens on the tab for the way in.
- Long work (backup, restore, delete history, delete the Library, clear saved lyrics, reset nudges) runs on a scope that outlives the screen.
- Checks: JVM tests: every section is in exactly one tab; the entry tab for each way in; delete-the-Library finishes deleting the saved lyrics when the screen goes away mid-run (fake repo). Emulator: phone, small phone (font 1.3) and tablet, light and dark, each tab, rotation keeps the tab, TalkBack reads each tab and its state, Tuning back returns to Privacy.

**Done 2026-10-11** (start `3dc291f`; one commit). `/finalize --slice`: checklist pass; one review agent over four rounds (5 findings, then 2 from the fixes, then clean twice); 4 owner questions (Q9 to Q12). Build passes, 67/67 unit tests (SettingsTabsTest 6 new), lint 0 errors, 49 older warnings. Emulator (smoke copy, removed after): small phone at font 1.0 and 1.3, light and dark, every tab, rotation and a font-size recreate keep the tab, "Choose your music app" opens Player, the More sheet opens Lyrics, reset nudges shows its result through a tab switch and a recreate and clears after leaving Settings, tab semantics (clickable, selected, 58dp). Tablet in dark. Not run on screen: backup, restore and the deletes (no Library on the emulator; covered by JVM tests and the owner's device tests), Tuning (not in the smoke build), TalkBack spoken output itself.

| Id | Finding | Fix |
| --- | --- | --- |
| L2-1 | Emulator: on a 360dp phone "Privacy" was cut off at the edge. | Tighter tab padding; then Q12: one row when they fit, two rows of two when not. |
| L2-2 | Emulator: after a font-size recreate the open tab could sit off screen in the swiping row. | Gone with Q12's two rows (nothing scrolls). |
| L2-3 | "That didn't finish. Try again." replaced the button, so it couldn't be tried again (reset nudges, clear saved lyrics); a failed clear that already emptied the count showed nothing. | The message shows above the button, whatever the count; tapping clears it. |
| L2-4 | The lyrics screen's reload after a clear, reset or Library delete went to a screen that a rotation or font change had replaced. | App-level `SettingsWork.lyricsChanges`, part of the lyrics screen's reload key. |
| L2-5 | A restore left an earlier "Listening history deleted." showing; a delete left an earlier backup message. | Starting either clears the other's message. |
| L2-6 | Finished results vanished on a recreate or a trip to Tuning; the first fix then wiped them on any move between other screens. | Forgotten only on the step out of Settings (not to Tuning; a recreate isn't a step). |
| L2-7 | TalkBack didn't hear the tabs as one group. | `selectableGroup()` on the tab row. |
| L2-8 | "Lyrics" tab, then "Lyrics" section heading. | Q11: the section is "Display". |

Device-test lines for the owner: "Settings: the four tabs; each way in opens its tab (More sheet: Lyrics; Choose your music app: Player; the empty Library's Open Settings: Library). Start a Library backup, switch tabs while it runs, come back: the result shows. Tuning and back keeps the tab. With TalkBack on, the tabs read as tabs and say which is selected."

### Slice 3: the text-size preview

- Two sample lines above the size choices in the Lyrics tab.
- One shared size function used by the preview and the lyrics screen.
- Checks: JVM test that both read the same table for every size and layout; emulator: each size, compact and full, light and dark.

**Done 2026-10-11** (start `0e0654a`; one commit). `/finalize --slice`: checklist pass; one review agent (no blocking findings; two owner questions Q13, Q14; re-check after the fixes). Build passes, 70/70 unit tests (LineSizeTest 3 new), lint 0 errors, 49 older warnings. Grep: `TEXT_SCALE[` only in `LyricsSettings.lineSp` (Player.kt) and PlainLyrics. Emulator (smoke copy, removed after): small phone, Small, Medium and Largest, light and dark; the lyrics rows draw the same sizes as before (same numbers, now from `lineSp`). Not run on screen: the compact layout of the preview (needs a window under 560dp tall or Flex mode), TalkBack spoken output.

| Id | Finding | Fix |
| --- | --- | --- |
| L3-1 | PlainLyrics read the size table without a range check, so a bad saved step could crash it. | Clamped, like `lineSp`. JVM test for out-of-range steps. |
| L3-2 | TalkBack read both sample sentences. | Q13: one item, "Preview of the chosen text size". |
| L3-3 | README didn't mention the preview. | Q14. |
| L3-4 | Known, left as is: in the compact layout the lyric rows have 16dp side padding and Settings 24dp, so the preview is 16dp narrower and a sample can wrap a little sooner. | None (cosmetic). |
| L3-5 | Known, left as is: a current-line scale above about 1.064 (only Tuning in personal builds can set it; the default is 1.06) can clip the end of the first sample line. | None (personal builds only). |
| L3-6 | Noted: tapping a larger size makes the preview taller, so the size choices move down under the finger. A second quick tap lands on the preview, which does nothing. | None. |

Device-test lines for the owner: "Settings > Lyrics: tap each text size; the two sample lines change size at once and match the lyrics screen's size afterwards. In split screen (short window) and Flex mode the preview shows the smaller compact size. With TalkBack, the preview reads 'Preview of the chosen text size'."

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
