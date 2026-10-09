package com.thescarletcoder.lyrics

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The album tint (LYRICS-POLISH-SPEC section 2), as plain maths on 0xRRGGBB ints so it can be unit-tested.
 * A little of the cover's dominant colour goes into the background, then saturation and brightness are clamped
 * so every line of lyrics keeps its contrast.
 */
object TintMath {
    const val CHARCOAL = 0x171614
    const val BONE = 0xFAF9F6
    /** Dark backgrounds never get brighter than this (relative luminance). */
    const val DARK_MAX_LUMINANCE = 0.0205
    /** Light backgrounds never get darker than this. */
    const val LIGHT_MIN_LUMINANCE = 0.80
    const val MAX_SATURATION = 0.5

    /** The theme background with [strength] of [swatch] mixed in, clamped. */
    fun tint(dark: Boolean, swatch: Int, strength: Float): Int {
        val base = if (dark) CHARCOAL else BONE
        var c = mix(base, swatch, strength.coerceIn(0f, 1f))
        c = capSaturation(c, MAX_SATURATION)
        return if (dark) clampTowards(c, CHARCOAL) { luminance(it) <= DARK_MAX_LUMINANCE }
        else clampTowards(c, BONE) { luminance(it) >= LIGHT_MIN_LUMINANCE }
    }

    /** [a] to [b] by [t] (0..1), per channel. */
    fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = (((a shr s) and 0xFF) + (((b shr s) and 0xFF) - ((a shr s) and 0xFF)) * t + 0.5f).toInt().coerceIn(0, 255)
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** [text] at [alpha] drawn over [bg]: the colour the eye actually sees. */
    fun over(text: Int, alpha: Float, bg: Int) = mix(bg, text, alpha)

    /** Smallest mix from [c] toward [anchor] that satisfies [ok] (anchor itself always does). */
    private inline fun clampTowards(c: Int, anchor: Int, ok: (Int) -> Boolean): Int {
        if (ok(c)) return c
        var lo = 0f; var hi = 1f
        repeat(20) { val mid = (lo + hi) / 2; if (ok(mix(c, anchor, mid))) hi = mid else lo = mid }
        return mix(c, anchor, hi)
    }

    // ---- WCAG relative luminance and contrast ----

    private fun lin(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }

    fun luminance(c: Int): Double = 0.2126 * lin((c shr 16) and 0xFF) + 0.7152 * lin((c shr 8) and 0xFF) + 0.0722 * lin(c and 0xFF)

    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a); val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    // ---- HSL saturation cap ----

    fun capSaturation(c: Int, cap: Double): Int {
        val r = ((c shr 16) and 0xFF) / 255.0; val g = ((c shr 8) and 0xFF) / 255.0; val b = (c and 0xFF) / 255.0
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        val l = (mx + mn) / 2
        val d = mx - mn
        if (d == 0.0) return c
        val s = if (l > 0.5) d / (2 - mx - mn) else d / (mx + mn)
        if (s <= cap) return c
        val h = when (mx) {
            r -> ((g - b) / d + (if (g < b) 6 else 0)) / 6
            g -> ((b - r) / d + 2) / 6
            else -> ((r - g) / d + 4) / 6
        }
        return hsl(h, cap, l)
    }

    private fun hsl(h: Double, s: Double, l: Double): Int {
        val q = if (l < 0.5) l * (1 + s) else l + s - l * s
        val p = 2 * l - q
        fun hue(tt: Double): Double {
            var t = tt
            if (t < 0) t += 1.0
            if (t > 1) t -= 1.0
            return when {
                t < 1.0 / 6 -> p + (q - p) * 6 * t
                t < 1.0 / 2 -> q
                t < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - t) * 6
                else -> p
            }
        }
        fun c(v: Double) = (v * 255 + 0.5).toInt().coerceIn(0, 255)
        return (c(hue(h + 1.0 / 3)) shl 16) or (c(hue(h)) shl 8) or c(hue(h - 1.0 / 3))
    }
}
