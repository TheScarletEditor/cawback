# Scarlet Lyrics

Lyrics for whatever you're playing, scrolling along with the song. By [The Scarlet Coder](https://thescarletcoder.com).

- Follows the song playing in your music app and highlights the line being sung.
- Lyrics come from [LRCLIB](https://lrclib.net), a free, open lyrics library. That is the only place the app connects to.
- Reads only the song title, artist and position. It never reads your notifications. To find the words, it sends the song's title, artist, album and length to lrclib.net.
- Listening history is off until you turn it on (Settings > Listening history: only while Lyrics is open, or always). When it's on, your Library counts the songs you play and keeps your notes, favorites and bookmarked moments, in an encrypted database on your device. It stores the song, when you played it and for how long; never which app, your location or any lyrics. Pause it, prune it, forget one song or delete it all, any time. Nothing in it leaves your device unless you send it: Library > Export makes plain text (with an optional note explaining the format, for pasting into an AI) or CSV, and goes only where you share or save it. It never includes lyrics.
- Library backup (Settings): your whole Library in one file, locked with a passphrase you choose (Argon2id and AES-256-GCM). Restoring adds the backup to what's on the device. It's the only way to keep your Library when you change phones, since Android's own backups are switched off for Lyrics.
- A small on-device cache keeps fetched lyrics, excluded from backups. Each saved song includes its title and artist. Settings shows how many songs are saved and has "Clear saved lyrics".
- "Wrong lyrics" lets you pick a different match, and it sticks to that track.
- Works in split screen next to your music app. Open it while your music app is playing and the app comes up beside it (unless the screen is already split).
- If the connection drops, it tries again quietly and the lyrics appear once you're back online.
- Light and dark (or following the system). While a song plays, a little of its album cover tints the background, taken from the art your music app already shares with Android. Art is never downloaded, and the tint is never saved.
- No ads, no tracking, no account.

## Build and install

Needs the Android SDK and JDK 17 (Android Studio's bundled JDK works).
`install.ps1` builds the `personal` type: optimized like a release, signed with your own debug key (`-Debug` for a debug build).

```
powershell -ExecutionPolicy Bypass -File .\install.ps1
```

On first launch, allow notification access when asked. Android requires it for an app to see what's playing.

## Credits

- Lyrics: [LRCLIB](https://lrclib.net).
- Fonts: Inter and Fraunces, SIL Open Font License 1.1 (`licenses/`).

## License

Scarlet Lyrics is free software: you can use it, study it, change it and share it under the [GNU General Public License v3.0](LICENSE). If you share a changed version, it has to stay open under the same license.

Copyright (C) 2026 The Scarlet Coder. The fonts keep their own license, listed under Credits.
