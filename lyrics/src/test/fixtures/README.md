# Test fixtures

Files the tests read that were made by an older Cawback. Not compiled into the app or the tests.

## cawback-0.5.0.lyrbak

`src/test/resources/backups/cawback-0.5.0.lyrbak` is a Library backup written by Cawback 0.5.0's own code (tag `v0.5.0`), with the app's real passphrase settings (Argon2id, 64 MiB, 3 passes). `Restore050BackupTest` restores it with the current code. Invented songs, notes and timings only; the passphrase is in the test.

What's in it: three songs (one with a note, a favorite, an earlier note and a bookmarked moment), four plays, a forgotten song's tombstone, three track links and three timing fixes:

- 9001: picked by hand, nudged and lined up in Fix lines, taps within 2 s of each other.
- 9002: the automatic match with only Earlier and Later.
- 9003: taps far apart in their moves, and a nudge that took the last one past 0.5.0's 10 s limit.

How it was made (2026-11-05): `Gen050.kt` compiled against the `v0.5.0` versions of `LibraryBackup.kt`, `LibrarySnapshot.kt`, `Library.kt`, `LibraryKey.kt`, `YourTiming.kt`, `LyricsStore.kt`, `LrcLib.kt` and `Lrc.kt`, in a plain Kotlin JVM project (Kotlin 2.1.21, Bouncy Castle 1.86, org.json 20240303), with compile-only stand-ins for the few Android and Room classes those files name and the two `LyricsRepo` members `LyricsStore` uses, copied from the same tag. It wrote the file, read it back with 0.5.0's code, and printed what 0.5.0's timing showed for each fix over a made-up record; those lists are in the test.

The salt and IV are random, so making it again gives a different file. The test pins the file's SHA-256, so a swap can't go unnoticed. Replace it only on purpose, and update the hash with it.
