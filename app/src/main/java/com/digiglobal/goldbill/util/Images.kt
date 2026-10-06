package com.digiglobal.goldbill.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream

/**
 * Turns a picked picture into a small base64 string that can be saved with the company or
 * person in the database (no paid file storage needed). A 256 px image is ~15–40 KB.
 */
object Images {

    /** Team member photo: square, centre-cropped, JPEG. */
    fun photo(ctx: Context, uri: Uri): String? = encode(ctx, uri, 256, square = true)

    /** Company logo: whole logo kept, on white if transparent, JPEG. */
    fun logo(ctx: Context, uri: Uri): String? = encode(ctx, uri, 320, square = false)

    private fun encode(ctx: Context, uri: Uri, max: Int, square: Boolean): String? = runCatching {
        // 1. Read the size only, then decode at a reduced size to save memory.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= max && bounds.outHeight / (sample * 2) >= max) sample *= 2
        var bmp = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        // 2. Rotate photos taken in portrait.
        val rotation = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        if (rotation != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)

        // 3. Crop to a square (photos) and scale down.
        if (square) {
            val side = minOf(bmp.width, bmp.height)
            bmp = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
        }
        val scale = minOf(1f, max.toFloat() / maxOf(bmp.width, bmp.height))
        if (scale < 1f) bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true)

        // 4. Put transparent logos on white so they don't turn black as JPEG.
        val flat = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply { drawColor(Color.WHITE); drawBitmap(bmp, 0f, 0f, null) }

        val out = ByteArrayOutputStream()
        flat.compress(Bitmap.CompressFormat.JPEG, 82, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()

    fun decode(b64: String): Bitmap? = runCatching {
        if (b64.isBlank()) return null
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
