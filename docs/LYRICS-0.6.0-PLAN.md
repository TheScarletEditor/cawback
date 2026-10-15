# Scarlet Lyrics 0.6.0 plan: visuals

Status: **draft, 2026-10-12.** The owner chose the version, the light theme treatment and the first wave (Q28 to Q30 in `LYRICS-DECISIONS.md`). Everything else is **proposed** and gets confirmed after the owner has played with the preview (a private interactive sketch of the lens, Drift, Spiral, Ink and Random) and before slice 1 starts. Nothing here is built yet; 0.5.0 comes first. **Changed 2026-10-14 (Q39):** Fix words ships before the visuals, so the visuals take the version after it; the number is set at their /prep.

## Why

A nod to the old media player visualizations (the morphing spirals), made calm by default and safe to read over. The lyrics never get drowned out: the area where they show is a soft "lens" that fades and stills the background, and the rest of the screen gets the visual.

## Decided (owner, 2026-10-12)

| # | What | Answer |
|---|---|---|
| Q28 | Version | 0.6 |
| Q29 | Visuals in the light theme | Pastel versions of each visual |
| Q30 | First wave and the rest of the idea list | Still, Drift (default), Spiral, Ink and Random first; the other ideas below go ahead as planned |
| Q31 | Speed | Tied to the song; no Speed setting |
| Q32 | Tap the beat | Yes: users can tap their own tempo per song |
| Q33 | Tempo in the Library export | Yes |
| Q34 | Draft and locked beats | Confident guesses export marked estimated; a tapped beat is only final once the user locks it in, so mis-taps never get codified with no recourse |

## Song-reactive, not sound-reactive (proposed)

The old visualizations listened to the audio. Lyrics can't hear another app without RECORD_AUDIO and the global Visualizer, which is a "record audio" prompt, flaky across phones, and silent for some players. Lyrics never asks for it. Visuals react to what Lyrics already knows:

