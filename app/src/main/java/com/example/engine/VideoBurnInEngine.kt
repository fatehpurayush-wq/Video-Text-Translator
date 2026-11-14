package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.domain.model.AspectRatioOption
import com.example.domain.model.CaptionPosition
import com.example.domain.model.CaptionStyleConfig
import com.example.domain.model.SegmentWithTranslation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

class VideoBurnInEngine(private val context: Context) {

    @OptIn(UnstableApi::class)
    suspend fun exportBurnedVideo(
        videoUri: Uri,
        outputFile: File,
        segments: List<SegmentWithTranslation>,
        style: CaptionStyleConfig,
        onProgress: suspend (progressFraction: Float) -> Unit
    ): File = withContext(Dispatchers.Main) {
        if (outputFile.exists()) outputFile.delete()
        outputFile.parentFile?.mkdirs()

        val sortedSegments = segments.sortedBy { it.startTimeMs }
        val overlay = SubtitleBitmapOverlay(sortedSegments, style)
        val overlayEffect = OverlayEffect(listOf(overlay))

        val videoEffects = mutableListOf<Effect>()
        when (style.aspectRatio) {
            AspectRatioOption.VERTICAL_9_16 -> {
                videoEffects.add(Presentation.createForAspectRatio(9f / 16f, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
            }
            AspectRatioOption.SQUARE_1_1 -> {
                videoEffects.add(Presentation.createForAspectRatio(1f, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
            }
            AspectRatioOption.ORIGINAL -> Unit
        }
        videoEffects.add(overlayEffect)

        val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(videoUri))
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        val progressScope = CoroutineScope(Dispatchers.Default)

        suspendCancellableCoroutine { cont ->
            val handler = Handler(Looper.getMainLooper())
            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        handler.removeCallbacksAndMessages(null)
                        if (cont.isActive) cont.resume(outputFile)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        handler.removeCallbacksAndMessages(null)
                        if (cont.isActive) cont.resumeWithException(exportException)
                    }
                })
                .build()

            transformer.start(editedMediaItem, outputFile.absolutePath)

            cont.invokeOnCancellation {
                handler.removeCallbacksAndMessages(null)
                runCatching { transformer.cancel() }
            }

            val progressHolder = ProgressHolder()
            val poller = object : Runnable {
                override fun run() {
                    if (!cont.isActive) return
                    val state = transformer.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        val frac = (progressHolder.progress / 100f).coerceIn(0f, 1f)
                        progressScope.launch { onProgress(frac) }
                    }
                    if (cont.isActive) {
                        handler.postDelayed(this, 350L)
                    }
                }
            }
            handler.post(poller)
        }
    }

    @OptIn(UnstableApi::class)
    private class SubtitleBitmapOverlay(
        private val segments: List<SegmentWithTranslation>,
        private val captionStyle: CaptionStyleConfig
    ) : BitmapOverlay() {

        private var frameWidth = 1280
        private var frameHeight = 720
        private var reusableBitmap: Bitmap? = null
        private var reusableCanvas: Canvas? = null
        private val emptyBitmap: Bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        private var lastRenderedSegId: Long = Long.MIN_VALUE

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val baseColor = captionStyle.bgColorHex.toInt()
            color = Color.argb(
                (captionStyle.bgAlpha.coerceIn(0f, 1f) * 255).roundToInt(),
                Color.red(baseColor),
                Color.green(baseColor),
                Color.blue(baseColor)
            )
            style = Paint.Style.FILL
        }

        private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = captionStyle.textColorHex.toInt()
            typeface = Typeface.create(
                Typeface.DEFAULT,
                if (captionStyle.boldFont) Typeface.BOLD else Typeface.NORMAL
            )
        }

        private val strokePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = captionStyle.outlineColorHex.toInt()
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            typeface = Typeface.create(
                Typeface.DEFAULT,
                if (captionStyle.boldFont) Typeface.BOLD else Typeface.NORMAL
            )
        }

        override fun configure(videoSize: Size) {
            super.configure(videoSize)
            frameWidth = videoSize.width.coerceAtLeast(320)
            frameHeight = videoSize.height.coerceAtLeast(240)
            val scaledTextPx = (captionStyle.fontSizeSp * (frameWidth / 390f)).coerceIn(18f, frameHeight * 0.08f)
            textPaint.textSize = scaledTextPx
            strokePaint.textSize = scaledTextPx
            strokePaint.strokeWidth = (scaledTextPx * 0.14f).coerceAtLeast(2f)

            if (reusableBitmap == null || reusableBitmap?.width != frameWidth || reusableBitmap?.height != frameHeight) {
                reusableBitmap?.recycle()
                reusableBitmap = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888)
                reusableCanvas = Canvas(reusableBitmap!!)
                lastRenderedSegId = Long.MIN_VALUE
            }
        }

        override fun getBitmap(presentationTimeUs: Long): Bitmap {
            val timeMs = presentationTimeUs / 1000L
            val active = segments.firstOrNull { timeMs in it.startTimeMs..it.endTimeMs }
                ?: return emptyBitmap

            val bmp = reusableBitmap ?: return emptyBitmap
            val canvas = reusableCanvas ?: return emptyBitmap

            if (lastRenderedSegId == active.id) {
                return bmp
            }
            lastRenderedSegId = active.id

            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

            val translatedWrapped = wrapWords(
                active.translatedText.ifBlank { active.originalText },
                captionStyle.maxWordsPerLine
            )
            val displayText = if (captionStyle.showBothOriginalAndTranslation) {
                "${wrapWords(active.originalText, captionStyle.maxWordsPerLine)}\n$translatedWrapped"
            } else {
                translatedWrapped
            }

            val maxBoxWidth = (frameWidth * 0.88f).roundToInt().coerceAtLeast(100)
            val layout = StaticLayout.Builder.obtain(displayText, 0, displayText.length, textPaint, maxBoxWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .build()

            val padX = frameWidth * 0.03f
            val padY = frameHeight * 0.018f
            val contentW = (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) } ?: maxBoxWidth.toFloat()
            val boxW = (contentW + padX * 2).coerceAtMost(frameWidth * 0.94f)
            val boxH = layout.height + padY * 2

            val spot = active.boundingBox
            val targetCenterY = when {
                captionStyle.position == CaptionPosition.AUTO_ORIGINAL_SPOT && spot != null -> {
                    ((spot.top + spot.bottom) * 0.5f) * frameHeight
                }
                captionStyle.position == CaptionPosition.TOP -> frameHeight * 0.14f
                else -> frameHeight * 0.84f
            }

            val top = (targetCenterY - boxH * 0.5f).coerceIn(padY, (frameHeight - boxH - padY).coerceAtLeast(padY))
            val left = ((frameWidth - boxW) * 0.5f).coerceAtLeast(frameWidth * 0.03f)
            val rect = RectF(left, top, left + boxW, top + boxH)

            canvas.drawRoundRect(rect, 16f, 16f, bgPaint)

            canvas.save()
            canvas.translate((frameWidth - maxBoxWidth) * 0.5f, top + padY)
            if (captionStyle.outlineEnabled) {
                val outlineLayout = StaticLayout.Builder.obtain(displayText, 0, displayText.length, strokePaint, maxBoxWidth)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(false)
                    .build()
                outlineLayout.draw(canvas)
            }
            layout.draw(canvas)
            canvas.restore()

            return bmp
        }

        override fun release() {
            super.release()
            reusableBitmap?.recycle()
            reusableBitmap = null
            reusableCanvas = null
            if (!emptyBitmap.isRecycled) emptyBitmap.recycle()
        }

        private fun wrapWords(text: String, maxWordsPerLine: Int): String {
            val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty() || maxWordsPerLine <= 0) return text
            return words.chunked(maxWordsPerLine).joinToString("\n") { it.joinToString(" ") }
        }
    }
}
