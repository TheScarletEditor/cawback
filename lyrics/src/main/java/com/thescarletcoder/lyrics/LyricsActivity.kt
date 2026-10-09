package com.thescarletcoder.lyrics

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Scarlet Coder dark palette, same values as Scarlet Command's tokens. */
private object T {
    val bg = Color(0xFF171614)
    val surface = Color(0xFF211F1C)
    val raised = Color(0xFF2A2825)
    val line = Color(0x1FFAF9F6)
    val text = Color(0xFFFAF9F6)
    val soft = Color(0xFF908E86)
    val faint = Color(0xFF65635E)
    val scarlet = Color(0xFFE20514)
    val scarletWash = scarlet.copy(alpha = 0.14f)
}

private val Fraunces = FontFamily(Font(R.font.fraunces_display))
private val Inter = FontFamily(Font(R.font.inter_regular), Font(R.font.inter_medium, FontWeight.Medium))

class LyricsActivity : ComponentActivity() {
    private lateinit var nowPlaying: NowPlaying
    private val access = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nowPlaying = NowPlaying(this)
        val repo = LyricsRepo(LyricsStore(this))
        if (savedInstanceState == null) maybeOpenSpotify(intent)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = T.scarlet, background = T.bg, surface = T.surface, onSurface = T.text)) {
                Box(Modifier.fillMaxSize().background(T.bg).safeDrawingPadding()) {
                    LyricsScreen(nowPlaying, repo, access.value, onGrant = ::openAccessSettings, onSpotify = ::openSpotifyAlongside)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        maybeOpenSpotify(intent)
    }

    override fun onStart() {
        super.onStart()
        access.value = nowPlaying.hasAccess()
        nowPlaying.start()
    }

    override fun onStop() {
        nowPlaying.stop()
        super.onStop()
    }

    /** Scarlet Command's Listen button sends this extra: open Spotify in the other half of the screen. */
    private fun maybeOpenSpotify(i: Intent?) {
        if (i?.getBooleanExtra(EXTRA_OPEN_SPOTIFY, false) == true) {
            i.removeExtra(EXTRA_OPEN_SPOTIFY)
            openSpotifyAlongside()
        }
    }

    private fun openSpotifyAlongside() {
        val launch = packageManager.getLaunchIntentForPackage(NowPlaying.SPOTIFY) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        runCatching { startActivity(launch) }
    }

    private fun openAccessSettings() {
        val cn = ComponentName(this, NowPlayingListener::class.java)
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, cn.flattenToString())
        runCatching { startActivity(detail) }.onFailure { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }

    companion object {
        const val EXTRA_OPEN_SPOTIFY = "com.thescarletcoder.lyrics.OPEN_SPOTIFY"
    }
}

@Composable
private fun LyricsScreen(np: NowPlaying, repo: LyricsRepo, hasAccess: Boolean, onGrant: () -> Unit, onSpotify: () -> Unit) {
    val track by np.track.collectAsState()
    val playing by np.playing.collectAsState()
    val scope = rememberCoroutineScope()
    var shown by remember { mutableStateOf<Shown>(Shown.NothingPlaying) }
    var picking by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(hasAccess, track, reload) {
        picking = false
        val t = track
        shown = when {
            !hasAccess -> Shown.NoAccess
            t == null -> Shown.NothingPlaying
            else -> { shown = Shown.Loading; repo.load(t) }
        }
    }

    // Short window (the Flip's split screen gives Lyrics the top half): compact header and smaller lyrics.
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val compact = maxHeight < 560.dp
    val inSplit = (LocalContext.current as? android.app.Activity)?.isInMultiWindowMode == true
    androidx.compose.runtime.CompositionLocalProvider(LocalCompact provides compact) {
    Column(Modifier.fillMaxSize()) {
        Header(track, shown, playing, showSpotify = !inSplit, onWrong = { picking = !picking }, onSpotify = onSpotify, onPlayPause = np::playPause,
            onNudge = { d -> val t = track; val s = shown
                if (t != null && s is Shown.Found) scope.launch { shown = s.copy(offsetMs = repo.nudge(t, d)) } })
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val t = track
            if (picking && t != null) {
                Picker(t, shown as? Shown.Found, repo,
                    onPick = { c -> scope.launch { shown = repo.pin(t, c); picking = false } },
                    onAuto = { scope.launch { shown = Shown.Loading; shown = repo.unpin(t); picking = false } },
                    onClose = { picking = false })
            } else when (val s = shown) {
                Shown.NoAccess -> Message(
                    "Scarlet Lyrics needs to see what's playing.",
                    "Turn on notification access for Scarlet Lyrics. It only reads the song title, artist and position. It never reads your notifications.",
                    "Open the setting", onGrant,
                )
                Shown.NothingPlaying -> Message("Nothing playing", "Start a song in Spotify and the lyrics show up here.", "Open Spotify", onSpotify)
                Shown.Loading -> Message("Finding lyrics...", null, null, null)
                Shown.NotFound -> Message("No lyrics found", "LRCLIB doesn't have this one yet.", "Search by hand") { picking = true }
                Shown.Offline -> Message(
                    "Can't reach LRCLIB",
                    "Check that Rethink lets Scarlet Lyrics through, then try again.",
                    "Try again",
                ) { reload++ }
                is Shown.Found -> when {
                    s.lines.isEmpty() -> Message(if (s.candidate.instrumental) "Instrumental" else "No words for this one", null, null, null)
                    s.synced -> SyncedLyrics(s, np)
                    else -> PlainLyrics(s)
                }
            }
        }
    }
    }
    }
}