- **A new line is a beat:** a soft pulse when the current line changes.
- **The beat comes from the timestamps (Q31):** lyric files are known whole before the song plays, so the beat is worked out once per song. For each candidate beat length from 0.33 to 1.0 s (60 to 180 BPM), count the share of lines that land on that grid (a soft window of about a tenth of a beat, so hand-timing slop is tolerated and an offbeat line just doesn't count). Subdivisions fit too, so take the longest length that scores within 90% of the best. Speed follows BPM (BPM / 100, clamped 0.6x to 1.6x). The grid's phase gives the downbeat, so visuals can swell on each bar in time with the music.
- **Line spacing is only the fallback:** the preview shows it gets the order wrong (a 96 BPM song with short lines reads faster than a 120 BPM song with long ones). It's used only when the beat score is under 0.6.
- **Half or double time is a known limit:** when lines land on the bar, a 140 BPM song and a 70 BPM song look the same from lyrics alone. The visual still lands on the beat, just every other one. "Tap the beat" settles it (Q32, below).
- **To test before relying on it:** run the estimate over real LRCLIB records for songs whose BPM the owner knows, and record how often it lands on the beat, half or double, or misses. Sloppy uploads will miss; the fallback covers them.
- **Gaps are instrumental breaks:** a gap over 8 s, starting 3.5 s after the line and ending 1.6 s before the next, shrinks the lens to a countdown ("Next line in 0:12") and the visual takes the screen. The intro gets "First line in".
- **Progress is the arc:** intensity builds to the 70% mark and settles over the last 12%.
- **Album colors are the palette:** today `AlbumTint` keeps only the dominant swatch. Visuals need 3 or 4 (dominant, vibrant, muted, dark vibrant), falling back to shades of the dominant one.
- **Pause eases motion to a stop** (about 0.6 s, slow deceleration) and play eases it back.
- No fake spectrum bars: bars that don't match the music look broken.

## The lens (proposed)

- A feathered, rounded rectangle around the lyric block (about 64 dp of feather) that glides between sizes with slow deceleration. During a break it shrinks to a pill around the countdown.
- Inside it the background is **scrimmed, blurred and stilled**. "Behind the lyrics" setting: Still (default, motion frozen) or Slow (15% speed).
- Lyric lines show only inside the lens, so text never sits on an unguarded background.
- **The readability rule is the album tint's own.** Under the lens every pixel meets `TintMath`'s limits: dark at or under 0.0205 luminance, light at or over 0.80, and `TintMath.readable()` passes. The shader clamps toward charcoal or bone in linear light, with a small margin. The header, the bottom strip and the track card get the same guard.
- Check: a harness renders each mode, theme and finish at fixed times and runs every sampled pixel under the lens through `TintMath.readable()`.

## Tap the beat (Q32; details proposed)

The user's beat wins over the estimate, the same way their timing wins over LRCLIB.

- **Where:** a "Beat" row in the This song sheet: the current BPM and where it came from (worked out, or yours), Tap the beat, ÷2 and ×2, and Automatic.
- **Tapping:** tap along 4 or more times while the song plays. The beat is the median gap of the last 12 taps; the phase is fitted to all of them. A pause over 2 s starts over. Gaps outside 0.25 to 1.5 s are refused with a plain message. The live BPM shows as you tap; nothing is saved until 4 taps agree.
- **÷2 and ×2:** one tap fixes a half or double time reading without tapping again.
- **Draft, then locked (Q34):** a new beat (tapped, ÷2 or ×2) is a draft. The visual breathes on it right away so the user can see whether it's right; "Lock it in" makes it final. Nothing is ever locked automatically.
- **Mis-taps:** if the taps are uneven (the typical tap is more than 8% off the median gap), nothing changes and the row says so. A draft never replaces a locked beat until it's locked in itself: the visual follows the draft so it can be judged, while the locked beat stays the one in the export and carried to other recordings. Leaving a draft unlocked keeps both, and the Beat row shows both.
- **Recourse, always:** a locked beat can be re-tapped, halved, doubled or set back to Automatic at any time; any change is a draft until locked again. One step of Undo in the sheet restores the previous beat, locked state included.
- **Latency:** the existing headphone delay applies. The 0.2 s reaction allowance from Fix lines does not: tapping along to a beat is anticipated, not a reaction.
- **Bar start:** the downbeat is the beat nearest the first lyric line.
- **Saved per recording:** in the cache entry of the exact recording the player reports (title, artist, album, length: `Track.key`), next to the offset and anchors (format version bump). The beat is measured against the player's clock, not the lyrics, so picking another lyric version doesn't touch it (unlike the offset, Q17). "Clear my timing" clears it too.
- **Backup:** included, like timing fixes (Q25 pattern); older backups still restore.
- **Never sent:** LRCLIB has no tempo field, and Share your fix doesn't include it.
- **Accessibility:** a large tap target; TalkBack announces the BPM after 4 taps, not every tap.

## Other recordings of the same song (proposed)

Every cache entry already stores the song key (`SongKey`: cleaned title and primary artist), so another recording of the same song can find a beat the user tapped. What carries over:

| The other recording | Same tempo? | What Lyrics does |
|---|---|---|
| Same recording on another album, single or remaster | Yes, start may shift a little | Reuse your BPM; find the phase again from this recording's lyric timestamps (with the BPM known, that's a much easier fit) |
| Radio edit or extended mix | Usually yes; intro and sections differ | Same as above, if this recording's own estimate agrees |
| Sped up, slowed, remix | No | The estimate disagrees, so your BPM isn't used |
| Live, acoustic | Usually no | Already a different song key ("(Live)" is kept in titles), so nothing carries over |

- **The agreement check:** your BPM is reused only when this recording's lyric estimate lands within 4% of it, or of half or double it (then your octave wins). This matters because `cleanTitle` strips " - Club Mix" and " - Sped Up Version", so those share the song key.
- **When the estimate is unsure** (score under 0.6): reuse automatically only if the length is within 3% (most likely the same recording). Otherwise the Beat row offers it instead: "Use your 140 BPM from another version".
- **Only locked beats carry over** to other recordings, so a draft or a mis-tap never spreads.
- **Your beat on this recording always wins** over one borrowed from another.
- The Beat row says where the beat came from: worked out, yours, or yours from another version.
- JVM tests: remaster (same tempo, shifted start), radio edit (shorter, same tempo), sped-up (1.25x), a club mix sharing the song key, unsure estimate with and without a matching length.

## Tempo in the Library export (Q33; details proposed)

- **What goes out (Q34):** a locked beat as `Tempo: 140 BPM`. A draft, or a confident estimate (score at or over 0.75), as `Tempo: about 140 BPM (estimated)`. Unsure estimates stay out.
- **The AI header says so:** tempo lines without "estimated" are the listener's own; estimated ones come from lyric timing and may be half or double the real tempo.
- **Text format:** an indented line under the song, like Notes and Moments, so the `title | artist | album` columns keep their shape.
- **CSV:** `bpm` and `bpm_source` (locked, draft, estimated) columns, blank when there's none.
- **Versions that differ:** a Library song covers several recordings. If their beats agree within 4%, one value; otherwise each, with its album: `Tempo: 140 BPM (Kiln), 70 BPM (Live Sessions)`.
- **An export option**, "Tempo", like Notes and Moments; on by default when any song has a beat.
- Whole numbers only.

## Visuals (proposed, names Claude's)

