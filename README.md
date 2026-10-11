# Scarlet Lyrics

Lyrics for whatever you're playing, scrolling along with the song. By [The Scarlet Coder](https://thescarletcoder.com).

Android 14 or newer. Free, open source, no ads, no tracking, no account.

## What it does

- Follows the song playing in your music app and highlights the line being sung. Tap a line to jump the song there.
- Timing a little off? Nudge it earlier or later, and it remembers for that song.
- "Wrong lyrics?" lets you pick a different match, and it sticks to that track.
- Works in split screen next to your music app. Open it while your music app is playing and the app comes up beside it (unless the screen is already split).
- If the connection drops, it tries again quietly and the lyrics appear once you're back online.
- Light and dark (or following the system). While a song plays, a little of its album cover tints the background, taken from the art your music app already shares with Android. Art is never downloaded, and the tint is never saved.

## Your player

Settings lets you set it up the way you listen:

- **Your music app.** Pick the app Lyrics follows, or let it follow whatever is playing. Follow only that app (so podcasts and other apps are never looked up), or prefer it and follow others too. Apps on the "Never follow" list are never looked up on lrclib.net.
- **"Lyrics + your app" shortcut.** Adds an icon to your home screen that opens Lyrics and your music app side by side.
- **Lyrics.** Text size, lines already sung dimmed or hidden, how long to wait after you scroll before it follows the song again (2, 4 or 8 seconds), and keeping the screen on while synced lyrics play.
- **Timing.** Some headphones play a little late. Set a delay once each for the speaker, wired headphones and Bluetooth, and Lyrics applies the right one whenever that output is in use.
- **Flex mode.** On a flip phone half-folded on a table, the lyrics sit above the fold and the song, play/pause and More sit below it.

## Your Library (off until you turn it on)

- **Listening history** is off by default. Turn it on in Settings > Listening history: only while Lyrics is open, or always (counts what you play even while Lyrics is closed). A play counts when a track over 30 seconds is listened to for half its length or 4 minutes, whichever comes first. Apps you've ignored are never counted.
- **What it keeps:** the song, when you played it and for how long, plus your notes, favorites and bookmarked moments. Never which app played it, your location or any lyrics. It's kept in an encrypted database on your device.
- **Songs and Stats.** Search and sort your songs, open a song's page, add a note, mark favorites. Stats for this week, this month, this year or all time.
- **Moments.** Long-press a lyric line to bookmark that moment in the song. It saves the time and an optional label, never the words.
- **You stay in charge.** Pause history for an hour or until you turn it back on, keep plays forever, for a year or for 90 days, forget one song, delete your history, or delete the whole Library.
- **Export.** Library > Export makes plain text (with an optional note explaining the format, for pasting into an AI) or CSV. It goes only where you share or save it, and never includes lyrics.
- **Backup.** Settings > Library backup puts your whole Library in one file, locked with a passphrase you choose (Argon2id and AES-256-GCM). Restoring adds the backup to what's on the device. It's the only way to keep your Library when you change phones, since Android's own backups are switched off for Lyrics. Don't lose the passphrase: without it the file can't be opened.

## Privacy and permissions

- **Internet, for one site only.** Lyrics come from [LRCLIB](https://lrclib.net), a free, open lyrics library, over HTTPS. That is the only place the app connects to. To find the words, it sends the song's title, artist, album and length, and anything you type into a "Wrong lyrics?" search. As with any website, LRCLIB also sees your IP address.
- **Notification access**, because Android requires it for an app to see what's playing. Lyrics sees only what's playing: the song's title, artist, album, length, position and cover art (the cover only tints the screen and is never saved). It never reads your notifications.
- **Nothing from your Library leaves your device** unless you export it or make a backup, and then it goes only where you choose.
- **Saved lyrics:** a small encrypted on-device cache keeps fetched lyrics (each with its song title and artist), your "Wrong lyrics?" picks and timing nudges. It's excluded from backups. Settings shows how many songs are saved and has "Clear saved lyrics".
- **Settings > What's on this device** shows exactly what Lyrics keeps, with real counts, and what it sends to lrclib.net.
- The full privacy policy is at [thescarletcoder.com/privacy](https://thescarletcoder.com/privacy).

## Build and install

Needs the Android SDK and JDK 17 (Android Studio's bundled JDK works).
`install.ps1` builds the `personal` type: optimized like a release, signed with your own debug key (`-Debug` for a debug build).

```
powershell -ExecutionPolicy Bypass -File .\install.ps1
```

On first launch, allow notification access when asked.

Tests: `gradlew :lyrics:testDebugUnitTest` runs the JVM tests. `labtest.ps1` runs the on-device tests on a connected device against Lyrics Lab, a separate test copy with its own data and no network, which is removed again when the run ends.

## Credits

- Lyrics: [LRCLIB](https://lrclib.net).
- Fonts: Inter and Fraunces, SIL Open Font License 1.1 (`licenses/`).
- Encrypted Library: SQLCipher by Zetetic, BSD-style license (`licenses/`).
- Backup key derivation: Bouncy Castle, MIT license (`licenses/`).

## License

Scarlet Lyrics is free software: you can use it, study it, change it and share it under the [GNU General Public License v3.0](LICENSE). If you share a changed version, it has to stay open under the same license.

Copyright (C) 2026 The Scarlet Coder. The fonts and bundled libraries keep their own licenses, listed under Credits.
