# Scarlet Lyrics: decisions

Against `docs/LYRICS-0.4.1-PLAN.md`. Newest first. scarlet-hq mirrors these (hq-log). Decisions before 0.4.1 live in scarlet-hq (D-001 to D-043) and `docs/FINALIZE-0.4.0.md`.

## 2026-10-12 (the owner, 0.6.0 visuals)

Against `docs/LYRICS-0.6.0-PLAN.md` (draft). Asked in the planning chat: version, light theme visuals, and the first wave (Still, Drift, Spiral, Ink, Random, the rest later). The owner: ".6, pastel for light theme, and yes to the rest".

| # | Question | Answer (word for word) | Effect | Check |
| --- | --- | --- | --- | --- |
| Q28 | Version for visuals | ".6" | Visuals ship as 0.6.0, after 0.5.0. | build.gradle at /finalize. |
| Q29 | Light theme visuals: pastel versions or dark only | "pastel for light theme" | Every visual has a pastel light version; the lens keeps the light readability limit. | Readability harness, both themes. |
| Q30 | First wave, and the rest of the list | "yes to the rest" | Wave 1: Still, Drift (default), Spiral, Ink, Random. Tunnel, Aurora, Starfield, Lava later. The extras and guardrails in the plan are confirmed slice by slice. | Per slice. |
| Q31 | Speed control | "the speed should be tied to the song, no?" | No Speed setting. Speed follows a beat worked out from the lyric timestamps; line spacing is only the fallback. The plan's tempo section is rewritten. | JVM tests on the estimate; real-record check. |

## 2026-10-11 (the owner, 0.5.0 "your timing")

Against `docs/LYRICS-0.5.0-PLAN.md`. The owner: "yeah, build as planned, make your timing wins".

| # | Question | Answer (word for word) | Effect | Check |
| --- | --- | --- | --- | --- |
| Q20 | Where the adjust control goes | "Header icon, \"This song\" sheet (Recommended)" | Slice 3: a drawn header icon opens one sheet with Other versions, Line up now, Earlier and Later, Fix lines, Share; More's Timing section moves there. | Emulator and device check. |
| Q21 | The end note | "Keep the note, drop its button (Recommended)" | Slice 3. | Device check. |
| Q22 | How far fixing goes | "Timing by tapping lines (Recommended)" | Slice 2 (model) and 4 (Fix lines mode): anchors per line, lines between follow the nearest anchors. | JVM tests on interpolation; device test. |
| Q23 | Sharing fixes with LRCLIB | "Ask each time (Recommended)" | Slice 7: preview of exactly what's sent; nothing goes until Send. | Payload tests; one real publish with the owner's OK. |
| Q24 | Unsure hint | "Yes (Recommended)" | Slice 3: a dot on the header icon; TalkBack says the timing may be off. | JVM test on the unsure flag; device check. |
| Q25 | Fixes in the Library backup | "Yes, fixes in the backup (Recommended)" | Slice 6: new backup format version; older backups still restore. | Format tests. |
| Q26 | Title-only matches by another artist | "Never automatically (Recommended)" | Slice 1: "No lyrics found"; listed in Other versions with their artist. | JVM test. |
| Q27 | Version | "0.5.0 (Recommended)" | The 0.4.2 plan becomes the 0.5.0 plan. | build.gradle at /finalize. |

## 2026-10-11 (the owner, 0.4.2 timing plan, now part of 0.5.0)

Against the 0.4.2 plan, now `docs/LYRICS-0.5.0-PLAN.md`.

| # | Question | Answer (word for word) | Effect | Check |
| --- | --- | --- | --- | --- |
| Q15 | Which ideas go into the timing plan | "Match the right cut (Recommended)", "Show fit in Other versions (Recommended)", "\"Line up now\" nudge (Recommended)" | Slices 1, 3 and 4 of the plan; fixing cuts that skip a section is out. | Per slice. |
| Q16 | Prefer the timing most records agree on | "Yes, add it (Recommended)" | Part of slice 1, with the cut check and D-038's fuller record. | JVM test: a lone outlier loses to a group that agrees. |
| Q17 | A nudge when another version is picked | "Reset to 0 on a new version (Recommended)" | Slice 2: picking a version or going back to automatic sets the offset to 0. | JVM test: pin after a nudge gives 0. |
| Q18 | Songs matched before the smarter matching | "Yes, the next time each plays (Recommended)" | Unpinned matches from before 0.4.2 are looked up again on their next play; picks by hand stay. | JVM test on the cache entry's matching version. |
| Q19 | How "Line up now" works | "Tap when you hear the line (Recommended)" | Slice 4: the next line and a "Now" button in the More sheet's Timing section. | JVM test on the offset sum; device test. |

## 2026-10-11 (the owner, slice 3 mini-audit)

