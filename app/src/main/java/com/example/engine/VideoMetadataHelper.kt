package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

data class VideoMeta(
    val title: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val thumbnailPath: String?
)

class VideoMetadataHelper(private val context: Context) {

    suspend fun extractMetadataAndThumbnail(videoUri: Uri, projectIdHint: Long = System.currentTimeMillis()): VideoMeta =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            var title = "Video_${projectIdHint % 10000}"
            var sizeBytes = 0L

            context.contentResolver.query(videoUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        val rawName = cursor.getString(nameIdx)
                        if (!rawName.isNullOrBlank()) {
                            title = rawName.substringBeforeLast(".")
                        }
                    }
                    if (sizeIdx >= 0) sizeBytes = cursor.getLong(sizeIdx)
                }
            }

            try {
                retriever.setDataSource(context, videoUri)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 0
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: 0

                if (durationMs <= 0L && width <= 0) {
                    throw IllegalArgumentException("Yeh video format supported nahi hai ya file kharab hai.")
                }

                val sampleUs = (durationMs * 1000L / 5L).coerceAtLeast(500_000L)
                val frame = retriever.getFrameAtTime(sampleUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

                val thumbPath = frame?.let { bmp ->
                    val scaled = scaleDownBitmap(bmp, 480)
                    val thumbsDir = File(context.filesDir, "thumbs").apply { mkdirs() }
                    val file = File(thumbsDir, "thumb_${projectIdHint}.jpg")
                    FileOutputStream(file).use { out ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
                    }
                    if (scaled !== bmp) scaled.recycle()
                    bmp.recycle()
                    file.absolutePath
                }

                VideoMeta(
                    title = title,
                    durationMs = durationMs,
                    width = width,
                    height = height,
                    sizeBytes = sizeBytes,
                    thumbnailPath = thumbPath
                )
            } catch (e: Exception) {
                throw IllegalArgumentException(
                    e.message ?: "Yeh video format supported nahi hai. Kripya MP4, MKV ya MOV video chunein."
                )
            } finally {
                runCatching { retriever.release() }
            }
        }

    suspend fun extractFrameAt(videoUri: Uri, timeMs: Long, maxDimension: Int = 960): Bitmap? =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, videoUri)
                val raw = retriever.getFrameAtTime(
                    timeMs.coerceAtLeast(0L) * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: return@withContext null
                val scaled = scaleDownBitmap(raw, maxDimension)
                if (scaled !== raw) raw.recycle()
                scaled
            } catch (_: Exception) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }

    fun scaleDownBitmap(source: Bitmap, maxDimension: Int): Bitmap {
        val maxSide = max(source.width, source.height)
        if (maxSide <= maxDimension) return source
        val ratio = maxDimension.toFloat() / maxSide.toFloat()
        val newW = (source.width * ratio).toInt().coerceAtLeast(2)
        val newH = (source.height * ratio).toInt().coerceAtLeast(2)
        return Bitmap.createScaledBitmap(source, newW, newH, true)
    }
}
