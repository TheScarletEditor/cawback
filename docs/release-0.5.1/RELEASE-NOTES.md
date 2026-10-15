# Cawback 0.5.1: release notes (draft for the owner's review)

Title: **Cawback 0.5.1: Cuts and corrections**

Draft body for the GitHub release (not created yet):

---

Cawback fixes timing for songs whose lyrics were cut differently, makes shared fixes land in the right place, and tidies Settings.

**Songs cut differently**
- When the lyrics are missing a section the song has, or the other way round, Fix lines can now set it right. A tapped line can move as far as it needs to, not just 10 seconds.
- When two tapped lines are far apart in timing, the change happens all at once at the longest pause between them, where the cut is, instead of being spread over every line.
- Fix lines has two new buttons: "Not in this song" hides a line the song doesn't have, and "Missed it" moves on to the next line. The line to tap stays put until you tap one of them or Now.
- Earlier and Later still move the lyrics up to 10 seconds either way.
- Clear my timing brings back any lines you hid.

**Sharing a fix**
- Share your fix now sends the version you fixed under its own title, artist, album and length, so LRCLIB updates that version instead of adding a near-copy.
- A version with no title or artist on LRCLIB can't usefully be shared; This song says so and points you to a named version.

**Settings**
- Your music app lists the apps playing now and the ones you've chosen first; other apps are under "More apps". Bluetooth and the Google app are no longer listed.
- Library's Songs and Stats tabs match the Settings tabs.
- New, off unless you turn it on: delete saved lyrics for songs not played in 90 days. Songs with your own pick or timing are always kept.
- Settings > Privacy > About has "Open source licenses", with every licence in full.

**Library backup**
- Backups now include lines you marked "Not in this song". Backups from older versions still restore; a 0.5.1 backup needs Cawback 0.5.1 or newer.

**Privacy**
- No new permissions. Still connects only to lrclib.net, and a fix is sent only when you tap Send.

Android 14 or newer. Updating from 0.5.0 keeps your settings, saved lyrics, picks, timing and Library. Check the download against the `.sha256` file next to it.

---

Assets to attach (from `release.ps1`): `cawback-0.5.1.apk` and `cawback-0.5.1.apk.sha256`, plus the same file as `cawback.apk` and `cawback.apk.sha256`.
