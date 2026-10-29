package com.example.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/** A picture or video the user attached. Video becomes a few frames so any vision model can look at it. */
data class Attachment(val label: String, val dataUrls: List<String>, val isVideo: Boolean)

object MediaPrep {
    private const val MAX_SIDE = 1280
    private const val VIDEO_FRAMES = 4

    fun toDataUrl(bmp: Bitmap, maxSide: Int = MAX_SIDE, quality: Int = 80): String {
        val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
        val b = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true) else bmp
        val out = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /** Reads an image or video the user picked. Returns null if it cannot be decoded. */
    fun fromUri(context: Context, uri: Uri): Attachment? = try {
        val type = context.contentResolver.getType(uri).orEmpty()
        if (type.startsWith("video/")) video(context, uri) else image(context, uri)
    } catch (_: Exception) { null }

    private fun image(context: Context, uri: Uri): Attachment? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        return Attachment("Photo", listOf(toDataUrl(bmp)), false)
    }

    private fun video(context: Context, uri: Uri): Attachment? {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val frames = (1..VIDEO_FRAMES).mapNotNull { i ->
                r.getFrameAtTime(durMs * 1000L * i / (VIDEO_FRAMES + 1), MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { toDataUrl(it, 960, 75) }
            }
            return if (frames.isEmpty()) null else Attachment("Video (${frames.size} frames)", frames, true)
        } finally { r.release() }
    }
}
