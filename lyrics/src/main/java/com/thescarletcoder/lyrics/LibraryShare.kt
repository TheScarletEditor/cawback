package com.thescarletcoder.lyrics

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PersistableBundle
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.thescarletcoder.lyrics.library.LibraryBackup
import com.thescarletcoder.lyrics.library.LibraryDatabase
import com.thescarletcoder.lyrics.library.LibraryExport
import com.thescarletcoder.lyrics.library.LibraryExport.Which
import com.thescarletcoder.lyrics.library.LibraryKey
import com.thescarletcoder.lyrics.library.LibraryRepo
import com.thescarletcoder.lyrics.library.LibrarySnapshot
import com.thescarletcoder.lyrics.library.SongKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/*
 * Library > Export and Settings > Library backup (scope section 6). Nothing here runs unless the user taps it, and an
 * export or a backup goes only where they send it.
 */

private enum class Range(val label: String) { WEEK("Last 7 days"), MONTH("Last 30 days"), YEAR("This year"), LAST_YEAR("Last year"), CUSTOM("Pick dates") }

private fun count(n: Int, one: String, many: String = one + "s") = "$n " + if (n == 1) one else many

/** Backup, restore or delete found the Library's key gone: the Library screen offers a new one. */
const val KEY_GONE = "Your Library can't be opened on this device any more. Open the Library to start a new one, then restore."

