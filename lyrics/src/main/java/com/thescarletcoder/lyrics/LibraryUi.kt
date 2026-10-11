package com.thescarletcoder.lyrics

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thescarletcoder.lyrics.library.History
import com.thescarletcoder.lyrics.library.LibraryDatabase
import com.thescarletcoder.lyrics.library.LibraryKey
import com.thescarletcoder.lyrics.library.LibraryRepo
import com.thescarletcoder.lyrics.library.LibraryStats
import com.thescarletcoder.lyrics.library.SongRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.Date

/*
 * The Library (Lyrics 0.4, scope section 6): Songs, Stats, and each song's page. Everything here is read from the
 * encrypted library.db on this device. Stats describe; they never push.
 */

/** The Library, opened lazily off the main thread (the key unwrap and SQLCipher open). Null while it opens. */
@Composable
private fun rememberLibrary(): LibraryRepo? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var attempt by remember { mutableIntStateOf(0) }
    val opened by produceState<Result<LibraryRepo>?>(null, attempt) {
        value = withContext(Dispatchers.IO) { runCatching { LibraryRepo(context) } }
    }
    var starting by remember { mutableStateOf(false) }
    val r = opened ?: return null
    val e = r.exceptionOrNull() ?: return r.getOrNull()
    if (starting) return null
    if (e is LibraryKey.Gone) KeyGoneNotice {
        starting = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { LibraryDatabase.startFresh(context) } }
            attempt++; starting = false
        }
    } else Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Anything else may pass (a busy key store): say so and offer another try, never crash.
        Text("The Library couldn't be opened just now.", fontFamily = Inter, fontSize = 16.sp, color = L.soft)
        Washed("Try again", { attempt++ })
    }
    return null
}

/** The Library's key is gone (LibraryKey.Gone): say so plainly and offer a new, empty Library. */
@Composable
private fun KeyGoneNotice(onStartFresh: () -> Unit) {
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("This device's Library key is gone", fontFamily = Fraunces, fontSize = 22.sp, color = L.text)
        Text(
            "The key that unlocks your Library on this device no longer exists, so the Library can't be opened. This can " +
                "happen after some device restores or resets. Start a new, empty Library? The old one is set aside, not " +
                "deleted. You can then restore a Library backup from Settings.",
            fontFamily = Inter, fontSize = 16.sp, color = L.soft,
        )
        Washed("Start a new Library", onStartFresh)
    }
}

private enum class LibTab(val label: String) { SONGS("Songs"), STATS("Stats") }
private enum class Sort(val label: String) { RECENT("Last played"), MOST("Most played"), AZ("A to Z") }

@Composable
fun LibraryPanel(onSong: (String) -> Unit, onExport: () -> Unit, onSettings: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val exists = remember { LibraryDatabase.exists(context) }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Library", fontFamily = Fraunces, fontSize = 28.sp, color = L.text, modifier = Modifier.weight(1f))
            if (exists) { Washed("Export", onExport, small = true); Box(Modifier.width(10.dp)) }
            Washed("Close", onClose, small = true)
        }
        if (!exists) {
            EmptyState(
                "Nothing here yet",
                if (settings.historyMode == History.Mode.OFF)
                    "Turn on listening history in Settings and the songs you play show up here, with your notes and moments."
                else "Play a song for a minute or two and it shows up here.",
                if (settings.historyMode == History.Mode.OFF) "Open Settings" else null, onSettings, raven = false,
            )
            return@Column
        }
        val repo = rememberLibrary() ?: return@Column
        var tab by remember { mutableStateOf(LibTab.SONGS) }
        Row(Modifier.padding(top = 12.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LibTab.entries.forEach { t -> Choice(t.label, tab == t) { tab = t } }
        }
        if (settings.historyMode == History.Mode.OFF) {
            Text("Listening history is off, so new plays aren't counted.", fontFamily = Inter, fontSize = 14.sp, color = L.soft,
                modifier = Modifier.padding(top = 8.dp))
        } else if (settings.historyPaused()) {
            Text("Listening history is paused.", fontFamily = Inter, fontSize = 14.sp, color = L.soft, modifier = Modifier.padding(top = 8.dp))
        }
        when (tab) {
            LibTab.SONGS -> SongsList(repo, onSong)
            LibTab.STATS -> StatsPage(repo, onSong)
        }
    }
}

