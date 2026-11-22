package dev.one.launcher.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap

/*
 * Film grain: Marquee's one texture. It lies over the platform's light
 * on every screen, the way grain lies over a picture on a cinema screen,
 * and nowhere else. Luminance noise around mid grey, laid on in Overlay
 * so it lightens and darkens the color under it without shifting its hue;
 * made once, small, and tiled.
 */

private const val GRAIN_SIZE = 128

/** How strongly the grain shows on the backdrop. */
const val GRAIN_AMOUNT = 0.16f

private val GrainImage: ImageBitmap by lazy {
    val random = java.util.Random(19)
    val pixels = IntArray(GRAIN_SIZE * GRAIN_SIZE) {
        val v = (128 + random.nextGaussian() * 48).toInt().coerceIn(0, 255)
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    Bitmap.createBitmap(pixels, GRAIN_SIZE, GRAIN_SIZE, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** The grain as a brush that tiles across whatever it fills. */
fun grainBrush(): ShaderBrush = ShaderBrush(ImageShader(GrainImage, TileMode.Repeated, TileMode.Repeated))
