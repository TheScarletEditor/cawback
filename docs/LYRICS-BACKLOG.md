# Scarlet Lyrics backlog

Owner wants waiting for a version. Nothing here is scheduled until the owner picks it into a plan. Mirrored in scarlet-hq `projects/scarlet-lyrics/IDEAS.md`.

## LB-1 · Send notes into Command's Journal (owner, 2026-10-12)

Owner's words: "one way, optional, sending notes into Command's Journal."

- **One way:** Lyrics sends, Command receives. Command never reads back into Lyrics.
- **Optional:** off unless the owner turns it on.
- Pairs with Command's journal polish (Command `docs/LATER.md`, "Journal polish").

Questions to settle before planning:
1. **Public or personal build?** The owner ruled on 2026-10-10 that the Command link stays out of public Lyrics (Command `docs/STATUS-AUDIT.md`). As written, this feature would live only in the owner's personal build. Confirm that, or decide whether a public, harmless form exists (for example an ordinary Android share of a note, which needs no door into Command).
2. **Which notes:** song notes only, or bookmarked moments too? Lyric text never leaves the app either way (the Library never stores it).
3. **When:** on each save, or a "Send to Journal" action per note?
4. **Transport:** the Command link (Noise over Bluetooth, the tablet) or an on-device handoff on the same device. The earlier Library-sync items deferred to the Command link work (scarlet-hq IDEAS, 2026-10-11) apply if it rides the link.
5. **How it lands in the Journal:** typed text in a handwritten journal; it should follow whatever the journal polish decides for inserted prompts.

## LB-2 · Sing-along mode (owner, 2026-10-12)

Owner's words: "Sing a long stays as a back burner some day possibility, not v7. We avoid tech giants whenever and wherever possible." Back burner, not scheduled, not 0.7.

Viability check (planning chat, 2026-10-12):

- **Out:** vocal removal (Lyrics never touches another app's audio) and pitch scoring (needs the microphone and melody data no open source has).
- **Line-level, every synced song:** a big two-line layout (current and next), lead-in dots before a line after a gap (0.6 break detection, on the beat when the beat is confident), backing vocals in parentheses shown dimmer, full screen and screen on as a header toggle.
- **Real word-by-word fill only when the record has it:** `Lrc.kt` already recognises enhanced LRC word stamps and drops them (line 27); keeping them is small. Rare on LRCLIB, though some tools now publish word timing there. No guessed word fill: people follow it, so a wrong one is worse than none.
- **Timing bar is higher:** singing exposes errors that reading forgives, so 0.5.0's timing tools and the headphone delay are prerequisites.
- **No tech giants:** no Chromecast or Google Cast SDK. A TV, if ever, only through a wired or screen-mirrored display (Android's own Presentation API, offline).
- **First step if it's ever picked up:** count how many cached records carry word stamps, to size the word fill.
