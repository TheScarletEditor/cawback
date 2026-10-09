package com.thescarletcoder.lyrics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thescarletcoder.scarlet.Motion
import com.thescarletcoder.scarlet.Tunables

/**
 * Header: album thumbnail (only with art), title in Fraunces, artist, a drawn play/pause and a drawn "more".
 * The six text chips are gone; everything else lives in the More sheet.
 */
@Composable
fun Header(track: Track?, art: AlbumLook?, playing: Boolean, onPlayPause: () -> Unit, onMore: () -> Unit) {
    val compact = LocalCompact.current
    val swap = Motion.duration(LocalContext.current, Tunables.int(LyricsTuning.SWAP_MS))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = if (compact) 16.dp else 24.dp, vertical = if (compact) 8.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Old title fades out, then the new one fades in.
        AnimatedContent(
            targetState = track to art, contentKey = { it.first?.key to (it.second != null) },
            transitionSpec = { fadeIn(tween(swap, delayMillis = swap)) togetherWith fadeOut(tween(swap)) },
            modifier = Modifier.weight(1f), label = "title",
        ) { (t, a) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (a != null) {
                    Image(a.thumb, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(t?.title ?: "Scarlet Lyrics", fontFamily = Fraunces, fontSize = if (compact) 17.sp else 20.sp, color = L.text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (t != null) Text(t.artist, fontFamily = Inter, fontSize = 13.sp, color = L.soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        if (track != null) {
            IconButton(40.dp, onPlayPause, if (playing) Icons.pause else Icons.play)
            Spacer(Modifier.width(8.dp))
        }
        IconButton(40.dp, onMore, Icons.more)
    }
}

/** Timing nudge in words: "Lyrics earlier by 0.25s". */
fun timingLabel(offsetMs: Long): String = when {
    offsetMs > 0 -> "Lyrics earlier by %.2fs".format(offsetMs / 1000.0)
    offsetMs < 0 -> "Lyrics later by %.2fs".format(-offsetMs / 1000.0)
    else -> "Lyrics match the song's timing"
}

/** The More sheet (slides up with the NAV voice). Tapping outside it closes it. */
@Composable
fun MoreSheet(
    open: Boolean, shown: Shown?, hasTrack: Boolean, showSpotify: Boolean,
    onClose: () -> Unit, onWrong: () -> Unit, onNudge: (Long) -> Unit, onSpotify: () -> Unit, onSettings: () -> Unit,
) {
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
                    .navigationBarsPadding().padding(horizontal = 24.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (hasTrack && shown !is Shown.NoAccess) SheetRow("Wrong lyrics?", "Pick a different version or search by hand.") { onClose(); onWrong() }
                if (shown is Shown.Found && shown.synced) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        Text("Timing", fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = L.text)
                        Text(timingLabel(shown.offsetMs), fontFamily = Inter, fontSize = 14.sp, color = L.soft, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Washed("Earlier", { onNudge(250) })
                            Washed("Later", { onNudge(-250) })
                        }
                    }
                }
                if (showSpotify) SheetRow("Open Spotify", null) { onClose(); onSpotify() }
                SheetRow("Settings", null) { onClose(); onSettings() }
            }
        }
    }
}

@Composable
private fun SheetRow(title: String, note: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp)) {
        Text(title, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = L.text)
        note?.let { Text(it, fontFamily = Inter, fontSize = 14.sp, color = L.soft) }
    }
}
