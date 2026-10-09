package com.thescarletcoder.lyrics

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Worst-case covers in both themes (LYRICS-POLISH-SPEC section 2): every lyric row keeps its contrast after
 * the tint and clamp. Tested at full tint strength too, so tuning tint_strength up can't break it.
 */
class TintMathTest {
    private val covers = mapOf("yellow" to 0xFFFF00, "red" to 0xFF0000, "white" to 0xFFFFFF, "black" to 0x000000,
        "blue" to 0x0000FF, "green" to 0x00FF00)
    private val strengths = listOf(0.07f, 0.2f, 1f)

    private fun check(dark: Boolean) {
        val text = if (dark) TintMath.BONE else TintMath.CHARCOAL
        val soft = if (dark) 0x908E86 else 0x66635D
        val next = if (dark) 0.60f else 0.72f
        val floor = if (dark) 0.38f else 0.52f
        for ((name, cover) in covers) for (st in strengths) {
            val bg = TintMath.tint(dark, cover, st)
            val tag = "${if (dark) "dark" else "light"} $name @$st"
            val lum = TintMath.luminance(bg)
            if (dark) assertTrue("$tag luminance $lum", lum <= TintMath.DARK_MAX_LUMINANCE + 1e-4)
            else assertTrue("$tag luminance $lum", lum >= TintMath.LIGHT_MIN_LUMINANCE - 1e-4)
            assertTrue("$tag current", TintMath.contrast(text, bg) >= 4.5)
            assertTrue("$tag next ${TintMath.contrast(TintMath.over(text, next, bg), bg)}", TintMath.contrast(TintMath.over(text, next, bg), bg) >= 4.5)
            assertTrue("$tag distant ${TintMath.contrast(TintMath.over(text, floor, bg), bg)}", TintMath.contrast(TintMath.over(text, floor, bg), bg) >= 3.0)
            assertTrue("$tag artist ${TintMath.contrast(soft, bg)}", TintMath.contrast(soft, bg) >= 4.5)
        }
    }

    @Test fun darkTheme() = check(dark = true)
    @Test fun lightTheme() = check(dark = false)

    @Test fun saturationIsCapped() {
        val c = TintMath.capSaturation(0xFF0000, 0.5)
        // Pure red at lightness 0.5 with saturation 0.5: rgb(191, 64, 64)
        assertTrue(c == 0xBF4040)
    }
}