| Visual | What it does | Wave |
|---|---|---|
| Still | The album tint only | 1 |
| Drift (default) | A slow mesh of the cover's colors; a faint breath on each line | 1 |
| Spiral | Morphing spiral arms, the homage; line pulses tighten the twist | 1 |
| Ink | A color bloom spreads from a random spot on each line, like ink in water | 1 |
| Random | A new visual (Drift, Spiral or Ink) each song | 1 |
| Tunnel | A warp that pushes forward on each line | 2 |
| Aurora | Slow ribbons, good at night | 2 |
| Starfield | The old screensaver nod; speed from tempo | 2 |
| Lava | Lava-lamp blobs | 2 |

Settings (Lyrics tab, Background group): Visual, Intensity, Breathe on the bar (on by default), Behind the lyrics, Finish. No Speed setting (Q31). Light theme uses pastel versions (Q29).

## Extras (proposed)

- **Swipe the background to change visual**, plus small arrows in a bottom strip; the visual's name brightens there for about 2 s.
- **Track card:** title and artist fade in at the bottom left at the start of a song, then leave.
- **Countdown** during breaks (above).
- **Finishes** that stack on any visual: Scanlines, VHS, Grain. Shader passes, off by default.
- **Word-by-word fill** only if enough LRCLIB records carry word timing (enhanced LRC). Check the share first; a bonus, never a mode.

## Guardrails (proposed)

1. **Photosensitivity:** at most 3 pulses a second (extra line changes skip their pulse), no full-screen brightness jumps.
2. **Remove animations** (system setting) and **battery saver** switch to Still, and the bottom strip says why.
3. **Battery:** a frame cap (30 by default; Drift could run at 20), no drawing when the screen is off, Lyrics is hidden or music is paused and settled. An hour-long split-screen test on the owner's phone before release.
4. **No new permissions.** The build guard should fail on RECORD_AUDIO or MODIFY_AUDIO_SETTINGS.
5. **Burn-in:** motion is slow but constant; the lens itself may drift a few pixels over minutes (to test).

## How it would be built (proposed)

minSdk is 34, so AGSL `RuntimeShader` is on every supported device; no fallback path. One shader draws the visual, the lens guard and the finishes. Pure Kotlin for the timing logic (pulse cap, tempo, breaks, arc) so it's JVM-testable.

Lessons from the preview (it stuttered until these were fixed):
- **No live blur over the moving background.** A blur re-run every frame over a constantly redrawing surface was the biggest cost. Under the lens the guard already pulls the background close to charcoal or bone, so the blur may not be needed at all; add `RenderEffect` blur only if the device check shows the lens edges or motion under the words.
- **Render the visual below screen resolution** (about 70% of the view's size) and scale it up. The visuals are soft, the lyrics are separate text and stay sharp.
- **Never read pixels back from the GPU in the app.** The preview's live readability check had to be made asynchronous; in the app the check belongs in the test harness only.
- **Frame cap as a divisor of the display's refresh rate** (every 2nd frame at 60 Hz, every 4th at 120 Hz), never by elapsed time, or the pacing goes uneven on 90 and 120 Hz screens.
- **Nothing snaps.** Line pulses and the bar swell rise over about 0.3 s and fall slowly; an instant jump in brightness or twist reads as a stutter even at a perfect frame rate (seen in the preview).
- **Skip work that can't show:** a bloom that can't reach a pixel is skipped before any noise is computed; nothing is redrawn while paused and settled.

## Slices (proposed)

1. **Song signals.** Pure model: the beat estimate (BPM, downbeat, score), the line-spacing fallback, pulse with the 3-a-second cap, break detection and countdown, arc. JVM tests with synthetic line timings (made-up words) at several tempos, loose hand-timing, offbeat lines and a half-time case; then the real-record check above.
2. **Your beat.** Tap model (median, phase fit, restart, limits, ÷2 and ×2), stored in the cache entry with a format version, in the backup with its locked flag; draft and locked, uneven taps, Undo; other recordings of the same song (the agreement check, locked beats only); tempo in the Library export. JVM tests. Then the Beat row in the This song sheet; device test tapping along to a real song, with and without Bluetooth headphones.
3. **Palette.** 3 or 4 swatches from the cover with fallbacks; the readability clamp as pure maths beside `TintMath`. JVM tests.
4. **The lens and Still/Drift.** Shader, lens geometry and glide, lyric lines limited to the lens, header, strip and card guards. Emulator plus device check, both themes.
   Breathe on the bar lands here too.
5. **Spiral, Ink, Random, swipe and strip.** Device check.
6. **Settings group, Remove animations, battery saver, frame cap, pause.** Device check, then the hour-long battery run.
7. **Finishes and the track card.**
8. **Readability harness** across every visual, theme and finish.

Then `/finalize 0.6.0`.
