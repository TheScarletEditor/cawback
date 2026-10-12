package com.thescarletcoder.lyrics

import android.content.Context
import com.thescarletcoder.scarlet.ScarletTokens
import com.thescarletcoder.scarlet.ThemeChoice
import com.thescarletcoder.scarlet.Tunable
import com.thescarletcoder.scarlet.Tunables

/** Lyrics' feel values (LYRICS-POLISH-SPEC section 9). Defaults are the spec's starting points. */
object LyricsTuning {
    const val TINT_STRENGTH = "tint_strength"
    const val TINT_SATURATION = "tint_saturation"
    const val LINE_SCALE = "line_scale"
    const val GLIDE_MS = "glide_ms"
    const val ANCHOR = "anchor"
    const val ANCHOR_COMPACT = "anchor_compact"
    const val FADE_TOP_START = "fade_top_start"
    const val FADE_TOP_END = "fade_top_end"
    const val FADE_BOTTOM_START = "fade_bottom_start"
    const val FADE_BOTTOM_END = "fade_bottom_end"
    const val CHROME_HIDE_MS = "chrome_hide_ms"
    const val CHROME_FADE_MS = "chrome_fade_ms"
    const val SWAP_MS = "swap_ms"
    const val TINT_FADE_MS = "tint_fade_ms"
    const val LOADING_DELAY_MS = "loading_delay_ms"
    /** Taken off a Line up now or Fix lines tap: people tap a little after they hear a line (0.5.0). */
    const val REACTION_MS = "reaction_ms"

    /** Line alpha by distance from the current line: 1, 2, 3, then the floor (4 or more). */
    fun alphaName(dark: Boolean, step: Int) = "alpha_${if (dark) "dark" else "light"}_${if (step >= 4) "floor" else step.toString()}"

    private fun alphas(dark: Boolean, v: List<Float>) = v.mapIndexed { i, f ->
        Tunable(alphaName(dark, i + 1), if (dark) "Line depth (dark)" else "Line depth (light)", f, 0.1f, 1f, 0.01f)
    }

    private val list = listOf(
        // Ruling of 2026-10-09: 0.9 (the spec started at 0.07). At this strength the saturation cap sets how vivid it gets.
        Tunable(TINT_STRENGTH, "Album tint", 0.9f, 0f, 1f, 0.01f),
        Tunable(TINT_SATURATION, "Album tint", 0.5f, 0f, 1f, 0.01f),
        Tunable(TINT_FADE_MS, "Album tint", 800f, 0f, 2000f, 50f, isInt = true),
        Tunable(LINE_SCALE, "Lines", 1.06f, 1f, 1.2f, 0.01f),
        Tunable(GLIDE_MS, "Lines", 650f, 0f, 1500f, 25f, isInt = true),
        // 0.4.1 (owner, 2026-10-11): the current line sits in the true middle on every screen size (was 0.38 and 0.30).
        Tunable(ANCHOR, "Lines", 0.50f, 0.2f, 0.6f, 0.01f),
        Tunable(ANCHOR_COMPACT, "Lines", 0.50f, 0.15f, 0.6f, 0.01f),
    ) + alphas(true, listOf(0.60f, 0.48f, 0.42f, 0.38f)) + alphas(false, listOf(0.72f, 0.62f, 0.56f, 0.52f)) + listOf(
        Tunable(FADE_TOP_START, "Edge fades", 0.14f, 0f, 0.5f, 0.01f),
        Tunable(FADE_TOP_END, "Edge fades", 0.30f, 0f, 0.5f, 0.01f),
        Tunable(FADE_BOTTOM_START, "Edge fades", 0.78f, 0.5f, 1f, 0.01f),
        Tunable(FADE_BOTTOM_END, "Edge fades", 1.0f, 0.5f, 1f, 0.01f),
        Tunable(CHROME_HIDE_MS, "Header", 3000f, 1000f, 10000f, 250f, isInt = true),
        Tunable(CHROME_FADE_MS, "Header", 420f, 0f, 1500f, 20f, isInt = true),
        Tunable(SWAP_MS, "Song changes", 220f, 0f, 800f, 10f, isInt = true),
        Tunable(LOADING_DELAY_MS, "Song changes", 250f, 0f, 1500f, 25f, isInt = true),
        Tunable(REACTION_MS, "Fix timing", 200f, 0f, 600f, 20f, isInt = true),
    )

    /** A value's starting point, before any tuning on the device. */
    internal fun defaultOf(name: String): Float = list.first { it.name == name }.default

    fun init(context: Context) {
        val store = context.getSharedPreferences("tuning", Context.MODE_PRIVATE)
        // Once: the tint was set to 0.9 as the new starting point (2026-10-09). A value saved under the old 0..0.3 slider
        // would otherwise hide it.
        if (!store.getBoolean("tint_default_09", false)) store.edit().remove(TINT_STRENGTH).putBoolean("tint_default_09", true).apply()
        // Once, the same way: the line moved to the middle in 0.4.1, so a value saved at the old height doesn't hide it.
        if (!store.getBoolean("anchor_default_50", false)) store.edit().remove(ANCHOR).remove(ANCHOR_COMPACT).putBoolean("anchor_default_50", true).apply()
        Tunables.init(store, list)
    }
}

/** Lyrics' own settings (only the theme so far). */
class LyricsPrefs(context: Context) {
    private val p = context.getSharedPreferences("lyrics_prefs", Context.MODE_PRIVATE)
    var theme: ThemeChoice
        get() = ScarletTokens.parseChoice(p.getString("theme", null))
        set(v) = p.edit().putString("theme", v.name).apply()
}
