# Scarlet Lyrics: decisions

Against `docs/LYRICS-0.4.1-PLAN.md`. Newest first. scarlet-hq mirrors these (hq-log). Decisions before 0.4.1 live in scarlet-hq (D-001 to D-043) and `docs/FINALIZE-0.4.0.md`.

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
