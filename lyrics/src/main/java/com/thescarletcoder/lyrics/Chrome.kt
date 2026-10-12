package com.thescarletcoder.lyrics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thescarletcoder.lyrics.library.History
import com.thescarletcoder.scarlet.Motion
import com.thescarletcoder.scarlet.Tunables

/**
 * Header: album thumbnail (only with art), title in Fraunces, artist, a drawn play/pause and a drawn "more".
 * The six text chips are gone; everything else lives in the More sheet.
 * With [onBack] (a page is open) it's the now-playing bar: a back arrow before the song, and tapping either goes back
 * a step, to the lyrics from any first-level page. With nothing playing it names where that is: "Back to [backTo]".
 */
@Composable
fun Header(
    track: Track?, art: AlbumLook?, playing: Boolean, onPlayPause: () -> Unit, onMore: () -> Unit, onBack: (() -> Unit)? = null,
    backTo: String = "Lyrics", onSong: (() -> Unit)? = null, unsure: Boolean = false,
) {
    val compact = LocalCompact.current
    val swap = Motion.duration(LocalContext.current, Tunables.int(LyricsTuning.SWAP_MS))
    // A narrow window (a split under about 340dp) drops the album thumbnail, so the title keeps its room beside
    // This song, play/pause and More.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val narrow = maxWidth < 340.dp
    Row(
        Modifier.fillMaxWidth().padding(horizontal = if (compact) 16.dp else 24.dp, vertical = if (compact) 8.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // With a page open, the arrow and the song are one button: back a step.
        val back = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Back", role = Role.Button) { onBack?.invoke() }
        Row(if (onBack != null) Modifier.weight(1f).heightIn(min = 48.dp).then(back) else Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                // Drawn like the other header buttons; the whole bar is the tap target, so this one only shows the way.
                Box(Modifier.size(40.dp).clip(CircleShape).background(L.wash).border(1.dp, L.washEdge, CircleShape), contentAlignment = Alignment.Center) {
                    val c = L.text
                    Canvas(Modifier.size(18.dp)) { Icons.back(this, c) }
                }
                Spacer(Modifier.width(12.dp))
            }
            // Old title fades out, then the new one fades in.
            AnimatedContent(
                targetState = track to art, contentKey = { it.first?.key to (it.second != null) },
                transitionSpec = { fadeIn(tween(swap, delayMillis = swap)) togetherWith fadeOut(tween(swap)) },
                modifier = Modifier.weight(1f), label = "title",
            ) { (t, a) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (a != null && !narrow) {
                        Image(a.thumb, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(t?.title ?: if (onBack != null) "Back to $backTo" else "Scarlet Lyrics", fontFamily = Fraunces,
                            fontSize = if (compact) 17.sp else 20.sp, color = L.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (t != null) Text(t.artist, fontFamily = Inter, fontSize = 13.sp, color = L.soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        if (onSong != null) {
            SongButton(40.dp, unsure, onSong)
            Spacer(Modifier.width(8.dp))
        }
        if (track != null) {
            IconButton(40.dp, if (playing) "Pause" else "Play", onPlayPause, draw = if (playing) Icons.pause else Icons.play)
            Spacer(Modifier.width(8.dp))
        }
        IconButton(40.dp, "More", onMore, draw = Icons.more)
    }
    }
}

/** This song (versions and timing), always there while a song's lyrics show (0.5.0, owner Q20); a dot when unsure (Q24). */
@Composable
fun SongButton(size: androidx.compose.ui.unit.Dp, unsure: Boolean, onClick: () -> Unit) =
    IconButton(size, if (unsure) "This song: versions and timing. The timing may be off." else "This song: versions and timing", onClick, dot = unsure, draw = Icons.tune)

/**
 * Flex mode (a flip phone half-folded on a table): below the fold, the song with its cover, and big play/pause and more
 * buttons that are easy to reach on the bottom half. Lyrics fill the top half.
 */
@Composable
fun FlexControls(track: Track?, art: AlbumLook?, playing: Boolean, onPlayPause: () -> Unit, onSong: (() -> Unit)? = null, unsure: Boolean = false, onMore: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (art != null) {
                Image(art.thumb, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)))
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f, fill = false)) {
                Text(track?.title ?: "Scarlet Lyrics", fontFamily = Fraunces, fontSize = 20.sp, color = L.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (track != null) Text(track.artist, fontFamily = Inter, fontSize = 14.sp, color = L.soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.size(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onSong != null) SongButton(52.dp, unsure, onSong)
            if (track != null) IconButton(64.dp, if (playing) "Pause" else "Play", onPlayPause, draw = if (playing) Icons.pause else Icons.play)
            IconButton(52.dp, "More", onMore, draw = Icons.more)
        }
    }
}

/** Timing in words: "Lyrics earlier by 0.25s"; with tapped lines (owner, 0.5.0) "Your timing: 3 lines set". */
fun timingLabel(offsetMs: Long, anchors: Int = 0): String {
    val lines = if (anchors == 1) "1 line set" else "$anchors lines set"
    return when {
        anchors > 0 && offsetMs == 0L -> "Your timing: $lines"
        offsetMs > 0 -> "Lyrics earlier by %.2fs".format(offsetMs / 1000.0) + if (anchors > 0) ", $lines" else ""
        offsetMs < 0 -> "Lyrics later by %.2fs".format(-offsetMs / 1000.0) + if (anchors > 0) ", $lines" else ""
        else -> "Lyrics match the song's timing"
    }
}

/** A sheet that slides up with the NAV voice over a dimmed screen; tapping outside it closes it. */
@Composable
private fun BottomSheet(open: Boolean, onClose: () -> Unit, content: @Composable () -> Unit) {
    val ms = Motion.duration(LocalContext.current, Motion.NAV_MS)
    AnimatedVisibility(open, enter = fadeIn(tween(ms, easing = Motion.NAV_EASE)), exit = fadeOut(tween(ms, easing = Motion.NAV_EASE))) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose))
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            open,
            enter = slideInVertically(tween(ms, easing = Motion.NAV_EASE)) { it } + fadeIn(tween(ms)),
            exit = slideOutVertically(tween(ms, easing = Motion.NAV_EASE)) { it } + fadeOut(tween(ms)),
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(L.surface)
                    .clickable(remember { MutableInteractionSource() }, indication = null) { }
                    // Scrolls when the window is short (split screen on a flip phone): every row stays reachable.
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding().padding(horizontal = 24.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) { content() }
        }
    }
}