/** Library > Export. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }
    var failed by remember { mutableStateOf<Throwable?>(null) }
    val snap by produceState<LibrarySnapshot?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { LibraryRepo(context).snapshot() } }.onFailure { failed = it }.getOrNull()
    }

    var which by remember { mutableStateOf(Which.ALL) }
    var range by remember { mutableStateOf(Range.MONTH) }
    var custom by remember { mutableStateOf<Pair<LocalDate, LocalDate>?>(null) }
    var picking by remember { mutableStateOf(false) }
    var artist by remember { mutableStateOf(TextFieldValue("")) }
    var counts by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf(true) }
    var moments by remember { mutableStateOf(true) }
    var aiHeader by remember { mutableStateOf(true) }
    var format by remember { mutableStateOf(LibraryExport.Format.TEXT) }
    var status by remember { mutableStateOf<String?>(null) }

    val today = LocalDate.now(zone)
    val (from, to) = when (range) {
        Range.WEEK -> today.minusDays(6) to today
        Range.MONTH -> today.minusDays(29) to today
        Range.YEAR -> today.withDayOfYear(1) to today
        Range.LAST_YEAR -> today.minusYears(1).withDayOfYear(1) to today.withDayOfYear(1).minusDays(1)
        Range.CUSTOM -> custom ?: (today.minusDays(29) to today)
    }
    val options = LibraryExport.Options(
        which = which,
        from = if (which == Which.RANGE) from.atStartOfDay(zone).toInstant().toEpochMilli() else null,
        to = if (which == Which.RANGE) to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() else null,
        artist = artist.text.trim().takeIf { which == Which.ARTIST },
        counts = counts, notes = notes, moments = moments, aiHeader = aiHeader && format == LibraryExport.Format.TEXT, format = format,
    )
    val songs = snap?.let { LibraryExport.rows(it, options).size }
    val name = "lyrics-library-$today.${format.ext}"
    fun build() = LibraryExport.build(snap!!, options, System.currentTimeMillis(), zone)

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(format.mime)) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = runCatching {
                if (snap == null) error("not loaded")
                val bytes = build().toByteArray()
                withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
                    } catch (e: Exception) {
                        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }   // no empty file left behind
                        throw e
                    }
                }
                "Saved."
            }.getOrElse {
                if (snap == null) {
                    runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                    "Couldn't save the file. Try again."
                } else "Couldn't save the file."
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text("Export", fontFamily = Fraunces, fontSize = 28.sp, color = L.text)
        Text("Titles, artists and your own words. Never any lyrics. It goes only where you send it.",
            fontFamily = Inter, fontSize = 15.sp, color = L.soft, modifier = Modifier.padding(top = 6.dp))

        SettingsSection("What")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Which.entries.forEach { w -> Choice(w.label, which == w) { which = w } }
        }
        if (which == Which.RANGE) {
            FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Range.entries.forEach { r -> Choice(r.label, range == r) { range = r; if (r == Range.CUSTOM) picking = true } }
            }
            Text("$from to $to", fontFamily = Inter, fontSize = 15.sp, color = L.soft, modifier = Modifier.padding(top = 8.dp))
        }
        if (which == Which.ARTIST) {
            Box(Modifier.padding(top = 12.dp)) { SearchField(artist, { artist = it }, {}, hint = "Artist") }
            val q = artist.text.trim().lowercase()
            val names = remember(snap, q) {
                snap?.songs.orEmpty().map { SongKey.primaryArtist(it.artist) }.distinctBy { it.lowercase() }
                    .filter { q.isEmpty() || it.lowercase().contains(q) }.sortedBy { it.lowercase() }.take(8)
            }
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                names.forEach { n -> Choice(n, n.equals(artist.text.trim(), true)) { artist = TextFieldValue(n) } }
            }
        }

        SettingsSection("Include")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Play counts and dates", counts) { counts = !counts }
            Choice("Notes", notes) { notes = !notes }
            Choice("Moments", moments) { moments = !moments }
        }

        SettingsSection("Format")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryExport.Format.entries.forEach { f -> Choice(f.label, format == f) { format = f } }
        }
        if (format == LibraryExport.Format.TEXT) {
            Box(Modifier.padding(top = 8.dp)) { Choice("Explain the format at the top (for an AI)", aiHeader) { aiHeader = !aiHeader } }
        }

        Text(
            when {
                failed is LibraryKey.Gone -> KEY_GONE
                failed != null -> "The Library couldn't be opened just now. Go back and try again."
                songs == null -> " "
                songs == 0 -> "Nothing to export with these choices."
                else -> count(songs, "song") + "."
            },
            fontFamily = Inter, fontSize = 16.sp, color = L.text, modifier = Modifier.padding(top = 20.dp),
        )
        if (songs != null && songs > 0) {
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Washed("Share", { scope.launch { status = share(context, build(), name, format) } })
                Washed("Save as a file", { status = null; save.launch(name) })
                Washed("Copy", { status = copy(context, build()) })
            }
        }
        status?.let { Text(it, fontFamily = Inter, fontSize = 15.sp, color = L.soft, modifier = Modifier.padding(top = 10.dp)) }
        Box(Modifier.heightIn(min = 32.dp))
    }

    if (picking) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    val s = state.selectedStartDateMillis; val e = state.selectedEndDateMillis ?: s
                    if (s != null && e != null) custom = utcDay(s) to utcDay(e)
                    picking = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DateRangePicker(state, Modifier.heightIn(max = 520.dp)) }
    }
}

/** The date picker hands back UTC midnights. */
private fun utcDay(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()

/**
 * Share sheet. Short plain text goes as text (it pastes straight into a chat); anything bigger, or CSV, goes as a
 * file: share intents choke on large strings (scope section 6).
 */
private suspend fun share(context: Context, body: String, name: String, format: LibraryExport.Format): String? {
    val send = Intent(Intent.ACTION_SEND).setType(format.mime)
    if (format == LibraryExport.Format.TEXT && body.length < 60_000) {
        send.putExtra(Intent.EXTRA_TEXT, body)
    } else {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { mkdirs(); listFiles()?.forEach { it.delete() } }   // only the latest export stays
            File(dir, name).apply { writeText(body) }
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".exports", file)
        send.putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    send.putExtra(Intent.EXTRA_TITLE, name)
    return runCatching { context.startActivity(Intent.createChooser(send, "Send your Library to")); null }.getOrElse { "Nothing on this device can receive it." }
}

/** Clipboard, marked sensitive so Android doesn't show it in the copy preview. */
private fun copy(context: Context, body: String): String {
    if (body.length > 500_000) return "That's too big to copy. Save it as a file instead."
    val clip = ClipData.newPlainText("Scarlet Lyrics library", body)
    clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    return "Copied."
}

/**
 * Settings > Library backup: back up to a file the user picks, or restore from one, locked with their passphrase.
 * The backup and the restore run on [SettingsWork], so leaving the tab or Settings never stops them halfway; the
 * result shows on the way back. Passphrases live only in this screen's fields, never in saved state.
 */
@Composable
fun LibraryBackupSection() {
    val context = LocalContext.current
    val runner = SettingsWork.runner
    @Suppress("UNUSED_VARIABLE") val work = SettingsWork.version.intValue   // redraw when work starts or ends
    var mode by remember { mutableStateOf<String?>(null) }   // "backup" or "restore" while the passphrase is asked
    var pass by remember { mutableStateOf(TextFieldValue("")) }
    var again by remember { mutableStateOf(TextFieldValue("")) }
    var restoreFrom by remember { mutableStateOf<Uri?>(null) }
    var note by remember { mutableStateOf<String?>(null) }   // a hint from this screen, not from the work
    val backingUp = runner.busy(SettingsWork.BACKUP)
    val restoring = runner.busy(SettingsWork.RESTORE)
    val busy = backingUp || restoring
    fun forget() { pass = TextFieldValue(""); again = TextFieldValue("") }
    // New work: older messages here, and a delete's, would be out of date.
    fun fresh() { note = null; runner.clear(SettingsWork.BACKUP); runner.clear(SettingsWork.RESTORE); runner.clear(SettingsWork.DELETE) }

    val saveTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val p = pass.text.toCharArray()
        forget(); mode = null
        if (uri == null) { p.fill(' '); return@rememberLauncherForActivityResult }
        if (p.size < LibraryBackup.MIN_PASSPHRASE) {   // Android stopped Lyrics while the picker was open: never back up without it
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            note = "Enter your passphrase again, then choose where to save."
            return@rememberLauncherForActivityResult
        }
        fresh()
        val app = context.applicationContext
        val started = runner.run(SettingsWork.BACKUP) {
            try {
                runCatching {
                    withContext(Dispatchers.IO) {
                        // Encrypt fully first, then write, so a failure never leaves a broken backup file behind.
                        val snap = LibraryRepo(app).snapshot()
                        val bytes = ByteArrayOutputStream().also { LibraryBackup.write(it, snap, p) }.toByteArray()
                        try {
                            app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
                        } catch (e: Exception) {
                            runCatching { DocumentsContract.deleteDocument(app.contentResolver, uri) }
                            throw e
                        }
                        snap
                    }
                }.fold({ "Backed up ${count(it.songs.size, "song")} and ${count(it.plays.size, "play")}. Keep the passphrase somewhere safe: without it this backup can't be opened." },
                    { e -> if (e is LibraryKey.Gone) KEY_GONE else "The backup didn't finish. Nothing was changed." })
            } finally { p.fill(' ') }
        }
        if (!started) p.fill(' ')
    }
    val openFrom = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) { restoreFrom = uri; mode = "restore"; fresh() }
    }

    Text(
        "A backup holds your whole Library (never any lyrics), locked with a passphrase you choose. It's the only way to " +
            "keep your Library when you change phones or reinstall. Restoring adds the backup to what's here; nothing here is lost.",
        fontFamily = Inter, fontSize = 15.sp, color = L.soft, modifier = Modifier.padding(bottom = 10.dp),
    )
    when (mode) {
        null -> if (!busy) FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (LibraryDatabase.exists(context)) Washed("Back up the Library", { mode = "backup"; fresh() })
            Washed("Restore from a backup", { fresh(); openFrom.launch(arrayOf("*/*")) })
        }
        "backup" -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Secret(pass, "Passphrase (at least ${LibraryBackup.MIN_PASSPHRASE} characters)") { pass = it }
            Secret(again, "The same passphrase again") { again = it }
            val ok = pass.text.length >= LibraryBackup.MIN_PASSPHRASE && pass.text == again.text
            if (again.text.isNotEmpty() && pass.text != again.text) Text("The two don't match.", fontFamily = Inter, fontSize = 14.sp, color = L.soft)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (ok && !busy) Washed("Choose where to save", { saveTo.launch(LibraryBackup.fileName(LocalDate.now())) })
                Washed("Cancel", { forget(); mode = null })
            }
        }
        "restore" -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Secret(pass, "The backup's passphrase") { pass = it }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (pass.text.isNotEmpty() && !busy) Washed("Restore", {
                    val uri = restoreFrom ?: return@Washed
                    val p = pass.text.toCharArray()
                    fresh()
                    val app = context.applicationContext
                    // The fields and the form are this screen's; if it's gone by the end, these writes go nowhere.
                    val started = runner.run(SettingsWork.RESTORE) {
                        try {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    val snap = app.contentResolver.openInputStream(uri)!!.use { LibraryBackup.read(it, p) }
                                    LibraryRepo(app).restore(snap)
                                }
                            }.fold(
                                { r -> forget(); mode = null; restoreFrom = null; "Restored. Your Library now has ${count(r.songsAfter, "song")} (was ${r.songsBefore}) and ${count(r.playsAfter, "play")} (was ${r.playsBefore})." },
                                { e ->
                                    when (e) {
                                        is LibraryKey.Gone -> KEY_GONE
                                        is LibraryBackup.WrongPassphrase -> "That passphrase doesn't open this backup, or the file is damaged or incomplete."
                                        is LibraryBackup.NotABackup -> { forget(); mode = null; restoreFrom = null; "That file isn't a Scarlet Lyrics Library backup." }
                                        is LibraryBackup.Unreadable -> e.message ?: "This backup can't be read."
                                        is IllegalArgumentException -> "This backup can't be read."
                                        else -> "The restore didn't finish. Your Library wasn't changed."
                                    }
                                },
                            )
                        } finally { p.fill(' ') }
                    }
                    if (!started) p.fill(' ')
                })
                if (!busy) Washed("Cancel", { forget(); mode = null; restoreFrom = null })
            }
        }
    }
    val status = when {
        backingUp -> "Backing up..."
        restoring -> "Restoring..."
        else -> runner.result(SettingsWork.BACKUP) ?: runner.result(SettingsWork.RESTORE) ?: note
    }
    status?.let { Text(it, fontFamily = Inter, fontSize = 15.sp, color = L.text, modifier = Modifier.padding(top = 10.dp)) }
}

