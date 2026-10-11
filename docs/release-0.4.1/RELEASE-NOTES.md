# Scarlet Lyrics 0.4.1: release notes (draft for the owner's review)

Title: **Scarlet Lyrics 0.4.1: Easier to read, easier to set up**

Draft body for the GitHub release (not created yet):

---

A polish release: the current line stays in the middle, Settings has tabs, and you can see a text size before you pick it.

**Lyrics**
- The current line sits in the middle of the screen and stays there when you resize the split screen, rotate, fold or change the text size.
- After you scroll by hand, Lyrics waits from the moment you let go before it follows the song again.

**Settings**
- Four tabs: Lyrics, Player, Library and Privacy. On narrow screens and at large font sizes the tabs wrap onto more rows, so none is hidden.
- Settings opens on the tab you need: "Choose your music app" opens Player, and the Library's "Open Settings" opens Library.
- A preview shows how the lyrics will look at each text size.
- Backups, restores and deletes finish even if you switch tabs or leave Settings, and the result shows when you come back. Only one of them runs at a time.
- In a short window (landscape split screen) the whole Settings page scrolls, so every setting can be reached.
- Choice buttons wrap at large font sizes instead of squeezing their words.

**Fixes**
- Resetting timing nudges or clearing saved lyrics now says "That didn't finish. Try again." if something went wrong, instead of reporting success or closing the app.

**Privacy**
- No new permissions. Still connects only to lrclib.net. Nothing new is stored.

Android 14 or newer. Updating from 0.4.0 keeps your settings, saved lyrics and Library. Check the download against the `.sha256` file next to it.

---

Assets to attach (from `release.ps1`): `scarlet-lyrics-0.4.1.apk` and `scarlet-lyrics-0.4.1.apk.sha256`.

Note for the owner: your own devices stay on personal builds (D-023) and never get this APK.
