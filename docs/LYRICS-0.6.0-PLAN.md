# Scarlet Lyrics 0.6.0 plan: visuals

Status: **draft, 2026-10-12.** The owner chose the version, the light theme treatment and the first wave (Q28 to Q30 in `LYRICS-DECISIONS.md`). Everything else is **proposed** and gets confirmed after the owner has played with the preview (a private interactive sketch of the lens, Drift, Spiral, Ink and Random) and before slice 1 starts. Nothing here is built yet; 0.5.0 comes first.

## Why

A nod to the old media player visualizations (the morphing spirals), made calm by default and safe to read over. The lyrics never get drowned out: the area where they show is a soft "lens" that fades and stills the background, and the rest of the screen gets the visual.

## Decided (owner, 2026-10-12)

| # | What | Answer |
|---|---|---|
| Q28 | Version | 0.6 |
| Q29 | Visuals in the light theme | Pastel versions of each visual |
| Q30 | First wave and the rest of the idea list | Still, Drift (default), Spiral, Ink and Random first; the other ideas below go ahead as planned |

## Song-reactive, not sound-reactive (proposed)

The old visualizations listened to the audio. Lyrics can't hear another app without RECORD_AUDIO and the global Visualizer, which is a "record audio" prompt, flaky across phones, and silent for some players. Lyrics never asks for it. Visuals react to what Lyrics already knows:

- **A new line is a beat:** a soft pulse when the current line changes.
- **Line spacing is tempo:** the average gap of the last few lines (gaps over 8 s ignored) scales motion speed, clamped 0.7x to 1.5x and smoothed over about 3 s.
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

Settings (Lyrics tab, Background group): Visual, Intensity, Speed, Behind the lyrics, Finish. Light theme uses pastel versions (Q29).

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

minSdk is 34, so AGSL `RuntimeShader` is on every supported device; no fallback path. One shader draws the visual, the lens guard and the finishes; `RenderEffect` blur covers the lens. Pure Kotlin for the timing logic (pulse cap, tempo, breaks, arc) so it's JVM-testable.

## Slices (proposed)

1. **Song signals.** Pure model: pulse with the 3-a-second cap, tempo, break detection and countdown, arc. JVM tests with synthetic line timings (made-up words).
2. **Palette.** 3 or 4 swatches from the cover with fallbacks; the readability clamp as pure maths beside `TintMath`. JVM tests.
3. **The lens and Still/Drift.** Shader, lens geometry and glide, lyric lines limited to the lens, header, strip and card guards. Emulator plus device check, both themes.
4. **Spiral, Ink, Random, swipe and strip.** Device check.
5. **Settings group, Remove animations, battery saver, frame cap, pause.** Device check, then the hour-long battery run.
6. **Finishes and the track card.**
7. **Readability harness** across every visual, theme and finish.

Then `/finalize 0.6.0`.