@Composable
private fun SongsList(repo: LibraryRepo, onSong: (String) -> Unit) {
    val context = LocalContext.current
    val all by repo.songs.collectAsState(null)
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var sort by remember { mutableStateOf(Sort.RECENT) }
    var favorites by remember { mutableStateOf(false) }
    var withNote by remember { mutableStateOf(false) }

    val rows = remember(all, query.text, sort, favorites, withNote) {
        val q = query.text.trim().lowercase()
        all.orEmpty()
            .filter { !favorites || it.favorite }
            .filter { !withNote || it.note.isNotBlank() }
            .filter { q.isEmpty() || it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.orEmpty().lowercase().contains(q) }
            .let { list ->
                when (sort) {
                    Sort.RECENT -> list.sortedByDescending { it.lastHeard ?: it.firstHeard ?: 0 }
                    Sort.MOST -> list.sortedWith(compareByDescending<SongRow> { it.plays }.thenBy { it.title.lowercase() })
                    Sort.AZ -> list.sortedWith(compareBy<SongRow> { it.title.lowercase() }.thenBy { it.artist.lowercase() })
                }
            }
    }

    Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
        SearchField(query, { query = it }, {}, hint = "Search songs, artists, albums")
        FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Sort.entries.forEach { s -> Choice(s.label, sort == s) { sort = s } }
            Choice("Favorites", favorites) { favorites = !favorites }
            Choice("Has a note", withNote) { withNote = !withNote }
        }
        when {
            all == null -> {}
            all!!.isEmpty() -> Text("No songs yet.", fontFamily = Inter, fontSize = 16.sp, color = L.soft, modifier = Modifier.padding(top = 16.dp))
            rows.isEmpty() -> Text("Nothing matches.", fontFamily = Inter, fontSize = 16.sp, color = L.soft, modifier = Modifier.padding(top = 16.dp))
        }
        // Swiping the list scrolls it; only a tap opens a song (the rule for every Scarlet app).
        LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(rows, key = { it.uid }) { r ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSong(r.uid) }.padding(vertical = 12.dp, horizontal = 4.dp)) {
                    Text(r.title, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 17.sp,
                        color = L.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            r.artist, if (r.favorite) "favorite" else null, plays(r.plays),
                            r.lastHeard?.let { "last " + day(context, it) },
                            if (r.note.isNotBlank()) "note" else null,
                        ).joinToString("  ·  "),
                        fontFamily = Inter, fontSize = 14.sp, color = L.soft, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsPage(repo: LibraryRepo, onSong: (String) -> Unit) {
    var period by remember { mutableStateOf(LibraryStats.Period.WEEK) }
    val songs by repo.songs.collectAsState(null)   // recompute when plays or songs change
    val result by produceState<LibraryStats.Result?>(null, period, songs) {
        value = withContext(Dispatchers.IO) { repo.stats(period, ZoneId.systemDefault()) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryStats.Period.entries.forEach { p -> Choice(p.label, period == p) { period = p } }
        }
        val r = result ?: return@Column
        if (r.plays == 0) {
            Text(if (period == LibraryStats.Period.ALL) "No plays yet." else "No plays ${period.label.lowercase()}.", fontFamily = Inter, fontSize = 16.sp, color = L.soft, modifier = Modifier.padding(top = 18.dp))
            return@Column
        }
        Text(LibraryStats.duration(r.listenedMs), fontFamily = Fraunces, fontSize = 30.sp, color = L.text, modifier = Modifier.padding(top = 18.dp))
        Text(
            listOfNotNull(plays(r.plays), r.favoriteHour?.let { "most often around " + LibraryStats.hourLabel(it) }).joinToString("  ·  "),
            fontFamily = Inter, fontSize = 15.sp, color = L.soft,
        )
        RankedList("Top songs", r.topSongs, onSong)
        RankedList("Top artists", r.topArtists, null)
        if (r.topAlbums.isNotEmpty()) RankedList("Top albums", r.topAlbums, null)
        if (r.newCount > 0 && period != LibraryStats.Period.ALL) {
            RankedList("New to you (${r.newCount})", r.newToYou, onSong)
        }
        Box(Modifier.heightIn(min = 24.dp))
    }
}

@Composable
private fun RankedList(title: String, items: List<LibraryStats.Ranked>, onSong: ((String) -> Unit)?) {
    SettingsSection(title)
    items.forEachIndexed { i, it ->
        val open = it.uid?.let { uid -> onSong?.let { f -> { f(uid) } } }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).let { m -> if (open != null) m.clickable(onClick = open) else m }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${i + 1}", fontFamily = Inter, fontSize = 15.sp, color = L.faint, modifier = Modifier.width(28.dp))
            Column(Modifier.weight(1f)) {
                Text(it.label, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = L.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                it.sub?.let { s -> Text(s, fontFamily = Inter, fontSize = 14.sp, color = L.soft, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Text(plays(it.plays), fontFamily = Inter, fontSize = 14.sp, color = L.soft)
        }
    }
}

/**
 * One song's page: plays, first and last heard, favorite, note (earlier notes kept), moments, and "Forget this song".
 * [position] is set when this song is the one playing: moments can be added at the current time, and tapping one seeks.
 */
@Composable
fun SongPage(songUid: String?, pending: Track?, position: (() -> Long)?, onSeek: ((Long) -> Unit)?, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = rememberLibrary() ?: return
    // A playing song that isn't in the Library yet ([pending]) gets its row only when a note, favorite or moment is saved.
    var uid by remember(songUid, pending) { mutableStateOf(songUid) }
    suspend fun ensureUid(): String? = uid ?: pending?.let { t -> repo.songFor(t, create = true)?.uid }?.also { uid = it }
    val row by remember(uid) { uid?.let { repo.songRow(it) } ?: flowOf(null) }.collectAsState(null)
    val moments by remember(uid) { uid?.let { repo.moments(it) } ?: flowOf(emptyList()) }.collectAsState(emptyList())
    val earlier by remember(uid) { uid?.let { repo.noteHistory(it) } ?: flowOf(emptyList()) }.collectAsState(emptyList())
    var note by remember(songUid, pending) { mutableStateOf<TextFieldValue?>(null) }   // a draft survives the row being made
    var showEarlier by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf(TextFieldValue("")) }
    var forgetting by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var forgetFailed by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(row) {
        val r = row
        if (r != null && note == null) note = TextFieldValue(r.note)
        if (r != null) loaded = true
        if (r == null && loaded) onClose()   // forgotten (here, or on the other device after a sync)
    }
    // Until its row arrives (also the moment after a first save), a pending song shows what's playing.
    val r = row ?: pending?.let { SongRow("", it.title, it.artist, it.album, false, "", 0, null, null, null) } ?: return

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.title, fontFamily = Fraunces, fontSize = 28.sp, color = L.text, modifier = Modifier.weight(1f))
            Washed("Back", onClose, small = true)
        }
        Text(listOfNotNull(r.artist, r.album?.ifBlank { null }).joinToString("  ·  "), fontFamily = Inter, fontSize = 16.sp, color = L.soft)
        Text(
            listOfNotNull(
                plays(r.plays),
                r.firstHeard?.let { "first heard " + day(context, it) },
                r.lastHeard?.let { "last " + day(context, it) },
                r.listenedMs?.takeIf { it > 0 }?.let { LibraryStats.duration(it) + " listened" },
            ).joinToString("  ·  "),
            fontFamily = Inter, fontSize = 15.sp, color = L.soft, modifier = Modifier.padding(top = 6.dp),
        )
        Box(Modifier.padding(top = 12.dp)) {
            Choice(if (r.favorite) "Favorite" else "Mark as favorite", r.favorite) { scope.launch { ensureUid()?.let { repo.setFavorite(it, !r.favorite) } } }
        }

        SettingsSection("Note")
        val n = note ?: TextFieldValue(r.note)
        NoteField(n) { note = it }
        if (n.text != r.note) {
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Washed("Save note", { scope.launch { ensureUid()?.let { repo.setNote(it, n.text.trim()); note = TextFieldValue(n.text.trim()) } } })
                Washed("Undo changes", { note = TextFieldValue(r.note) })
            }
        }
        if (earlier.isNotEmpty()) {
            Box(Modifier.padding(top = 10.dp)) {
                Washed(if (showEarlier) "Hide earlier notes" else "Earlier notes (${earlier.size})", { showEarlier = !showEarlier }, small = true)
            }
            if (showEarlier) earlier.forEach { h ->
                Column(Modifier.padding(top = 10.dp)) {
                    Text("Until " + day(context, h.replacedAt), fontFamily = Inter, fontSize = 13.sp, color = L.faint)
                    Text(h.text, fontFamily = Inter, fontSize = 15.sp, color = L.soft)
                }
            }
        }

        SettingsSection("Moments")
        if (moments.isEmpty()) {
            Text(
                if (position != null) "Long-press a line in the lyrics, or add one at the current time below."
                else "While this song plays, long-press a line in the lyrics to bookmark it.",
                fontFamily = Inter, fontSize = 15.sp, color = L.soft,
            )
        }
        moments.forEach { m ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).let { if (onSeek != null) it.clickable { onSeek(m.atMs) } else it }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                ) {
                    Text(clock(m.atMs), fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = L.text, modifier = Modifier.width(56.dp))
                    Text(m.label.ifBlank { " " }, fontFamily = Inter, fontSize = 16.sp, color = L.soft)
                }
                Washed("Remove", { scope.launch { repo.removeMoment(m.uid) } }, small = true)
            }
        }
        if (position != null) {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NoteField(label, singleLine = true, hint = "Label (optional)") { label = it }
                Washed("Add a moment here", {
                    if (adding) return@Washed
                    val at = position()
                    val text = label.text.trim().take(80)
                    adding = true
                    scope.launch {
                        try { ensureUid()?.let { repo.addMoment(it, at, text); label = TextFieldValue("") } } finally { adding = false }
                    }
                })
            }
        }

        val saved = uid
        if (saved != null) {   // nothing to forget until the song is in the Library
            SettingsSection("Forget this song")
            if (forgetting) {
                Text("Remove its plays, note, moments and saved lyrics? This can't be undone.", fontFamily = Inter, fontSize = 16.sp, color = L.text)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Washed("Forget", { scope.launch { if (runCatching { repo.forget(saved) }.isFailure) forgetFailed = true } })
                    Washed("Cancel", { forgetting = false })
                }
                if (forgetFailed) Text("That didn't finish. Nothing was forgotten.", fontFamily = Inter, fontSize = 15.sp, color = L.soft,
                    modifier = Modifier.padding(top = 8.dp))
            } else Washed("Forget this song", { forgetting = true })
        }
        Box(Modifier.heightIn(min = 32.dp))
    }
}

