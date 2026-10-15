# Cawback 0.5.0: release notes (draft for the owner's review)

Title: **Cawback 0.5.0: Your timing**

Draft body for the GitHub release (not created yet):

---

Cawback picks better timing, and when it's still off you can fix it yourself and share the fix.

**Better matches**
- Cawback now picks the timing most uploads agree on, instead of trusting a single upload that may be off by seconds.
- Timing made for a longer cut of the song (lines that run past the end) is passed over.
- Cawback never shows another artist's song on its own just because the title matches. "Other versions" still lists those.
- A dot on This song means Cawback isn't sure of the timing.
- Songs matched by an older version are looked up again the next time they play, unless you picked them by hand or timed them yourself.

**This song**
- A new icon at the top opens This song: the version showing, Other versions, and your timing.
- Earlier and Later nudge every line. Picking another version starts its timing at 0.
- Line up now: tap Now when you hear the next line, and Cawback moves the timing to match.
- Fix lines: tap Now on line after line to fix timing that drifts.
- Clear my timing puts the version back as it came.
- "Other versions" lists every version, best fit first, and says which ones run past the song's end.
- The note after the last line no longer has a button; This song is always at the top.

**Share your fix**
- After Line up now or Fix lines, Share your fix sends your timing to LRCLIB, the open lyrics database, so everyone gets it. You see exactly what's sent first, and nothing is sent until you tap Send.

**Library backup**
- Backups now include your picks and timing (never lyric text), and restoring brings them back.

**Settings**
- "Reset all timing" resets nudges and fixed lines on every song.

**Privacy**
- No new permissions. Still connects only to lrclib.net; sharing a fix sends only what the Share page shows, and only when you tap Send.
- Cawback follows LRCLIB's rules for apps: one request at a time, and it waits when LRCLIB asks. Other versions says "LRCLIB is busy" when that happens.

Android 14 or newer. Updating from 0.4.1 keeps your settings, saved lyrics, picks, nudges and Library. Check the download against the `.sha256` file next to it.

---

Assets to attach (from `release.ps1`): `cawback-0.5.0.apk` and `cawback-0.5.0.apk.sha256`, plus the same file as `cawback.apk` and `cawback.apk.sha256`.

Note for the owner: your own devices stay on personal builds (D-023) and never get this APK.
