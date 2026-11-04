# Lyr Player Spec

_Copied from the Claude Doc of the same name on Nov 4, 2026. Docs only: nothing in this file changes Cawback's code. No song lyrics are quoted here._

Nov 4, 2026

The Lyr Player is the demo Sam shows bands: it opens a .lyr, plays it lossless with word-by-word lyrics, proves the file is untouched and signed, and shows side by side why that beats community lyrics. This spec is written so Code can build it without asking Sam anything; it reuses `lyr-core` and `packages/ui` from the Lyr Format and Packer Spec.

## Needed from Sam before the design is final

Both of Sam's repos have now been read: Cawback (`TheScarletEditor/scarlet-lyrics`, the Lyrics app under its new name) and the website (`TheScarletEditor/scarletcoder-site`). The colors, type, motion, lyric display and raven files in this spec come from them. A few things are still missing; each line says what Code uses until it arrives.

- [x] Colors, type and motion: taken from Cawback's ScarletTokens v1 and the website's fonts (Visual design).
- [x] The Lyrics codebase: read. Its docs hold no .lyr plan, which shapes the platform pick in Decisions.
- [ ] `BRAND-GUIDELINES.md` (the site's CLAUDE.md says the brand rules live in the-scarlet-coder repo), plus `brand-voice.md` and `mockup-home.html`, which the site's README calls authoritative but keeps in a handoff folder outside the repo. Until then, the voice rules in Visual design are drawn from shipped Cawback and website copy.
- [ ] Where the raven may appear inside an app. Until then it appears only in the top bar, the empty state and About, never on the lyric stage.
- [ ] An app icon for the player in the ring style (the masters are in the brand repo's `brand/app-icons`). Until then Code uses the site's `raven-ring.svg`.

## Decisions

Platform: a Tauri 2 desktop app, built and tested on Windows first, designed as Cawback's sibling. Cawback is a Kotlin and Jetpack Compose app for Android 14 or newer that follows whatever another music app is playing, through notification access, and by design never touches another app's audio (its README and backlog item LB-2). The player's job is different: it decodes and plays its own lossless audio, verifies signatures and signs files. Putting that inside Cawback would change what Cawback is, and a laptop is still the easiest thing to show a band in person or on a screen share. So the demo stays on the desktop, and the player copies Cawback's tokens, lyric display rules and motion exactly, so the two read as one family. Opening .lyr files in Cawback is a later phase (Y9) over the same Rust core. Cawback's docs record no .lyr plan, so the earlier idea of taking .lyr to Cawback first is not written down there. Sam confirmed the pick on Nov 4: the demo player is a desktop app, and .lyr in Cawback comes later. The app also builds for macOS and Linux from the same code.

Why not the alternatives:

| Option | Why not, for this demo |
| --- | --- |
| A web page | Browsers resample audio and hide the output path, so the quality story is weaker; "no network code at all" is harder to prove; opening local files is clunky |
| A mode inside Cawback (Kotlin) | Cawback follows other apps' playback and never plays audio itself; a lossless player, verifier and signer would change what it is, and a phone is harder to show a room. Kept as a later phase (Y9), not dropped |
| Electron | Larger downloads and a whole bundled browser, for no gain over Tauri |

What the player is: the reference Lyr reader. It opens, verifies and plays .lyr files, shows lyrics word by word, explains what is inside a file, compares against LRCLIB for the demo, and lets an artist fix and re-sign their lyrics.

What it is not, for now: a music library manager, a streaming client, or a social app. It has no accounts, no telemetry, no third-party SDKs and no network code. Listener data it keeps (trusted signers, settings, recent files) is encrypted on the device.

Other defaults picked:

| Question | Default |
| --- | --- |
| Front end | React 18 and TypeScript with Vite, plain CSS with custom properties for tokens, Motion (`motion` package, MIT) for animation |
| Where audio plays | In Rust (decode with `claxon`, output with `cpal`), not in the web view, for exact timing and honest quality reporting |
| Window | Opens at 1440 by 900, minimum 1024 by 640; a Presenter mode goes fullscreen |
| Theme | Dark and light from ScarletTokens v1, following the OS like Cawback, with a Theme setting (System, Dark, Light); Presenter mode is always dark |
| Demo signer | Sam's own key, named "Four Year Strong (demo stand-in)", with a "demo key" chip wherever the name appears |
| Encrypted local store | A JSON file encrypted with a key held in the OS keychain (`keyring` crate) |

## Users and the five-minute demo

The demo works if a band member walks away able to say, in one sentence, "our song, our words, in one file we control". Everything in the player serves that sentence.

Who uses it:

1. The audience: band members or their manager, musicians not engineers, watching a laptop next to Sam or a screen share. They care about their songs being right and about control, not about zip files. They will judge it in the first 30 seconds by how it looks and sounds.
2. The presenter: Sam, driving with keyboard shortcuts, needing every step to work the same way every time, and a quick reset between rehearsals.
3. Later, an artist: someone fixing and signing their own lyrics in artist mode.
4. Later still, fans: people who open a .lyr someone sent them. The design should not need a presenter to make sense.

The story, as Presenter mode's four chapters:

| Time | Chapter | What Sam does | What they see | The point |
| --- | --- | --- | --- | --- |
| 0:00 to 1:15 | 1. One file, whole song | Drops `Enemy Of The World.lyr` on the player; it plays | The Verified badge settles in, the song starts, lyrics fill word by word. Then Inside the file: the parts bar, every part ticks green, "Open as zip" shows the plain folder | Everything is in one open file; nothing is locked |
| 1:15 to 2:30 | 2. Sync versus LRCLIB | Opens Compare on the stress-case song | LRCLIB's lines highlight late or early as whole lines, with wrong words marked; Lyr lights each word on time. Three numbers on top | Community lyrics are wrong and coarse; signed lyrics are exact |
| 2:30 to 3:45 | 3. Artist control | Opens Artist mode on Enemy Of The World, jumps to the line with the one-letter typo, fixes it, signs | The badge turns to "Edited, not signed", then the seal stamps it Verified again. Then she opens the tampered copy: Unverified, "the lyrics changed after signing" | The band, and only the band, can change their words, and everyone can tell |
| 3:45 to 4:30 | 4. Quality and size | Opens the Quality panel, drops the original Bandcamp FLAC on it | Format, bit depth, rate, sizes, then "Bit-perfect: same audio as the original download" with matching fingerprints | No quality was lost, and the file is no bigger than the download |
| 4:30 to 5:00 | Close | Back to Now Playing, music keeps going | The song with lyrics | The ask: their official lyrics and their signature would make this definitive |

Sam says once, out loud and in the Compare footnote: the Lyr timing in this demo is her own hand-corrected work, standing in for theirs. That honesty is part of the pitch.

Note on the typo line: the band's own Bandcamp page has a one-letter typo in one repeat of a chorus line (the Lyr Demo Plan names it). No lyrics are quoted in this spec. The demo file keeps their text exactly as posted, so the fix in chapter 3 is real. Sam should frame it kindly, as the kind of slip that signing lets them fix everywhere at once.

## Screens and flows

_Diagram: Player screens and how they connect. The picture is in the Claude Doc version of this spec; the text around it covers what it shows._

Inside the file, Quality and Compare open as full-height panels over Now Playing, so the song keeps playing and the lyrics stay in view where there is room. Escape closes any panel. Artist mode replaces the lyric stage with the editor; the transport stays.

Screen by screen:

1. Library. A grid of song cards: cover, title, artist, a verification badge and a quality chip ("24-bit FLAC"). Empty state: the raven, "Drop a .lyr file here", and an Open button. Recent files are remembered in the encrypted store. A Demo kit row appears when a demo kit folder is configured.
2. Now Playing. Top bar, the lyric stage, cover and details column, transport. Described in Visual design.
3. Inside the file. A horizontal parts bar, sized by bytes (audio, lyrics, art, manifest, signatures), each part labelled with its size and percentage. Below it, the list of files with their SHA-256 shortened to 12 characters, each with a tick that appears as the player re-hashes it (staggered, about 60 ms apart, for the demo's sake; the real check already ran on open). An "Open as zip" button copies the file to a temp folder as `<name>.zip` and opens it in the system file manager, showing it is an ordinary archive. A small manifest viewer shows the raw JSON.
4. Quality. Big facts: codec, bit depth, sample rate, channels, duration, file size, compression versus raw PCM, and the song id. A "Bit-perfect check" card: the PCM fingerprint stored in the file, the fingerprint of what was just decoded, and a match line. A drop zone: "Drop the original download to compare"; it decodes that FLAC and shows its fingerprint beside the others. An output line says honestly whether playback is resampling ("Playing at 44.1 kHz, no resampling" or "Your output device runs at 48 kHz, so playback is resampled; the file itself is untouched").
5. Compare. Described in its own section below.
6. Artist mode. Described under Opening and verifying.
7. Sign sheet. A modal over Artist mode: choose a key file, type the passphrase, confirm signer name and role, Sign. Shows the key code. On success the sheet closes into the seal animation.
8. Settings. Trusted signers (add from `.lyrpub`, remove, see key codes), Sync offset (slider, plus a tap test that measures it), Text size, Theme, Reduced motion (follows the OS by default), Read-along announcements, Demo kit folder, About (version, licenses, privacy statement).

Unverified files still open and play, with the scarlet badge and the reason; the player informs, it never refuses to play a person's music. Files that cannot be read at all (E701, E702, E704) show a clear error card instead.

## Visual design

The look is Cawback's: ScarletTokens v1 (Cawback's `scarlet/ScarletTokens.kt`, whose header says to keep it identical across the Scarlet apps), Fraunces for display and Inter for everything else. Cawback's own rule carries over unchanged: the lyrics use neutrals only, and scarlet appears only where no music shows. So the lyric stage, the word fill and the line bar are the text color at different strengths; scarlet is for the brand mark, focus rings, the one primary action on a sheet (Sign) and the Unverified warning; teal is the quiet secondary, for the Verified badge and small labels. Buttons use Cawback's one button style, a neutral wash. Every value lives in `packages/ui/src/tokens.css`, a direct copy of ScarletTokens v1 with a header comment saying where it came from.

| Token | Dark | Light | Use |
| --- | --- | --- | --- |
| --lyr-bg | #171614 | #FAF9F6 | App background (charcoal, bone) |
| --lyr-surface | #211F1C | #F2F0EB | Panels, details column, transport |
| --lyr-raised | #2A2825 | #FFFEFC | Popovers and sheets |
| --lyr-pressed | #3A3733 | #E4E0D9 | Pressed buttons |
| --lyr-text | #FAF9F6 | #171614 | Main text, the current lyric line, sung words |
| --lyr-soft | #908E86 | #66635D | Secondary text |
| --lyr-faint | #65635E | #85817A | Hints and disabled text, large sizes only |
| --lyr-line | text at 12% | text at 12% | Dividers and hairlines |
| --lyr-wash, --lyr-wash-edge | text at 7%, 14% | text at 7%, 14% | Cawback's washed buttons |
| --lyr-scarlet | #E20514 | #E20514 | Brand mark, focus rings, Sign, Unverified icon |
| --lyr-scarlet-wash, --lyr-scarlet-edge | scarlet at 14%, 45% | scarlet at 10%, 40% | Unverified badge fill and outline |
| --lyr-teal | #5E9B8C | #44776A | Verified badge, small labels |
| --lyr-teal-wash, --lyr-teal-edge | teal at 22%, 60% | teal at 18%, 60% | Verified badge fill and outline |

The website uses slightly different values in its `global.css` (#141110, #1C1816, #F0EBE4, #948E86). The player follows the apps, not the site, because it is an app. If Sam wants one set everywhere, only `tokens.css` changes.

Measured contrast: scarlet on dark charcoal is 3.7 to 1, so in dark it never carries small text. It is used for icons, outlines, focus rings and large text only, and the Unverified badge sets its words in the text color beside a scarlet icon and edge. Scarlet on bone is 4.7 to 1. Soft text is 5.5 to 1 in dark and 5.7 to 1 in light; teal is 5.6 and 4.9. Faint text is 3.0 and 3.7, so it is kept to large or decorative text. The dimmed lyric lines stay above 3.4 to 1 at every distance, which passes for text this large. Code adds an automated contrast test over every token pair, in both themes, in CI.

Type:

| Role | Font | Size and weight |
| --- | --- | --- |
| Lyric lines | Inter Medium | 36 px on a 48 px line at the default size: Cawback's 26 on 34, scaled up for a laptop seen from further away |
| The current line | Inter Medium | The same size scaled to 1.06 (Cawback's line scale), full strength |
| Other lines | Inter Medium | Text at 60, 48 and 42 percent for one, two and three lines away, then 38 percent (dark); 72, 62, 56, then 52 percent (light). Sung lines dim a little more (90 percent of that, never below the floor), as in Cawback |
| Backing vocals | Inter Regular | 0.8 of the lyric size, in parentheses, one step fainter than their line |
| Song title, big numbers | Fraunces | 32 px, 600 |
| Brand name in the top bar | Fraunces | 18 px, 600, scarlet, beside the 30 px raven mark, as in the website header |
| UI text | Inter | 14 px regular, labels 12 px, buttons 16 px medium (14 px when small) |
| Text size setting | Inter | Four steps, 0.85, 1, 1.18 and 1.36 times, the same as Cawback's; Large lyrics uses 1.36 |

Fonts: copy the WOFF2 files and `fonts.css` from the website repo's `public/fonts` (Fraunces 400, 500, 600 and 400 italic; Inter 400 and 500). They are SIL Open Font License files, and their license goes in the notices. Nothing loads from Google Fonts. Icons come from Lucide (ISC license), bundled as SVG and never as a font, matching Cawback's drawn icons. The raven: copy `raven-mark.svg` (the round cut the site header shows at 30 px) and `raven-ring.svg` (the raven in the scarlet ring, the app-icon style) from the site's `public/brand` into `packages/ui/assets/`. The top bar shows the mark with "Lyr" beside it in the brand-name style. The branch divider (the site's `branch-divider.svg`, its signature motif) sits under the title on the empty state and in About.

Voice for all UI copy, drawn from shipped Cawback and website text until `brand-voice.md` arrives: short plain sentences that speak to "you"; say what happened and what to do (Cawback: "That didn't finish. Try again."); be honest about limits ("That's all the lyrics this file has."); no hype and no em dashes; never use "dark" as an insult, and call manipulative design manipulative. The privacy line matches the website's: no ads, no accounts, no tracking, nothing sent anywhere.

Layout on an 8 px grid. Now Playing: top bar 56 px, transport 88 px, a details column 360 px wide (cover at 320 px, then title, artist, album, format chip, signer), and the lyric stage taking the rest. Presenter mode adds a 72 px chapter rail on the left. The current line sits in the true middle of the stage, as in Cawback (Sam's ruling for 0.4.1), and goes straight back there after a resize or a text size change. The stage fades out at its edges like Cawback's: from 14 to 30 percent of the height at the top, and from 78 percent to the bottom. A 3 px bar in the text color (55 percent in dark, 45 in light, 2 px corners) sits left of the current line and fades with it. Lines are capped at about 22 words wide and wrap; a wrapped current line keeps its block together.

_Diagram: Now Playing wireframe, 1440 by 900 window. The picture is in the Claude Doc version of this spec; the text around it covers what it shows._

Compare keeps both versions on the same moment: the current line of each sits in one shared band across the screen, and both columns scroll together.

Album tint: as in Cawback, a little of the cover's dominant color tints the background while a song plays (Cawback's defaults: strength 0.9 with saturation capped at 0.5, an 800 ms fade). Code ports Cawback's `TintMath.kt` so the result matches. The tint is worked out in memory from the file's cover and never saved. Sam owns Cawback's code, so the port is fine; Cawback is GPL-3.0, so if the player is ever published, Sam picks its license knowing that.

_Diagram: Compare wireframe. The picture is in the Claude Doc version of this spec; the text around it covers what it shows._

Badges: Verified is a teal pill with a check and the signer's name; Signed by an unknown signer is a bone outline pill with a question mark and the key code; Unverified is a pill with the scarlet wash and edge, a scarlet broken-seal icon and the reason in the text color. Each badge opens a popover that explains the state in two plain sentences and shows the key code.

## Motion and the lyric sync display

The sync display is the product. Its job: the word lights at the instant it is sung, every time, and nothing on screen moves without a reason.

Word highlight:

1. Each word on the current line has three states: to come (the text color at 60 percent in dark, 72 in light, the same strength as the next line), being sung (a left-to-right wipe up to full strength), and sung (full strength). The fill changes strength, never color, which keeps Cawback's neutral rule.
2. The fill is a CSS gradient on the word's text with `background-clip: text`, its stop position set every frame to `(t - start) / (end - start)`, clamped 0 to 1. No per-frame React re-render: a single `requestAnimationFrame` loop writes a CSS custom property on the active word's element only.
3. A word shorter than 120 ms skips the wipe and switches state at its start, so fast runs read as a crisp flicker of words, not a smear.
4. Cawback highlights whole lines, because word stamps are rare on LRCLIB and a guessed word fill is worse than none (its backlog item LB-2). Lyr files carry checked word timing, which is why the player can fill words. A file whose words are not timed falls back to Cawback's whole-line highlight.
5. Gaps: when the next line starts more than 4 seconds after the current line ends, three 7 px dots appear where the next line will be and fill one by one as the gap passes, as in Cawback. Instrumental intros show the song title in Fraunces instead, fading out 1.5 seconds before the first line. After the last line, an end note in soft text reads "That's all the lyrics this file has."

Line movement:

1. When a line ends, the stage glides so the next line takes the middle: 650 ms on Cawback's lux ease (cubic-bezier 0.16, 1, 0.3, 1). The incoming line scales from 1 to 1.06 and comes up to full strength while the outgoing line drops back to its dimmed strength, both over 220 ms on Cawback's navigation ease (cubic-bezier 0.2, 0.7, 0.2, 1). Nothing overshoots, which is Cawback's motion rule.
2. The glide starts early enough to land as the first word is sung: 650 ms before it, or at once when the gap is shorter.
3. Backing vocal lines come and go with their lead line, fading only.
4. Clicking any line seeks to its start and scrolls immediately (no ease), so seeking feels instant.
5. If the user scrolls the lyrics by hand, following pauses and a "Back to now" chip appears. Following resumes after the follow delay (2, 4 or 8 seconds, default 4, the same choices as Cawback's setting) or on click.

Signature moments (each under a second, each once):

| Moment | Motion |
| --- | --- |
| A file verifies on open | The badge draws its outline in 300 ms, then the check mark in 200 ms |
| Lyrics edited in artist mode | The badge cross-fades to "Edited, not signed" (bone outline) |
| Re-signed | A seal stamp: scale 1.12 to 1 and fade in over 650 ms on the lux ease, no bounce, then the badge returns to Verified. It uses the seal icon, not the raven, until the brand rules say where the raven may appear |
| A tampered file opens | The badge fades in with its scarlet edge over 220 ms, then the reason line fades in under it. No shake, since nothing in the Scarlet apps overshoots |
| Inside the file | Parts bar segments grow from zero to their widths in 500 ms, staggered by 60 ms; hash ticks follow in order |

Reduced motion (the OS setting, or the player's own switch): no wipe (words switch state at their start), no scale or slide (lines cross-fade in 120 ms), no glide, no stamp (the badge simply changes). The sync timing itself does not change.

Performance budget: 60 frames per second on Sam's laptop with the lyric stage, the transport and one open panel. Animate only `transform`, `opacity` and the one custom property. Test with the browser performance tools in the web view; any frame over 16 ms during playback is a bug.

## Accessibility

Target: WCAG 2.2 AA for everything outside the live lyric animation, and an equivalent experience for the lyrics.

1. Keyboard. Every action works without a mouse. Space play or pause; Left and Right seek 5 s; Up and Down move to the previous or next line; Enter on a focused line seeks to it; I, Q, C, A open the panels; Escape closes; 1 to 4 jump chapters in Presenter mode; ? opens a shortcut sheet. Focus order follows the visual order. Focus rings are 2 px scarlet with a 3 px offset, as on the website, and never removed.
2. Screen readers. Landmarks for the top bar, lyric stage, details and transport. The badge has an accessible name that includes the state and reason. Buttons have labels, not just icons. The lyric stage is not a live region by default (announcing every line would talk over the song). A Read-along setting makes the current line a polite live region, one announcement per line.
3. Color is never the only signal. Badge states have different icons and words. The wrong-word marks in Compare are underlined as well as colored. The word fill is a change in brightness, not color, so it reads without color vision.
4. Contrast as set in Visual design, checked in CI.
5. Text size: a setting from 80 to 140 percent for the lyric stage, and the app respects the OS text scale for UI text. Layouts are tested at 140 percent and at the minimum window size.
6. Motion: the reduced-motion rules above. No flashing anywhere; the tamper shake is two cycles only.
7. Captions mindset: the lyric stage doubles as captions. A "Large lyrics" layout hides the details column and puts the lyrics at full width for people watching from across a room, which also suits Presenter mode.
8. Testing: run axe-core against each screen in an automated test, and do one manual pass with NVDA on Windows and VoiceOver on macOS before the demo.

## Architecture

_Diagram: Player architecture: Rust backend and web view. The picture is in the Claude Doc version of this spec; the text around it covers what it shows._

The web view never parses a .lyr, never decodes audio and never holds a key. It asks the Rust side through Tauri commands and listens for a few events.

Repo layout for `lyr-player`:

```
lyr-player/
  vendor/lyr/              git submodule (lyr-core, packages/ui)
  src-tauri/
    src/main.rs            app setup, capabilities, no network plugins
    src/commands.rs        every command the front end may call
    src/audio/             decoder.rs, output.rs, clock.rs, resample.rs
    src/store.rs           encrypted local store
    src/compare.rs         LRCLIB cache loading and matching (math in lyr-core)
    tauri.conf.json        CSP, file associations for .lyr
  src/                     React app
    state/                 Zustand store (MIT)
    screens/               Library, NowPlaying, Settings
    panels/                Inside, Quality, Compare, Artist
    stage/                 LyricStage, useClock, wordFill
  tools/fetch-lrclib.ts    prep-only script, see Compare
  demo-kit/                gitignored, see Demo kit
```

Commands (all return typed results; errors carry the core's codes):

| Command | Does |
| --- | --- |
| `open_file(path)` | Runs full verify, decodes PCM into memory, returns song, lyrics, verify result, sizes, PCM fingerprints |
| `play()`, `pause()`, `seek(ms)`, `set_volume(0..1)` | Transport |
| `audio_status()` | Device name, device sample rate, whether resampling, measured output latency |
| `rehash_file()` | Re-runs the hashes for the Inside panel's tick sequence |
| `compare_original(path)` | Decodes another FLAC and returns its PCM fingerprints |
| `open_as_zip()` | Copies to temp as `.zip` and reveals it |
| `load_compare(song_id)` | Returns the LRCLIB cache entry and computed metrics, or none |
| `save_edit(lyrics_json)` | Validates, writes an unsigned edited copy in memory, returns the new verify result ("Edited, not signed") |
| `sign(key_path, passphrase, signer)` | Signs the edited copy, writes it to disk (see Opening and verifying), returns the new verify result |
| `trusted_list()`, `trust_key(path)`, `untrust_key(key_id)` | Trusted signers |
| `settings_get()`, `settings_set(patch)` | Settings |
| `demo_reset()` | Restores the demo kit |

Events: `clock` (anchor updates), `playback_ended`, `device_changed`.

Audio engine. On open, decode the whole FLAC into memory as interleaved `f32` (a 4 minute 24-bit stereo song is about 85 MB, fine on a laptop), keeping the integer samples only long enough to hash them. Output through `cpal` on the default device. Ask for a stream at the song's sample rate; if the device refuses, resample with `rubato` and report it. The audio callback reads from the buffer by an atomic frame index, so seeking is a single atomic store. No allocation or locks in the callback.

Position clock. The callback increments `frames_played`. Each callback also receives cpal's output timestamp, which gives the delay until those frames reach the speakers; keep a smoothed `output_latency`. The playback position is `frames_played / rate - output_latency + user_offset`. The backend emits a `clock` event with `{position_ms, host_time_ns, rate, playing}` on play, pause and seek, and every 250 ms. The front end maps host time to `performance.now()` once, then extrapolates the position every frame and eases out any drift over 100 ms, never jumping backwards by less than 30 ms (to avoid flicker). Target error between the highlight and the sound: under 20 ms.

Sync offset. Bluetooth headphones add 150 to 300 ms that the OS may not report. Settings has a slider from minus 300 to plus 300 ms and a tap test (tap along to a click track for 8 beats; the median offset becomes the setting).

Front end state. One Zustand store holds the open song, verify result, current mode and settings. The lyric stage subscribes to the clock outside React's render cycle (a ref and a frame loop), so playback never causes component re-renders except when the current line changes.

## Opening and verifying files

Opening. A .lyr opens from the Library, from a file drop anywhere in the window, from the Open command (Ctrl+O), or by double-clicking it in the file manager (the installer registers the `.lyr` extension and the `application/vnd.lyr+zip` type). Opening always runs `lyr-core`'s full `verify()`, including the PCM decode, on a background thread. While it runs (about a second for a 4 minute song), the stage shows the cover and title from the manifest and a quiet "Checking" badge. Playback can start before the check finishes; the badge resolves when it does.

What the badge shows comes straight from the verify result, using the three states and the wording in the format spec. The badge popover adds:

- Verified: "This file hasn't changed since {signer} signed it. You chose to trust this key." plus the key code and signing date.
- Signed by an unknown signer: "This file hasn't changed since it was signed, but you haven't told Lyr to trust this key. It says it belongs to {name}." plus the key code and a "Trust this key" button that asks the user to confirm the code matches one the artist published.
- Unverified: the reason in plain words, for example "The lyrics changed after signing", "There's a file inside that the signer didn't include", "The audio doesn't match what was signed", "Not signed".

The demo signer. Sam's demo key is named "Four Year Strong (demo stand-in)" with signer role `demo`. Whenever the signer role is `demo`, the badge and popover add a small "demo key" chip and the line "Signed by a stand-in key for this demo, not by the band." The demo kit trusts that key, so the files show Verified in the demo without pretending the band signed them.

Trusted signers. Stored in the encrypted store. Added from a `.lyrpub` file or by pasting a public key; the player shows the key code and asks the user to compare it with the code the artist published. Removing a key immediately re-evaluates the open file.

Artist mode. The same editor component as the packer app (`packages/ui` TimingEditor), with these controls:

1. The lyric list on the left; select a word to edit. `E` edits its text in place (Enter saves, Escape cancels). Splitting and joining words: typing a space splits; Backspace at the start of a word joins it to the previous one.
2. A waveform strip of the song with word blocks under it. Drag a block's edges, or nudge with `[` `]` (start) and `{` `}` (end) by 10 ms, Shift for 50 ms. `P` plays the selected line; `L` loops it.
3. Every edit is checked live against the lyrics rules; problems show inline.
4. After the first edit the badge changes to "Edited, not signed". Nothing is written to disk yet.
5. Sign opens the Sign sheet. Signing calls `lyr-core`'s update path: new manifest with `supersedes`, old signatures dropped, new signature added, full self-verify. The signed file is written next to the original as `<name> (edited).lyr` unless the user picks Save over original in the sheet; the original is never silently replaced.
6. Close without signing asks: Discard edits, Keep editing, or Export unsigned (writes an unsigned copy, which will read Unverified, not signed).

Tampered copy. For the demo, `tools/make-tampered` (a small Rust binary in the player repo) copies a signed .lyr, changes one lyric word in `lyrics.json` (one letter of one word in the chosen line) and repacks without re-signing and without touching the manifest. Opening it gives Unverified with the reason "The lyrics changed after signing". The tool refuses to run on files whose signer role is not `demo`, so it cannot be misused to fake an artist's edit.

## The LRCLIB comparison

The comparison is the strongest moment of the demo, so its numbers must be fair, reproducible and explainable in one sentence each.

Fetching (prep only, never in the app). `tools/fetch-lrclib.ts` is a Node script Sam runs once while preparing, with the network on. For each .lyr in the demo kit it reads title, artist, album and duration from the manifest, then:

1. Calls LRCLIB's get endpoint (`https://lrclib.net/api/get` with `track_name`, `artist_name`, `album_name` and `duration` in seconds), sending a User-Agent that names the tool, for example `LyrPlayerDemo/0.1 (+https://thescarletcoder.com)`.
2. If that returns nothing, calls the search endpoint with track and artist and picks the result whose duration is within 2 seconds; if none is that close, it records "no match" rather than guessing.
3. Saves the raw response, the request URL and the fetch time to `demo-kit/compare/<song id>.lrclib.json`. The player reads these files and nothing else.

Code confirms the endpoint names and response fields against LRCLIB's own API docs before writing the script. The script prints which songs have synced lyrics, plain lyrics only, or nothing, so Sam knows before the demo which song to use for chapter 2.

Matching and metrics (pure functions in `lyr-core::compare`, unit tested with hand-made cases):

1. Parse LRCLIB's `syncedLyrics` as LRC (lines can carry several timestamps; each becomes its own line occurrence). If only `plainLyrics` exists, text metrics still work and timing metrics show "no timing in LRCLIB".
2. Normalize text for comparison only: lowercase, Unicode NFKD with accents removed, curly quotes straightened, punctuation removed except apostrophes inside words, spaces collapsed. Display always uses the original text.
3. Align lines: Needleman-Wunsch between Lyr lines and LRCLIB lines in song order, scoring each pair by word-level similarity (1 minus the word edit distance over the longer line's word count), match threshold 0.5. Backing vocal lines in Lyr that LRCLIB lacks are not counted against LRCLIB.
4. Word errors: within matched lines, a word-level edit alignment gives substitutions (wrong word), deletions (missing word) and insertions (extra word). Unmatched lead lines count all their words as missing; unmatched LRCLIB lines count as extra.
5. Timing: for each matched line, offset = LRCLIB line time minus the Lyr line's first word start.

The three numbers at the top:

| Number | Definition | Plain words shown under it |
| --- | --- | --- |
| Wrong or missing words | Substitutions + deletions + insertions, LRCLIB versus the official text, with the word error rate in brackets | "Words LRCLIB gets wrong, compared with the band's lyrics" |
| Average line offset | Mean of the absolute offsets over matched lines, in ms; a second line shows the share of lines off by more than 300 ms | "How far off LRCLIB's line timing is, on average" |
| Timing detail | "Lines" for LRCLIB (or "Words" if it carries enhanced word tags), and the count of individually timed words for Lyr | "Lyr times every word" |

Display. Both columns follow playback. The LRCLIB column highlights whole lines by LRCLIB's own timestamps, so the audience sees it run early or late against the music. Wrong words get a 2 px scarlet underline with the official word in a tooltip (the one place scarlet touches lyrics, because marking errors is the point of Compare; a wavy underline in the text color is the fallback if Sam wants the neutral rule kept strictly); extra words are struck through in soft text; missing words appear as a small teal caret with the missing word in light italic. The Lyr column is the normal word-by-word stage at a smaller size. The offset lane under the columns draws one bar per matched line (up for late, down for early, scaled so 1 second is 40 px, capped at 2 seconds); the current line's bar is bright and clicking a bar seeks to that line.

Honesty. A fixed footnote under the columns: "Lyr timing in this demo is Sam's own hand-corrected work, standing in for the band's. LRCLIB lyrics are community-contributed, from lrclib.net." The metrics compare LRCLIB against the official text from the band's Bandcamp, which is the fair reference; they never compare LRCLIB against the aligner's raw output.

## Offline and privacy

The player has no network code at all, so "turn off the Wi-Fi" is a demo trick, not a risk.

1. No Tauri HTTP, updater, shell-open-URL or WebSocket plugins are included. `tauri.conf.json` sets a content security policy of `default-src 'self'; connect-src 'none'; img-src 'self' data: blob:; font-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self'`.
2. Tauri capabilities grant only the commands listed in Architecture, file dialogs, and reading files the user picked or dropped.
3. Fonts, icons and images are bundled. No CDN, no Google Fonts, no analytics, no crash reporter, no third-party SDKs.
4. Rust dependencies are checked with `cargo deny` to keep out crates that open sockets (no `reqwest`, `hyper` or `ureq` in the tree); CI fails if one appears.
5. The encrypted store holds trusted signers, settings and recent file paths. Its key lives in the OS keychain through the `keyring` crate. Recent files can be cleared in Settings.
6. About shows a short privacy statement: no network, no accounts, no data collected, what is stored on the device and how to clear it.
7. Everything works the same with no network adapter at all. Acceptance includes a run with the network disabled and a firewall log showing zero attempts.

## Demo kit and Presenter mode

The demo has to work the same way every time, after any number of rehearsals.

The demo kit is a folder (gitignored, on Sam's laptop) that holds:

```
demo-kit/
  pristine/                 never modified by the player
    enemy-of-the-world.lyr  signed by the demo key, band text as posted (typo included)
    maybe-its-me.lyr
    uncooked.lyr
    it-must-really-suck.lyr
    enemy-of-the-world-tampered.lyr
  originals/                the four purchased FLACs, for the bit-perfect drop
  compare/                  the LRCLIB cache files
  keys/demo-stand-in.lyrpub (and the .lyrkey, passphrase not stored)
  working/                  what the player actually opens; rebuilt on reset
  demo.json                 chapter order, which song each chapter opens, start positions
```

`tools/build-demo-kit` (Rust, in the player repo) builds this from `private/` using the `lyr` CLI: packs each song, makes the tampered copy, copies the originals, and writes `demo.json` with defaults Sam can edit.

Reset. Settings has "Reset demo" (and Presenter mode has Ctrl+Shift+R): it empties `working/`, copies `pristine/` into it, re-trusts the demo key, clears the edited copies, closes panels and returns to the Library. It takes under 2 seconds and asks once for confirmation.

Presenter mode (F5 to enter, Escape to leave):

1. Fullscreen, the chapter rail on the left, the Large lyrics layout available with L.
2. Keys 1 to 4 jump to a chapter: each opens its song at its saved start position and opens the right panel (1 Now Playing, then Inside; 2 Compare; 3 Artist mode at the typo line; 4 Quality). Right arrow or Page Down (the keys a clicker sends) moves to the next step within a chapter, for example Inside the file, then Open as zip.
3. A presenter notes window can open on a second screen (N): the current chapter's talking points from `demo.json`, a clock and the next step. The audience screen never shows notes.
4. The mouse cursor hides after 2 seconds of no movement.

Rehearsal kit. The repo includes `docs/demo-script.md`: the five-minute script from the story table, a pre-demo checklist (laptop on power, notifications off, volume set, sync offset checked with the tap test on the actual speakers or headphones, Reset demo, Wi-Fi off), and what to do if something goes wrong (switch to the backup screen recording, which Sam records after the final rehearsal).

## Acceptance criteria

The player is ready to show when all of these pass on Sam's Windows laptop with the demo kit.

Sync and sound:

- [ ] On the click-track fixture, a 240 fps phone video shows every word starting within 30 ms of its click, across the whole file, after a seek, and after pause and play.
- [ ] When the output device runs at the file's rate, audio\_status reports no resampling and the samples reach the device unchanged; otherwise it reports the resampling honestly.
- [ ] A 60 minute soak run shows the clock drifting less than 20 ms from the samples played.
- [ ] No frame over 16 ms during playback with one panel open, in a performance trace.

Opening and trust:

- [ ] Enemy Of The World.lyr opens and starts playing in under 2 seconds from the drop.
- [ ] The pristine demo file shows Verified with the demo stand-in chip; the tampered copy shows Unverified and names the lyrics as the changed part; a file from an unknown key shows Signed by an unknown signer with its key code.
- [ ] Editing a word in Artist mode shows Edited, not signed; signing with the demo key brings back Verified, and the saved file passes `lyr verify`.
- [ ] Every reader conformance fixture from the format spec gives the expected state or error card, and no malformed fixture crashes the app.

Compare:

- [ ] Compare runs entirely from the prepared files with Wi-Fi off, and its counts match a hand count on one song.

Privacy:

- [ ] With Wi-Fi off and a packet capture running, a full run-through makes zero network attempts. A CI test reads the Tauri config and Cargo.lock and fails if an HTTP or updater plugin appears or the CSP loosens.
- [ ] The local store on disk cannot be read without the keychain key.

Design and access:

- [ ] Every token pair passes the contrast test in both themes, and `tokens.css` matches ScarletTokens v1 value for value (a test compares them).
- [ ] A side-by-side with Cawback on a phone shows the same lyric display: line in the true middle, neutral bar, dimming steps, 1.06 scale, gap dots.
- [ ] axe-core reports no serious or critical issues on any screen; a keyboard-only run-through of the whole demo works; NVDA reads each badge's state and reason.
- [ ] With reduced motion on, nothing scales, slides or glides, and the sync timing is unchanged.
- [ ] Layouts hold at 1024 by 640 and at the largest text size, in both themes.

Demo:

- [ ] Reset returns the demo kit to its starting state in under 2 seconds.
- [ ] Presenter mode runs chapters 1 to 4 from keys 1 to 4, and the notes window works on a second screen.
- [ ] Ten full rehearsals in a row with no crash, no stuck audio and no wrong badge.
- [ ] The installer installs and uninstalls cleanly on a fresh Windows 11 account.

## Phased build order

Each phase is sized for one or two Code sessions and ends with something Sam can open. The player needs the packer's steps 1 to 6 for any real .lyr file and step 7 for word-timed files; until those exist, Code works from the format spec's fixtures, so Y0 to Y3 can start right away.

| Phase | What gets built | Done when |
| --- | --- | --- |
| Y0 Shell | Tauri 2 app with React and TypeScript, `tokens.css` copied from ScarletTokens v1, fonts and raven from the website repo, CSP, CI with cargo deny, contrast and axe tests | The empty state opens in both themes and CI is green |
| Y1 Open and verify | open\_file over lyr-core, the three badges and popovers, error cards, recent files in the encrypted store | Every conformance fixture gives the right state |
| Y2 Audio and clock | FLAC decode, cpal output, transport, position clock and events, sync offset and tap test, audio\_status | The soak test passes and the format chip tells the truth |
| Y3 Lyric stage | Word fill, Cawback's line display (middle, bar, dimming, scale, glide, gap dots, end note), follow delay, click to seek, album tint, text sizes, reduced motion | The click-track video is within 30 ms |
| Y4 Inside and Quality | Parts bar, hash ticks, Open as zip, the quality panel | Every part of the demo file shows and checks out |
| Y5 Compare | `tools/fetch-lrclib.ts`, line and word alignment, the two-column view and its footnote | Counts match a hand count, offline |
| Y6 Artist mode | Word editor, sign sheet, keys, `tools/make-tampered` | The edit, sign and tamper round trip passes `lyr verify` |
| Y7 Presenter and demo kit | demo-kit layout, build and reset tools, Presenter mode, notes window, demo script | A full rehearsal runs from the keys alone |
| Y8 Polish and ship | Accessibility pass, minimum size and largest text, installer, notices, ten rehearsals | Every acceptance criterion passes |
| Y9 Later: .lyr in Cawback | lyr-core built for Android and called from Kotlin through UniFFI, and an "Open a .lyr" path in Cawback that plays the file's own audio with the same lyric stage | Planned with Sam first. Cawback is GPL-3.0, and lyr-core's MIT or Apache license is compatible with it |

The demo is ready to rehearse after Y7; Y8 is what makes it safe to show.
