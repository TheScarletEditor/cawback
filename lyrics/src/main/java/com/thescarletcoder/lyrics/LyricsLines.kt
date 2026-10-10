package com.thescarletcoder.lyrics

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.thescarletcoder.scarlet.Motion
import com.thescarletcoder.scarlet.Tunables
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Soft fade at the top and bottom of the lyrics (offscreen layer + DstIn gradient). [top] (0..1) fades the top fade
 * in only once the list has scrolled, so the first lines sit right under the header at the start of a song.
 */
fun Modifier.edgeFade(top: () -> Float = { 1f }): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val k = top().coerceIn(0f, 1f)
        val a = Tunables[LyricsTuning.FADE_TOP_START] * k; val b = Tunables[LyricsTuning.FADE_TOP_END] * k
        val c = Tunables[LyricsTuning.FADE_BOTTOM_START]; val d = Tunables[LyricsTuning.FADE_BOTTOM_END]
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent, a to Color.Transparent, b to Color.Black, c to Color.Black, d to Color.Transparent,
                (if (d < 1f) 1f else d) to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
fun SyncedLyrics(s: Shown.Found, np: NowPlaying) {
    val list = rememberLazyListState()
    val dragged by list.interactionSource.collectIsDraggedAsState()
    var pos by remember { mutableLongStateOf(0L) }
    var lastTouch by remember { mutableLongStateOf(0L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val compact = LocalCompact.current
    val anchor = Tunables[if (compact) LyricsTuning.ANCHOR_COMPACT else LyricsTuning.ANCHOR]
    val settings = LocalSettings.current
    // This song's nudge plus the delay for the output in use (Bluetooth buds lag; Settings, Timing).
    val offset by rememberUpdatedState(s.offsetMs + LocalOutputDelay.current)

    LaunchedEffect(s) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { pos = np.positionMs() + offset; delay(60) }
        }
    }
    // Only the line index drives the list, so it recomposes when the line changes, not 16 times a second.
    val current by remember(s) { derivedStateOf { Lrc.indexAt(s.lines, pos) } }
    LaunchedEffect(dragged) { if (dragged) lastTouch = System.currentTimeMillis() }
    LaunchedEffect(current) {
        // Follow the song unless Sam scrolled in the last few seconds (Settings: 2, 4 or 8).
        if (!dragged && System.currentTimeMillis() - lastTouch > settings.followDelaySec * 1000L) glideTo(list, current.coerceAtLeast(0), anchor, Motion.duration(context, Tunables.int(LyricsTuning.GLIDE_MS)))
    }

    // At the top of a song the lyrics start right under the header; the current line glides down to the anchor
    // as the song goes on. The top fade appears once there's something scrolled away above.
    val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0 } }
    val topFade by animateFloatAsState(if (scrolled) 1f else 0f, tween(Motion.duration(context, Motion.NAV_MS), easing = Motion.NAV_EASE), label = "topFade")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val top = maxHeight * anchor
        LazyColumn(
            state = list, contentPadding = PaddingValues(top = 8.dp, bottom = maxHeight - top),
            modifier = Modifier.fillMaxSize().edgeFade { topFade },
        ) {
            itemsIndexed(s.lines) { i, line ->
                val gapEnd = if (line.text.isBlank()) s.lines.getOrNull(i + 1)?.timeMs else null
                LyricRow(i, line, current, compact, gapEnd, offset, np, settings) {
                    line.timeMs?.let { np.seekTo((it - offset).coerceAtLeast(0)); lastTouch = 0 }
                }
            }
        }
    }
}

/** Put line [i]'s centre at [anchor] of the list's height, gliding (LUX). Lays it out first if it isn't yet. */
private suspend fun glideTo(list: LazyListState, i: Int, anchor: Float, ms: Int) {
    if (list.layoutInfo.visibleItemsInfo.none { it.index == i }) list.scrollToItem(i)
    val info = list.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == i } ?: return
    val target = info.viewportStartOffset + (info.viewportEndOffset - info.viewportStartOffset) * anchor
    val delta = item.offset + item.size / 2f - target
    if (ms <= 0) list.scrollBy(delta) else list.animateScrollBy(delta, tween(ms, easing = Motion.LUX_EASE))
}

