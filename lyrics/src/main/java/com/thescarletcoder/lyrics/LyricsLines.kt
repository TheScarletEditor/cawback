package com.thescarletcoder.lyrics

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
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
/**
 * [onMoment]: a line was long-pressed; bookmark that time in the song (the Library; never the line's text).
 * [onPick]: "Try another version" on the end note (opens Other versions).
 */
fun SyncedLyrics(s: Shown.Found, np: NowPlaying, onMoment: (Long) -> Unit = {}, onPick: () -> Unit = {}) {
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

    var known by remember { mutableStateOf(false) }   // the first real position has been read
    LaunchedEffect(s) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { pos = np.positionMs() + offset; known = true; delay(60) }
        }
    }
    // The last row is the end note when the lyrics run out (Lrc.withEnd).
    val lines = remember(s) { Lrc.withEnd(s.lines) ?: s.lines }
    val endRow = if (lines !== s.lines) lines.lastIndex else -1
    // Only the line index drives the list, so it recomposes when the line changes, not 16 times a second.
    val current by remember(lines) { derivedStateOf { Lrc.indexAt(lines, pos) } }
    // The follow delay counts from letting go of a drag, so a long scroll by hand isn't thrown away when it ends.
    LaunchedEffect(dragged) { lastTouch = Follow.touched(dragged, lastTouch, System.currentTimeMillis()) }
    fun following() = Follow.now(dragged, System.currentTimeMillis(), lastTouch, settings.followDelaySec)
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(current, known) {
        if (!known) return@LaunchedEffect
        // Follow the song unless the user scrolled in the last few seconds (Settings: 2, 4 or 8). Coming back to the
        // lyrics (from Settings after a text size change, say) jumps straight to the middle; after that each line glides.
        val ms = if (placed) Motion.duration(context, Tunables.int(LyricsTuning.GLIDE_MS)) else 0
        placed = true
        if (following()) glideTo(list, current.coerceAtLeast(0), anchor, ms)
    }

    // At the top of a song the lyrics start right under the header; the current line glides down to the anchor
    // as the song goes on. The top fade appears once there's something scrolled away above.
    val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0 } }
    val topFade by animateFloatAsState(if (scrolled) 1f else 0f, tween(Motion.duration(context, Motion.NAV_MS), easing = Motion.NAV_EASE), label = "topFade")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val top = maxHeight * anchor
        // A resize (split divider, fold, rotation), the compact layout or a new text size moves the lines without a new
        // current line: put it straight back in the middle, no glide, unless the user scrolled by hand just now.
        var laidOut by remember { mutableStateOf(false) }
        LaunchedEffect(maxWidth, maxHeight, compact, settings.textSize, anchor) {
            if (!laidOut) { laidOut = true; return@LaunchedEffect }
            withFrameNanos { }   // let the list measure at the new size first
            if (following()) glideTo(list, current.coerceAtLeast(0), anchor, 0)
        }
        LazyColumn(
            state = list, contentPadding = PaddingValues(top = 8.dp, bottom = maxHeight - top),
            modifier = Modifier.fillMaxSize().edgeFade { topFade },
        ) {
            itemsIndexed(lines) { i, line ->
                if (i == endRow) { EndNote(i == current, compact, onPick); return@itemsIndexed }
                val gapEnd = if (line.text.isBlank()) lines.getOrNull(i + 1)?.timeMs else null
                LyricRow(i, line, current, compact, gapEnd, offset, np, settings,
                    onLongPress = { line.timeMs?.let { onMoment((it - offset).coerceAtLeast(0)) } }) {
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
    val delta = Follow.delta(item.offset, item.size, info.viewportStartOffset, info.viewportEndOffset, anchor)
    // A jump (ms 0) takes the scroll over from a glide still running, so the two never fight.
    if (ms <= 0) list.scrollBy(delta) else list.animateScrollBy(delta, tween(ms, easing = Motion.LUX_EASE))
}

/** When and where the lyrics follow the song. Plain maths, tested on the JVM. */
internal object Follow {
    /** Follow unless the user is dragging or touched the list within the follow delay (Settings: 2, 4 or 8 seconds). */
    fun now(dragged: Boolean, nowMs: Long, lastTouchMs: Long, delaySec: Int) = !dragged && nowMs - lastTouchMs > delaySec * 1000L

    /** The touch time after a drag starts or ends: stamped both times, so the delay runs from letting go. 0 = untouched. */
    fun touched(dragged: Boolean, lastTouchMs: Long, nowMs: Long): Long = if (dragged || lastTouchMs != 0L) nowMs else lastTouchMs

    /** How far to scroll so an item's centre lands at [anchor] (0 top, 1 bottom) of the viewport. */
    fun delta(itemOffset: Int, itemSize: Int, viewportStart: Int, viewportEnd: Int, anchor: Float): Float =
        itemOffset + itemSize / 2f - (viewportStart + (viewportEnd - viewportStart) * anchor)
}

@Composable
private fun LyricRow(
    i: Int, line: LyricLine, current: Int, compact: Boolean, gapEnd: Long?, offsetMs: Long, np: NowPlaying, settings: LyricsSettings,
    onLongPress: () -> Unit, onSeek: () -> Unit,
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
            .combinedClickable(
                onClickLabel = "Play from here",
                onLongClickLabel = "Bookmark this moment",
                onLongClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onLongPress()
                },
            ) {
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

/** After the last sung line: says the words have run out, with a way to another version. Fades in when it's reached. */
@Composable
private fun EndNote(isCurrent: Boolean, compact: Boolean, onPick: () -> Unit) {
    val context = LocalContext.current
    val alpha by animateFloatAsState(if (isCurrent) 1f else 0f, tween(Motion.duration(context, 520), easing = Motion.NAV_EASE), label = "end")
    Column(Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha }
        .padding(start = if (compact) 35.dp else 43.dp, end = 16.dp, top = 12.dp)) {
        Text("That's all the lyrics this version has.", fontFamily = Inter, fontSize = 17.sp, color = L.soft)
        // Tappable only once it shows, so an unseen button can't catch a tap meant for a line above.
        if (isCurrent) Box(Modifier.padding(top = 10.dp)) { Washed("Try another version", onPick, small = true) }
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