/**
 * This song (0.5.0, owner Q20): other versions and the user's timing for the song playing. A note when Lyrics isn't
 * sure of the timing (Q24). Line up now, Fix lines and Share come in later slices.
 */
@Composable
fun SongSheet(
    open: Boolean, shown: Shown?, onClose: () -> Unit, onWrong: () -> Unit, onNudge: (Long) -> Unit, onClear: () -> Unit,
    onFix: (FixMode) -> Unit,
) {
    // Reset with the song too, so a confirm left open can never clear the next song's timing.
    var confirmClear by remember(open, (shown as? Shown.Found)?.candidate?.id) { mutableStateOf(false) }
    BottomSheet(open, onClose) {
        Text("This song", fontFamily = Fraunces, fontSize = 22.sp, color = L.text, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
        val found = shown as? Shown.Found
        if (found?.unsure == true) Text(
            "The timing may be off: the versions on LRCLIB don't agree, or don't fit this song. Try another version or fix the timing.",
            fontFamily = Inter, fontSize = 14.sp, color = L.soft, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        SheetRow("Other versions", "Find a synced or different version, or search by hand.") { onClose(); onWrong() }
        if (found != null && found.synced) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp)) {
                Text("Timing", fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = L.text)
                Text(timingLabel(found.offsetMs, found.anchors.size), fontFamily = Inter, fontSize = 14.sp, color = L.soft, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Washed("Earlier", { onNudge(250) })
                    Washed("Later", { onNudge(-250) })
                }
            }
            SheetRow("Line up now", "Tap Now the moment you hear the line shown. The whole song moves.") { onClose(); onFix(FixMode.LINE_UP) }
            SheetRow("Fix lines", "As the song plays, tap Now as each line starts. Lines you tap stay put; the rest follow.") { onClose(); onFix(FixMode.FIX) }
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                if (found.offsetMs != 0L || found.anchors.isNotEmpty()) {
                    if (confirmClear) Column(Modifier.padding(top = 12.dp)) {
                        Text("Clear your timing for this song? The version stays.", fontFamily = Inter, fontSize = 16.sp, color = L.text)
                        FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Washed("Clear", { confirmClear = false; onClear() })
                            Washed("Cancel", { confirmClear = false })
                        }
                    } else Box(Modifier.padding(top = 12.dp)) { Washed("Clear my timing", { confirmClear = true }) }
                }
            }
        }
    }
}