/** A passphrase field: hidden, and a password field so keyboards (Scarlet Keys included) never learn from it. */
@Composable
private fun Secret(value: TextFieldValue, hint: String, onChange: (TextFieldValue) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        textStyle = TextStyle(color = L.text, fontSize = 17.sp, fontFamily = Inter), cursorBrush = SolidColor(L.text),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().background(L.wash, shape).border(1.dp, L.washEdge, shape).padding(14.dp)) {
                if (value.text.isEmpty()) Text(hint, fontFamily = Inter, fontSize = 17.sp, color = L.faint)
                inner()
            }
        },
    )
}

/**
 * Settings > What's on this device (scope section 8): everything Scarlet Lyrics keeps, with real counts, and where
 * each one goes. [refresh] changes when something in Settings cleared or restored data, so the counts follow.
 */
@Composable
fun OnThisDevice(repo: LyricsRepo, refresh: Any?) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    data class Facts(val cached: Int, val library: com.thescarletcoder.lyrics.library.LibraryCounts?, val exports: Int, val hasLibrary: Boolean)
    val facts by produceState<Facts?>(null, refresh) {
        value = withContext(Dispatchers.IO) {
            val has = LibraryDatabase.exists(context)
            Facts(
                repo.savedCount(),
                if (has) runCatching { LibraryRepo(context).counts() }.getOrNull() else null,
                File(context.cacheDir, "exports").listFiles()?.size ?: 0,
                has,
            )
        }
    }
    val f = facts ?: return
    fun n(n: Int, one: String, many: String = one + "s") = count(n, one, many)

    Item(
        "Saved lyrics: " + n(f.cached, "song"),
        "Lyrics already fetched, so a song you've played before shows up at once and works offline, with your " +
            "\"Other versions\" picks and timing nudges. Each one includes the song's title and artist. Encrypted on this device; " +
            "goes nowhere, and Android's backups are switched off for Lyrics.",
    )
    val lib = f.library
    Item(
        if (lib == null) (if (f.hasLibrary) "Library: can't be opened on this device right now" else "Library: empty") else "Library: " + listOf(
            n(lib.songs, "song"), n(lib.plays, "play"), n(lib.notes, "note"), n(lib.moments, "moment"), n(lib.favorites, "favorite"),
        ).joinToString(", ") + if (lib.earlierNotes > 0) " (and " + n(lib.earlierNotes, "earlier note") + ")" else "",
        "Encrypted, with a key kept by Android's secure key store that never leaves this device. Listening history is " +
            (when (settings.historyMode) {
                com.thescarletcoder.lyrics.library.History.Mode.OFF -> "off"
                com.thescarletcoder.lyrics.library.History.Mode.WHILE_OPEN -> "on while Lyrics is open"
                com.thescarletcoder.lyrics.library.History.Mode.ALWAYS -> "on, always"
            }) + (if (settings.historyPaused()) " (paused)" else "") +
            ". It leaves only when you export it or back it up, and only to where you send it.",
    )
    if (f.exports > 0) Item(
        "Last shared export: " + n(f.exports, "file"),
        "Kept in Lyrics' own temporary storage so the app you shared it with could open it. Replaced at the next export and removed after a day; Android can clear it sooner.",
    )
    Item(
        "Your settings",
        "Your music app, ignored apps, display and timing choices, and history settings. Not a record of anything you played.",
    )
    Item(
        "Sent to lrclib.net",
        "Only to look up words: the title, artist, album and length of the song playing, and what you type into an " +
            "\"Other versions\" search. Nothing else, and to no one else.",
    )
    Item(
        "Never kept",
        "Which app played a song, where you were, your notifications, and album art (the background tint is worked out on the spot and never saved).",
    )
}

@Composable
private fun Item(title: String, body: String) {
    Column(Modifier.padding(top = 10.dp)) {
        Text(title, fontFamily = Inter, fontSize = 16.sp, color = L.text)
        Text(body, fontFamily = Inter, fontSize = 14.sp, color = L.soft, modifier = Modifier.padding(top = 2.dp))
    }
}