| # | Question | Answer (word for word) | Effect | Check |
| --- | --- | --- | --- | --- |
| Q13 | TalkBack read both sample sentences between "Text size" and the choices | "\"Preview of the chosen text size\" (Recommended)" | The preview is one TalkBack item with that description. | Code: `clearAndSetSemantics` on the preview (LyricsLines.kt); device test with TalkBack. |
| Q14 | README Display bullet mentions the preview | "Yes, as written (Recommended)" | "**Display.** Text size (with a preview of how the lyrics will look), lines already sung ..." | grep README. |

## 2026-10-11 (the owner, slice 2 mini-audit)

| # | Question | Answer (word for word) | Effect | Check |
| --- | --- | --- | --- | --- |
| Q9 | New failure text for reset nudges and clear saved lyrics: "That didn't finish. Try again." (the button stays) | "Yes, as written (Recommended)" | `WorkRunner.FAILED`; before, a failure there crashed the app. | JVM test: a failing job ends with exactly this text, never a success message. |
| Q10 | README Settings paths for the tabs | "Yes, as written (Recommended)" | README line "Settings has four tabs: Lyrics, Player, Library and Privacy. Lyrics and Player set it up the way you listen:"; paths "Settings > Library > Listening history", "Settings > Library > Library backup", "Settings > Privacy shows how many songs are saved", "Settings > Privacy > What's on this device". | grep README for each path. |
| Q11 | "Lyrics" twice in a row (tab and first section) | "Rename it \"Display\" (Recommended)" | The Lyrics tab reads Display, Timing, Theme. README's bullet follows ("**Display.** Text size, ..."). | JVM test: the Lyrics tab's sections are Display, Timing, Theme. |
| Q12 | The plan's "four tabs fit at 280dp with font 1.3" can't hold at 16sp | "Two rows when narrow" | Tabs sit on one row when they fit; otherwise two rows of two, so no tab is hidden. Replaces Q7's sideways swipe. | JVM test `TabFit.perRow`; emulator: one row at font 1.0 on a 360dp phone, two rows at 1.3. |

## 2026-10-11 (the owner, on the 0.4.1 plan, second round)

| # | Question | Answer (word for word) | Effect | Check |
| --- | --- | --- | --- | --- |
| Q5 | Which tab should Settings open on? | "Tab for the way in (Recommended)" | "Choose your music app" opens Player, the Library's "Open Settings" opens Library, the More sheet opens Lyrics. | JVM test: the entry tab for each way in. |
| Q6 | Leaving Settings mid-run can stop "Delete the Library" before the saved lyrics are deleted. Fix it in 0.4.1? | "Work always finishes (Recommended)" | Backup, restore, delete history, delete the Library, clear saved lyrics and reset nudges run on a scope that outlives the screen, so they always finish; the result shows on return. Fixes Back and tab switches. | JVM test: delete-the-Library still deletes the saved lyrics when the screen goes away mid-run (fake repo). |
| Q7 | The rest of "What can go wrong" | "Accept as drafted (Recommended)" | Passphrase fields and open confirmations clear on a tab change or rotation; tabs fit at 280dp with font 1.3 or scroll sideways; TalkBack tab names and states, 48dp; one shared size function; the preview shows the current window's layout; songs still start under the header; anchor defaults 0.50. | As listed per slice in the plan. |
| Q8 | Slice order | "Centring, tabs, preview (Recommended)" | Slice 1 centring, slice 2 tabs, slice 3 preview. | None (order). |

## 2026-10-11 (the owner, on the 0.4.1 plan, first round)

| # | Question | Answer (word for word) | Effect | Check |
| --- | --- | --- | --- | --- |
| Q1 | How should Settings be grouped? | "4 tabs (Recommended)" | Four tabs. Lyrics: text size and preview, sung lines, follow delay, screen on, timing, theme. Player: your music app, Never follow, shortcut, notification access. Library: history, pause, keep plays, delete, backup. Privacy: saved lyrics, what's on this device, about (and Tuning in personal builds). The open tab survives rotation. | JVM test: every Settings section belongs to exactly one tab. Emulator: each tab on the phone, small phone and tablet, light and dark, rotation keeps the tab. |
| Q2 | How should the text-size preview look? | "Two sample lines (Recommended)" | Above the size choices: a current line at full brightness with its bar, then a dimmed next line, in the lyrics screen's style at the chosen size. Our own words, never real lyrics. Updates on each tap. | JVM test: the preview and the lyrics screen read the same size table. Emulator: each size, both layouts. |
| Q3 | Where should the current line sit? | "True middle, re-centres (Recommended)" | The current line sits halfway down on every screen size. After a resize, a fold change, the compact layout switch or a text size change it jumps straight back to the middle (no glide), unless the user scrolled by hand within the follow delay. | JVM test: the scroll target is the list's middle. Device: drag the split narrower and wider while a song plays; the line stays in view. |
| Q4 | What goes first, and what's in 0.4.1? | "0.4.1 first, site after" | Build 0.4.1 now; all the site work (0.4.0 copy fixes and new screenshots) waits until 0.4.1 ships. The fuller-LRCLIB-record idea (D-038) stays in IDEAS, not in 0.4.1. | None (order). |