/** Which way of fixing the timing the bar is in (0.5.0, owner Q19, Q22). */
enum class FixMode { NONE, LINE_UP, FIX }

/**
 * The bar at the bottom of the lyrics while fixing the timing: a line to listen for, and Now. The line stays put until
 * it's tapped (it never moves on by the lyrics' own clock, which is the one being fixed). Line up now takes one tap and
 * closes; Fix lines moves on to the next line with each tap, until Done.
 */
@Composable
fun FixBar(mode: FixMode, s: Shown.Found, np: NowPlaying, playing: Boolean, outputDelayMs: Long, onNow: (lineMs: Long, offsetMs: Long) -> Unit, onDone: () -> Unit) {
    var pos by remember { mutableLongStateOf(np.positionMs()) }
    LaunchedEffect(Unit) { while (true) { pos = np.positionMs(); kotlinx.coroutines.delay(100) } }
    val timed = remember(s) { YourTiming.apply(s.lines, s.offsetMs, s.anchors) }
    var target by remember { mutableStateOf<Int?>(null) }
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(playing) {
        if (playing && !started) { target = YourTiming.firstTarget(timed, np.positionMs() + outputDelayMs); started = true }
    }
    // A missed line is replaced once, by a new line that can't have been sung yet; the bar never drifts with the clock.
    val missed = target?.let { YourTiming.missed(timed, it, pos + outputDelayMs) } == true
    LaunchedEffect(missed) { if (missed) target = YourTiming.firstTarget(timed, np.positionMs() + outputDelayMs) }
    val next = target.takeIf { !missed }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(L.surface)
            .clickable(remember { MutableInteractionSource() }, indication = null) { }
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        when {
            !playing || !started -> Text("Play the song to fix its timing.", fontFamily = Inter, fontSize = 15.sp, color = L.soft)
            next == null -> Text("No more lines to fix.", fontFamily = Inter, fontSize = 15.sp, color = L.soft)
            else -> {
                Text("Tap Now when you hear:", fontFamily = Inter, fontSize = 14.sp, color = L.soft)
                Text(s.lines[next].text, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 20.sp, color = L.text,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
        FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (playing && started && next != null) Washed("Now", {
                // The line's time in the record, not on screen: the move is worked out against the record's own timing.
                val lineMs = s.lines[next].timeMs ?: return@Washed
                onNow(lineMs, YourTiming.offsetFromTap(lineMs, np.positionMs(), outputDelayMs, Tunables.int(LyricsTuning.REACTION_MS).toLong()))
                target = YourTiming.afterTap(timed, next)
            })
            Washed(if (mode == FixMode.LINE_UP && next != null) "Cancel" else "Done", onDone)
        }
    }
}

/** The More sheet: the song's note and moments, the Library, history, your music app, Settings. */
@Composable
fun MoreSheet(
    open: Boolean, shown: Shown?, hasTrack: Boolean, playerLabel: String?,
    onClose: () -> Unit, onPlayer: () -> Unit, onSettings: () -> Unit, onSongPage: () -> Unit, onLibrary: () -> Unit,
) {
    val settings = LocalSettings.current
    BottomSheet(open, onClose) {
                if (hasTrack && shown !is Shown.NoAccess) SheetRow("Note and moments", "Your note on this song, and bookmarked times.") { onClose(); onSongPage() }
                SheetRow("Library", null) { onClose(); onLibrary() }
                // Pausing is on the lyrics screen, not buried in Settings (scope section 6).
                if (settings.historyMode != History.Mode.OFF) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        Text("Listening history", fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = L.text)
                        if (settings.historyPaused()) {
                            Text("Paused.", fontFamily = Inter, fontSize = 14.sp, color = L.soft, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                            Washed("Resume", { settings.pauseHistory(0) })
                        } else {
                            Text("On. Pause it for a while:", fontFamily = Inter, fontSize = 14.sp, color = L.soft, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Washed("1 hour", { settings.pauseHistory(System.currentTimeMillis() + 3_600_000L) })
                                Washed("Until I turn it back on", { settings.pauseHistory(History.PAUSED_UNTIL_ON) })
                            }
                        }
                    }
                }
                if (playerLabel != null) SheetRow("Open $playerLabel", null) { onClose(); onPlayer() }
                SheetRow("Settings", null) { onClose(); onSettings() }
    }
}

@Composable
private fun SheetRow(title: String, note: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp)) {
        Text(title, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = L.text)
        note?.let { Text(it, fontFamily = Inter, fontSize = 14.sp, color = L.soft) }
    }
}