/** True when Lyrics has a short window (split screen): everything gets smaller so the words have room. */
private val LocalCompact = androidx.compose.runtime.staticCompositionLocalOf { false }

@Composable
private fun Header(
    track: Track?, shown: Shown, playing: Boolean, showSpotify: Boolean,
    onWrong: () -> Unit, onSpotify: () -> Unit, onPlayPause: () -> Unit, onNudge: (Long) -> Unit,
) {
    if (LocalCompact.current) { CompactHeader(track, shown, playing, showSpotify, onWrong, onSpotify, onPlayPause, onNudge); return }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(track?.title ?: "Scarlet Lyrics", fontFamily = Fraunces, fontSize = 30.sp, color = T.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (track != null) Text(track.artist, fontFamily = Inter, fontSize = 17.sp, color = T.soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.padding(top = 10.dp))
        // Wraps onto a second line on narrow screens (the phone) instead of squeezing the last button.
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (track != null) Chip(if (playing) "Pause" else "Play", onPlayPause)
            if (track != null && shown !is Shown.NoAccess) Chip("Wrong lyrics?", onWrong)
            if (shown is Shown.Found && shown.synced) {
                Chip("Earlier", { onNudge(250) })
                Chip("Later", { onNudge(-250) })
                if (shown.offsetMs != 0L) Text("%+.2fs".format(shown.offsetMs / 1000.0), fontFamily = Inter, fontSize = 14.sp, color = T.faint)
            }
            if (showSpotify) Chip("Spotify", onSpotify)
        }
    }
}

/** Split screen: title and artist on one line, the buttons in one small row you swipe sideways. */
@Composable
private fun CompactHeader(
    track: Track?, shown: Shown, playing: Boolean, showSpotify: Boolean,
    onWrong: () -> Unit, onSpotify: () -> Unit, onPlayPause: () -> Unit, onNudge: (Long) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            // The title comes first and takes the room it needs; the artist gets what's left (Sam, 2026-10-08).
            Text(track?.title ?: "Scarlet Lyrics", fontFamily = Fraunces, fontSize = 20.sp, color = T.text, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            if (track != null) Text("  " + track.artist, fontFamily = Inter, fontSize = 13.sp, color = T.soft, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
        Row(
            Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (track != null) SmallChip(if (playing) "Pause" else "Play", onPlayPause)
            if (track != null && shown !is Shown.NoAccess) SmallChip("Wrong lyrics?", onWrong)
            if (shown is Shown.Found && shown.synced) {
                SmallChip("Earlier", { onNudge(250) })
                SmallChip("Later", { onNudge(-250) })
                if (shown.offsetMs != 0L) Text("%+.2fs".format(shown.offsetMs / 1000.0), fontFamily = Inter, fontSize = 12.sp, color = T.faint)
            }
            if (showSpotify) SmallChip("Spotify", onSpotify)
        }
    }
}

@Composable
private fun SmallChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 40.dp).background(T.raised, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = T.text) }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit, accent: Boolean = false) {
    Box(
        Modifier.heightIn(min = 48.dp)
            .background(if (accent) T.scarlet else T.raised, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = T.text) }
}

@Composable
private fun Message(title: String, body: String?, action: String?, onAction: (() -> Unit)?) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontFamily = Fraunces, fontSize = 28.sp, color = T.text)
        if (body != null) Text(body, fontFamily = Inter, fontSize = 17.sp, color = T.soft, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 10.dp).width(460.dp))
        if (action != null && onAction != null) Box(Modifier.padding(top = 20.dp)) { Chip(action, onAction, accent = true) }
    }
}

