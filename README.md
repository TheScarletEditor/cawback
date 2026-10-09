# Scarlet Lyrics

Lyrics for whatever you're playing, scrolling along with the song. By [The Scarlet Coder](https://thescarletcoder.com).

- Follows the song playing on your device (Spotify first, otherwise whatever is playing) and highlights the line being sung.
- Lyrics come from [LRCLIB](https://lrclib.net), a free, open lyrics library. That is the only place the app connects to.
- Reads only the song title, artist and position. It never reads your notifications. To find the words, it sends the song's title, artist, album and length to lrclib.net.
- Keeps no listening history. A small on-device cache stores fetched lyrics under hashed file names, excluded from backups. Settings shows how many songs are saved and has "Clear saved lyrics".
- "Wrong lyrics" lets you pick a different match, and it sticks to that track.
- Works in split screen next to your music app. Open it while Spotify is playing and Spotify comes up beside it (unless the screen is already split).
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

Copyright (C) 2026 The Scarlet Coder. Fonts and the word list keep their own licenses, listed under Credits.