@Composable
private fun LyricRow(
    i: Int, line: LyricLine, current: Int, compact: Boolean, gapEnd: Long?, offsetMs: Long, np: NowPlaying, settings: LyricsSettings,
    onSeek: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val dark = L.p.dark
    val isCurrent = i == current
    val distance = kotlin.math.abs(i - current)
    val floor = Tunables[LyricsTuning.alphaName(dark, 4)]
    val alphaTarget = when {
        isCurrent -> 1f
        i < current && settings.hidePast -> 0f   // Settings: lines already sung hidden instead of dimmed
        else -> {
            val a = Tunables[LyricsTuning.alphaName(dark, distance.coerceAtMost(4))]
            if (i < current) maxOf(a * 0.9f, floor) else a // sung lines dim a little more, never below the floor
        }
    }
    val nav = Motion.duration(context, 520)
    val alpha by animateFloatAsState(alphaTarget, tween(nav, easing = Motion.NAV_EASE), label = "alpha")
    val scale by animateFloatAsState(
        if (isCurrent) Tunables[LyricsTuning.LINE_SCALE] else 1f,
        tween(Motion.duration(context, Motion.LUX_MS), easing = Motion.LUX_EASE), label = "scale",
    )
    val barAlpha by animateFloatAsState(if (isCurrent) 1f else 0f, tween(nav, easing = Motion.NAV_EASE), label = "bar")
    val wash = remember { Animatable(0f) }
    val text = L.text

    Row(
        Modifier.fillMaxWidth()
            .background(text.copy(alpha = 0.10f * wash.value))
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                scope.launch { wash.snapTo(1f); wash.animateTo(0f, tween(Motion.duration(context, 300))) }
                onSeek()
            }
            .padding(horizontal = if (compact) 16.dp else 24.dp, vertical = if (compact) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The current-line bar: neutral, fades with the line.
        Box(Modifier.width(3.dp).heightIn(min = 30.dp).graphicsLayer { this.alpha = barAlpha }
            .background(text.copy(alpha = if (dark) 0.55f else 0.45f), RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(16.dp))
        if (line.text.isBlank() && line.timeMs != null) {
            GapDots(isCurrent, line.timeMs, gapEnd, offsetMs, np, alpha)
        } else {
            // One size and one weight: nothing reflows when the song moves on. 6% end room so the scaled line never clips.
            Text(
                line.text,
                fontFamily = Inter, fontWeight = FontWeight.Medium,
                fontSize = (if (compact) 18f else 26f).times(LyricsSettings.TEXT_SCALE[settings.textSize]).sp,
                lineHeight = (if (compact) 24f else 34f).times(LyricsSettings.TEXT_SCALE[settings.textSize]).sp,
                color = text,
                modifier = Modifier.fillMaxWidth(0.94f).graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale; scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                },
            )
        }
    }
}

/** Instrumental gap: three dots that fill in as the gap passes (only animates while it's the current line). */
@Composable
private fun GapDots(isCurrent: Boolean, start: Long, end: Long?, offsetMs: Long, np: NowPlaying, rowAlpha: Float) {
    var p by remember { mutableFloatStateOf(0f) }
    if (isCurrent && end != null && end > start) LaunchedEffect(start, end) {
        while (true) withFrameMillis { p = ((np.positionMs() + offsetMs - start).toFloat() / (end - start)).coerceIn(0f, 1f) }
    }
    val c = L.text
    Row(Modifier.height(34.dp).graphicsLayer { alpha = rowAlpha }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until 3) {
            val a = if (isCurrent) 0.22f + 0.78f * (p * 3 - i).coerceIn(0f, 1f) else 0.22f
            Box(Modifier.size(7.dp).background(c.copy(alpha = a), CircleShape))
        }
    }
}

@Composable
fun PlainLyrics(s: Shown.Found) {
    val compact = LocalCompact.current
    val context = LocalContext.current
    val k = LyricsSettings.TEXT_SCALE[LocalSettings.current.textSize]
    val list = rememberLazyListState()
    // Same as synced: start right under the header; the top fade only once something has scrolled away.
    val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0 } }
    val topFade by animateFloatAsState(if (scrolled) 1f else 0f, tween(Motion.duration(context, Motion.NAV_MS), easing = Motion.NAV_EASE), label = "topFade")
    LazyColumn(Modifier.fillMaxSize().edgeFade { topFade }, state = list, contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 40.dp)) {
        item { Text("Not synced to the song. Scroll as you listen.", fontFamily = Inter, fontSize = 14.sp, color = L.soft, modifier = Modifier.padding(bottom = 14.dp)) }
        itemsIndexed(s.lines) { _, line ->
            Text(line.text, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = ((if (compact) 18f else 24f) * k).sp,
                lineHeight = ((if (compact) 24f else 34f) * k).sp, color = L.text)
        }
    }
}