@Composable
private fun SyncedLyrics(s: Shown.Found, np: NowPlaying) {
    val list = rememberLazyListState()
    val dragged by list.interactionSource.collectIsDraggedAsState()
    var pos by remember { mutableLongStateOf(0L) }
    var lastTouch by remember { mutableLongStateOf(0L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(s) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { pos = np.positionMs() + s.offsetMs; delay(60) }
        }
    }
    LaunchedEffect(dragged) { if (dragged) lastTouch = System.currentTimeMillis() }
    val current = Lrc.indexAt(s.lines, pos)
    LaunchedEffect(current) {
        // Follow the song unless Sam scrolled in the last 4 seconds.
        if (!dragged && System.currentTimeMillis() - lastTouch > 4000) list.animateScrollToItem(current.coerceAtLeast(0))
    }

    val compact = LocalCompact.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val lead = maxHeight * if (compact) 0.25f else 0.36f
        LazyColumn(state = list, contentPadding = PaddingValues(top = lead, bottom = maxHeight - lead), modifier = Modifier.fillMaxSize()) {
            itemsIndexed(s.lines) { i, line ->
                val color by animateColorAsState(
                    when { i == current -> T.text; i < current -> T.faint; else -> T.soft }, label = "line",
                )
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { line.timeMs?.let { np.seekTo((it - s.offsetMs).coerceAtLeast(0)); lastTouch = 0 } }
                        .padding(horizontal = if (compact) 16.dp else 24.dp, vertical = if (compact) 4.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(4.dp).heightIn(min = 34.dp).background(if (i == current) T.scarlet else Color.Transparent, RoundedCornerShape(2.dp)))
                    Text(
                        line.text.ifBlank { "♪" },
                        fontFamily = Inter,
                        fontWeight = if (i == current) FontWeight.Medium else FontWeight.Normal,
                        fontSize = if (compact) (if (i == current) 22.sp else 18.sp) else if (i == current) 32.sp else 26.sp,
                        lineHeight = if (compact) (if (i == current) 28.sp else 24.sp) else if (i == current) 40.sp else 34.sp,
                        color = color,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlainLyrics(s: Shown.Found) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)) {
        item { Text("Not synced to the song. Scroll as you listen.", fontFamily = Inter, fontSize = 14.sp, color = T.faint, modifier = Modifier.padding(bottom = 14.dp)) }
        itemsIndexed(s.lines) { _, line ->
            val compact = LocalCompact.current
            Text(line.text, fontFamily = Inter, fontSize = if (compact) 18.sp else 24.sp, lineHeight = if (compact) 24.sp else 34.sp, color = T.text)
        }
    }
}

@Composable
private fun Picker(t: Track, current: Shown.Found?, repo: LyricsRepo, onPick: (Candidate) -> Unit, onAuto: () -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    // TextFieldValue so the cursor starts at the end of the pre-filled search (Sam couldn't reach the end on the phone).
    var query by remember {
        val start = "${Matching.cleanTitle(t.title)} ${t.artist}"
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(start, androidx.compose.ui.text.TextRange(start.length)))
    }
    var results by remember { mutableStateOf<List<Candidate>?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    fun run() {
        status = "Searching..."; results = null
        scope.launch {
            try {
                var r = repo.search(query.text.trim())
                // Nothing for title + artist? Try the title alone: some songs are filed under a label or composer.
                if (r.isEmpty()) r = repo.searchTitle(Matching.cleanTitle(t.title))
                results = r
                status = if (r.isEmpty()) "Nothing found. Try fewer words." else null
            }
            catch (_: Exception) { status = "Can't reach LRCLIB. Check Rethink." }
        }
    }
    LaunchedEffect(Unit) { run() }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        // The field gets the full width; the buttons go underneath (narrow phone screens).
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { run() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = T.text, unfocusedTextColor = T.text,
                    focusedBorderColor = T.soft, unfocusedBorderColor = T.line, cursorColor = T.text,
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("Search", ::run)
                Chip("Close", onClose)
            }
        }
        if (current?.pinned == true) Box(Modifier.padding(top = 10.dp)) { Chip("Go back to the automatic match", onAuto) }
        status?.let { Text(it, fontFamily = Inter, fontSize = 16.sp, color = T.soft, modifier = Modifier.padding(top = 14.dp)) }
        LazyColumn(Modifier.fillMaxSize().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(results.orEmpty()) { _, c ->
                val chosen = current?.candidate?.id == c.id
                val durDiff = t.durationMs?.let { Math.abs(c.durationSec - it / 1000.0) }
                Column(
                    Modifier.fillMaxWidth()
                        .background(if (chosen) T.scarletWash else T.surface, RoundedCornerShape(14.dp))
                        .clickable { onPick(c) }
                        .padding(16.dp),
                ) {
                    Text(c.track, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = T.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            c.artist, c.album.ifBlank { null },
                            "%d:%02d".format(c.durationSec.toInt() / 60, c.durationSec.toInt() % 60),
                            when { c.synced != null -> "Synced"; c.plain != null -> "Words only"; c.instrumental -> "Instrumental"; else -> null },
                            durDiff?.takeIf { it > 3 }?.let { "length differs by ${it.toInt()}s" },
                            if (chosen) "Showing now" else null,
                        ).joinToString("  ·  "),
                        fontFamily = Inter, fontSize = 15.sp, color = T.soft, maxLines = 2,
                    )
                }
            }
        }
    }
}
