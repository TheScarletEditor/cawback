package com.thescarletcoder.lyrics

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PersistableBundle
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
import androidx.compose.ui.Alignment
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
import com.thescarletcoder.lyrics.library.LibraryRepo
import com.thescarletcoder.lyrics.library.LibrarySnapshot
import com.thescarletcoder.lyrics.library.SongKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/*
 * Library > Export and Settings > Library backup (scope section 6). Nothing here runs unless Sam taps it, and an
 * export or a backup goes only where she sends it.
 */

private enum class Range(val label: String) { WEEK("Last 7 days"), MONTH("Last 30 days"), YEAR("This year"), LAST_YEAR("Last year"), CUSTOM("Pick dates") }

/** Library > Export. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportPanel(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }
    val snap by produceState<LibrarySnapshot?>(null) { value = withContext(Dispatchers.IO) { LibraryRepo(context).snapshot() } }

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
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(build().toByteArray()) } }
                "Saved."
            }.getOrElse { "Couldn't save the file." }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Export", fontFamily = Fraunces, fontSize = 28.sp, color = L.text, modifier = Modifier.weight(1f))
            Washed("Back", onClose, small = true)
        }
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
            when (songs) { null -> " "; 0 -> "Nothing to export with these choices."; 1 -> "1 song."; else -> "$songs songs." },
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

/** Settings > Library backup: back up to a file Sam picks, or restore from one, locked with her passphrase. */
@Composable
fun LibraryBackupSection(onRestored: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf<String?>(null) }   // "backup" or "restore" while the passphrase is asked
    var pass by remember { mutableStateOf(TextFieldValue("")) }
    var again by remember { mutableStateOf(TextFieldValue("")) }
    var restoreFrom by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    fun forget() { pass = TextFieldValue(""); again = TextFieldValue("") }

    val saveTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val p = pass.text.toCharArray()
        forget(); mode = null
        if (uri == null) { p.fill(' '); return@rememberLauncherForActivityResult }
        busy = true; status = "Backing up..."
        scope.launch {
            status = runCatching {
                withContext(Dispatchers.IO) {
                    val snap = LibraryRepo(context).snapshot()
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { LibraryBackup.write(it, snap, p) }
                    snap
                }
            }.fold({ "Backed up ${it.songs.size} songs and ${it.plays.size} plays. Keep the passphrase somewhere safe: without it this backup can't be opened." },
                { "The backup didn't finish. Nothing was changed." })
            p.fill(' ')
            busy = false
        }
    }
    val openFrom = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) { restoreFrom = uri; mode = "restore"; status = null }
    }

    Text(
        "A backup holds your whole Library (never any lyrics), locked with a passphrase you choose. It's the only way to " +
            "keep your Library when you change phones or reinstall. Restoring adds the backup to what's here; nothing here is lost.",
        fontFamily = Inter, fontSize = 15.sp, color = L.soft, modifier = Modifier.padding(bottom = 10.dp),
    )
    when (mode) {
        null -> if (!busy) FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (LibraryDatabase.exists(context)) Washed("Back up the Library", { mode = "backup"; status = null })
            Washed("Restore from a backup", { status = null; openFrom.launch(arrayOf("*/*")) })
        }
        "backup" -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Secret(pass, "Passphrase (at least ${LibraryBackup.MIN_PASSPHRASE} characters)") { pass = it }
            Secret(again, "The same passphrase again") { again = it }
            val ok = pass.text.length >= LibraryBackup.MIN_PASSPHRASE && pass.text == again.text
            if (again.text.isNotEmpty() && pass.text != again.text) Text("The two don't match.", fontFamily = Inter, fontSize = 14.sp, color = L.soft)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (ok) Washed("Choose where to save", { saveTo.launch(LibraryBackup.fileName(LocalDate.now())) })
                Washed("Cancel", { forget(); mode = null })
            }
        }
        "restore" -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Secret(pass, "The backup's passphrase") { pass = it }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (pass.text.isNotEmpty() && !busy) Washed("Restore", {
                    val uri = restoreFrom ?: return@Washed
                    val p = pass.text.toCharArray()
                    busy = true; status = "Restoring..."
                    scope.launch {
                        status = runCatching {
                            withContext(Dispatchers.IO) {
                                val snap = context.contentResolver.openInputStream(uri)!!.use { LibraryBackup.read(it, p) }
                                LibraryRepo(context).restore(snap)
                            }
                        }.fold(
                            { r -> forget(); mode = null; onRestored(); "Restored. Your Library now has ${r.songsAfter} songs (was ${r.songsBefore}) and ${r.playsAfter} plays." },
                            { e ->
                                when (e) {
                                    is LibraryBackup.WrongPassphrase -> "That passphrase doesn't open this backup."
                                    is LibraryBackup.NotABackup -> { forget(); mode = null; "That file isn't a Scarlet Lyrics Library backup." }
                                    is IllegalArgumentException -> e.message ?: "This backup can't be read."
                                    else -> "The restore didn't finish. Your Library wasn't changed."
                                }
                            },
                        )
                        p.fill(' ')
                        busy = false
                    }
                })
                if (!busy) Washed("Cancel", { forget(); mode = null; restoreFrom = null })
            }
        }
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
