package com.thescarletcoder.lyrics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thescarletcoder.scarlet.ScarletPalette
import com.thescarletcoder.scarlet.ScarletTokens

val Fraunces = FontFamily(Font(R.font.fraunces_display))
val Inter = FontFamily(Font(R.font.inter_regular), Font(R.font.inter_medium, FontWeight.Medium))

/** The resolved theme for everything below it. Lyrics uses neutrals only; scarlet only where no music shows. */
val LocalPalette = compositionLocalOf { ScarletTokens.DARK }

/** Short window (the Flip's split screen gives Lyrics the top half): compact header and smaller lyrics. */
val LocalCompact = staticCompositionLocalOf { false }

/** Neutral colours derived from the theme. */
object L {
    val p: ScarletPalette @Composable @ReadOnlyComposable get() = LocalPalette.current
    val text: Color @Composable @ReadOnlyComposable get() = Color(LocalPalette.current.text)
    val soft: Color @Composable @ReadOnlyComposable get() = Color(LocalPalette.current.soft)
    val faint: Color @Composable @ReadOnlyComposable get() = Color(LocalPalette.current.faint)
    val surface: Color @Composable @ReadOnlyComposable get() = Color(LocalPalette.current.surface)
    val line: Color @Composable @ReadOnlyComposable get() = Color(LocalPalette.current.line)
    /** Washed button: text colour at 7% with a 14% hairline. */
    val wash: Color @Composable @ReadOnlyComposable get() = text.copy(alpha = 0.07f)
    val washEdge: Color @Composable @ReadOnlyComposable get() = text.copy(alpha = 0.14f)
}

/** The one button style in Lyrics: a neutral wash. */
@Composable
fun Washed(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, small: Boolean = false) {
    val shape = RoundedCornerShape(if (small) 20.dp else 24.dp)
    Box(
        modifier.heightIn(min = if (small) 40.dp else 48.dp).clip(shape).background(L.wash).border(1.dp, L.washEdge, shape)
            .clickable(onClick = onClick).padding(horizontal = if (small) 14.dp else 18.dp, vertical = if (small) 8.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = if (small) 14.sp else 16.sp, color = L.text) }
}

/** A round washed icon button with a drawn icon (never a font glyph). */
@Composable
fun IconButton(size: Dp, onClick: () -> Unit, draw: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit) {
    val c = L.text
    Box(
        Modifier.size(size).clip(CircleShape).background(L.wash).border(1.dp, L.washEdge, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Canvas(Modifier.size(size * 0.45f)) { draw(c) } }
}

object Icons {
    /** Play: a rounded-feel triangle. */
    val play: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit = { c ->
        val w = size.width; val h = size.height
        drawPath(Path().apply { moveTo(w * 0.22f, h * 0.1f); lineTo(w * 0.9f, h * 0.5f); lineTo(w * 0.22f, h * 0.9f); close() }, c)
    }
    val pause: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit = { c ->
        val w = size.width; val h = size.height
        drawRoundRect(c, Offset(w * 0.18f, h * 0.1f), androidx.compose.ui.geometry.Size(w * 0.22f, h * 0.8f), androidx.compose.ui.geometry.CornerRadius(w * 0.05f))
        drawRoundRect(c, Offset(w * 0.6f, h * 0.1f), androidx.compose.ui.geometry.Size(w * 0.22f, h * 0.8f), androidx.compose.ui.geometry.CornerRadius(w * 0.05f))
    }
    val more: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit = { c ->
        val r = size.width * 0.1f
        listOf(0.18f, 0.5f, 0.82f).forEach { x -> drawCircle(c, r, Offset(size.width * x, size.height / 2)) }
    }
}

/** The raven with a soft translucent scarlet halo (no ring). Only where no music is showing. */
@Composable
fun RavenHalo(size: Dp = 112.dp) {
    val scarlet = Color(L.p.scarlet)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size * 0.75f)) { drawCircle(scarlet.copy(alpha = 0.14f)) }
        Image(painterResource(R.mipmap.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(size))
    }
}
