# Scarlet Lyrics 0.4.0: release notes (draft for the owner's review)

Title: **Scarlet Lyrics 0.4.0: Your player, your Library**

Draft body for the GitHub release (not created yet):

---

Your music app, your settings, and a private Library that's off until you turn it on.

**Your player**
- Follows any music app you pick, or whatever is playing. Follow only your app, or other apps too.
- "Never follow" list: ignored apps are never looked up.
- "Lyrics + your app" home screen shortcut opens both side by side.
- Text size, sung lines dimmed or hidden, and how soon it follows the song again after you scroll.
- Keep the screen on while synced lyrics play.
- Timing per output: set a delay once for the speaker, wired headphones and Bluetooth.
- Flex mode on flip phones: lyrics above the fold, controls below.

**Your Library (optional, off by default)**
- Listening history: off, only while Lyrics is open, or always.
- Songs and Stats: search, sort, favorites, notes, and stats for this week, month, year or all time.
- Long-press a line to bookmark the moment (time and label only, never the words).
- Pause history, choose how long plays are kept, forget a song, or delete it all.
- Export as text or CSV. Never includes lyrics.
- Passphrase-protected backup (Argon2id and AES-256-GCM); restore adds to what's there.
- Settings > What's on this device shows exactly what Lyrics keeps and what it sends.

**Privacy**
- No new permissions. Still connects only to lrclib.net, still reads only title, artist and position.
- The Library is encrypted on your device and never stores lyrics, which app played a song, or your location.
- Android's own cloud backup is off for Lyrics: use Library backup to move to a new phone.

**Fixes**
- More sheet scrolls in short split-screen windows, so Settings is always reachable.
- Small layout fixes on small phones.

Android 14 or newer. Check the download against the `.sha256` file next to it.

---

Assets to attach (from `release.ps1`): `scarlet-lyrics-0.4.0.apk` and `scarlet-lyrics-0.4.0.apk.sha256`.

Note for the owner: updating from 0.2.0 keeps saved lyrics, because both are signed with the release key. Your own devices stay on personal builds (D-023) and never get this APK.
