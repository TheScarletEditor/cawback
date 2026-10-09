package com.thescarletcoder.lyrics

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the cover gives the screen: a small thumbnail for the header and its dominant colour (0xRRGGBB, or null). */
class AlbumLook(val thumb: ImageBitmap, val swatch: Int?)

/**
 * Album thumbnail and tint colour, worked out once per track off the main thread from the media session's bitmap.
 * Held in memory only, a few tracks at most. Never written to disk: that would be listening history.
 */
object AlbumTint {
    private const val KEEP = 6
    private val cache = object : LinkedHashMap<String, AlbumLook>(KEEP, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AlbumLook>?) = size > KEEP
    }

    suspend fun load(trackKey: String, art: Bitmap, thumbPx: Int): AlbumLook? {
        synchronized(cache) { cache[trackKey]?.let { return it } }
        return withContext(Dispatchers.Default) {
            runCatching {
                if (art.isRecycled || art.width <= 0 || art.height <= 0) return@runCatching null
                val small = scaled(art, 64)
                val swatch = Palette.from(small).generate().dominantSwatch?.rgb?.and(0xFFFFFF)
                val thumb = scaled(art, thumbPx).asImageBitmap()
                AlbumLook(thumb, swatch).also { look -> synchronized(cache) { cache[trackKey] = look } }
            }.getOrNull()
        }
    }

    /** Longest side scaled to [side] px (never upscaled). */
    private fun scaled(b: Bitmap, side: Int): Bitmap {
        val longest = maxOf(b.width, b.height)
        if (longest <= side) return b
        val f = side.toFloat() / longest
        return Bitmap.createScaledBitmap(b, (b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1), true)
    }
}
