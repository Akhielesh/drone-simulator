package com.akhielesh.datum.ui.screens.objects

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.akhielesh.datum.core.geometry.Quad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

class LoadedImage(
    val bitmap: Bitmap,
    /** Focal length in pixels of [bitmap] (from EXIF 35 mm-equivalent, or a typical phone default). */
    val focalPx: Double,
    val focalFromExif: Boolean,
    /** Luminance in 0..1 at reduced resolution, for corner snapping. */
    val gray: FloatArray,
    val grayW: Int,
    val grayH: Int,
) {
    /** Gray-image pixels per bitmap pixel. */
    val grayScale: Float get() = grayW.toFloat() / bitmap.width
}

object ImageTools {
    private const val FULL_FRAME_DIAGONAL_MM = 43.27

    suspend fun load(context: Context, uri: Uri, maxSide: Int = 2048): LoadedImage? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })
            } ?: return@runCatching null
            val exif = resolver.openInputStream(uri)?.use { ExifInterface(it) }
            val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                ?: ExifInterface.ORIENTATION_NORMAL
            var bmp = rotate(decoded, orientation)
            if (max(bmp.width, bmp.height) > maxSide) {
                val k = maxSide.toFloat() / max(bmp.width, bmp.height)
                bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * k).roundToInt(), (bmp.height * k).roundToInt(), true)
            }
            val f35 = exif?.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0) ?: 0
            val diag = hypot(bmp.width.toDouble(), bmp.height.toDouble())
            // 35 mm-equivalent focal length maps to pixels via the frame diagonal (aspect-independent).
            val focal = (if (f35 > 0) f35.toDouble() else 26.0) * diag / FULL_FRAME_DIAGONAL_MM
            val (gray, gw, gh) = grayscale(bmp, 1024)
            LoadedImage(bmp, focal, f35 > 0, gray, gw, gh)
        }.getOrNull()
    }

    private fun rotate(src: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                m.postRotate(90f); m.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                m.postRotate(270f); m.postScale(-1f, 1f)
            }
            else -> return src
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    private fun grayscale(src: Bitmap, maxSide: Int): Triple<FloatArray, Int, Int> {
        val k = (maxSide.toFloat() / max(src.width, src.height)).coerceAtMost(1f)
        val w = max(1, (src.width * k).roundToInt())
        val h = max(1, (src.height * k).roundToInt())
        val small = if (k < 1f) Bitmap.createScaledBitmap(src, w, h, true) else src
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        val g = FloatArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            g[i] = (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)) / 255f
        }
        return Triple(g, w, h)
    }

    /** Warps the [quad] region of [src] into an upright [outW]×[outH] rectangle (perspective removed). */
    fun rectify(src: Bitmap, quad: Quad, outW: Int, outH: Int): Bitmap {
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val m = Matrix()
        val s = quad.points.flatMap { listOf(it.x.toFloat(), it.y.toFloat()) }.toFloatArray()
        val d = floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat())
        if (m.setPolyToPoly(s, 0, d, 0, 4)) {
            Canvas(out).drawBitmap(src, m, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        return out
    }

    fun saveJpeg(bitmap: Bitmap, file: File, quality: Int = 88): Boolean = runCatching {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        true
    }.getOrDefault(false)

    fun loadFile(path: String, maxSide: Int = 1024): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}