@Composable
private fun NoteField(value: TextFieldValue, singleLine: Boolean = false, hint: String? = null, onChange: (TextFieldValue) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = singleLine,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        textStyle = TextStyle(color = L.text, fontSize = 16.sp, fontFamily = Inter), cursorBrush = SolidColor(L.text),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().heightIn(min = if (singleLine) 0.dp else 96.dp).background(L.wash, shape).border(1.dp, L.washEdge, shape).padding(14.dp)) {
                if (value.text.isEmpty()) Text(hint ?: "What this song means to you, when you heard it first, anything.", fontFamily = Inter, fontSize = 16.sp, color = L.faint)
                inner()
            }
        },
    )
}

/**
 * Long-press a lyric line: bookmark that time with an optional label. Stores the time and the label, never the line.
 * [at] null = closed.
 */
@Composable
fun MomentSheet(at: Long?, onClose: () -> Unit, onSave: suspend (Long, String) -> Boolean) {
    val scope = rememberCoroutineScope()
    var label by remember(at) { mutableStateOf(TextFieldValue("")) }
    var saved by remember(at) { mutableStateOf(false) }
    var failed by remember(at) { mutableStateOf(false) }
    var saving by remember(at) { mutableStateOf(false) }
    LaunchedEffect(saved) { if (saved) { delay(1_200); onClose() } }
    if (at == null) return
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f))
        .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose))
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(L.surface)
                .clickable(remember { MutableInteractionSource() }, indication = null) { }
                .navigationBarsPadding().imePadding().padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Bookmark ${clock(at)}", fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = L.text)
            if (saved) {
                Text("Saved. It's in this song's Note and moments.", fontFamily = Inter, fontSize = 15.sp, color = L.soft)
            } else {
                Text(if (failed) "Couldn't save that moment. Try again?" else "Saves the time and your label, not the words.", fontFamily = Inter, fontSize = 14.sp, color = L.soft)
                NoteField(label, singleLine = true, hint = "Label (optional)") { label = it }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Washed("Save", {
                        if (saving) return@Washed
                        saving = true
                        scope.launch {
                            try { if (onSave(at, label.text.trim().take(80))) saved = true else failed = true } finally { saving = false }
                        }
                    })
                    Washed("Cancel", onClose)
                }
            }
        }
    }
}

private fun plays(n: Int) = if (n == 1) "1 play" else "$n plays"
private fun day(context: android.content.Context, ms: Long): String = DateFormat.getMediumDateFormat(context).format(Date(ms))
private fun clock(ms: Long) = "%d:%02d".format(ms / 60_000, (ms / 1000) % 60)
