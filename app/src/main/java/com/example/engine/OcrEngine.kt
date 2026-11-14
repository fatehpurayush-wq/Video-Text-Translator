package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import com.example.domain.model.LanguageCatalog
import com.example.domain.model.NormalizedRect
import com.example.domain.model.OcrEngineType
import com.example.domain.model.ScriptFamily
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class OcrResult(
    val text: String,
    val detectedLangCode: String,
    val confidence: Float,
    val unsureWordIndices: Set<Int>,
    val boundingBox: NormalizedRect?
)

class OcrEngine(private val context: Context) {

    private val latinRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val devanagariRecognizer by lazy {
        TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    }
    private val chineseRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }
    private val japaneseRecognizer by lazy {
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    }
    private val koreanRecognizer by lazy {
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    }
    private val languageIdentifier by lazy {
        LanguageIdentification.getClient()
    }

    fun tessDataRootDir(): File = context.filesDir

    fun isTesseractModelReady(tessCode: String): Boolean {
        val file = File(tessDataRootDir(), "tessdata/$tessCode.traineddata")
        return file.exists() && file.length() > 50_000L
    }

    fun isOcrAvailableForLanguage(langCode: String): Boolean {
        if (langCode == LanguageCatalog.AUTO_DETECT_CODE) return true
        val spec = LanguageCatalog.findByCode(langCode) ?: return true
        return when (spec.ocrEngineType) {
            OcrEngineType.MLKIT_BUNDLED -> true
            OcrEngineType.TESSERACT_PACK -> spec.tessCode?.let { isTesseractModelReady(it) } ?: false
        }
    }

    suspend fun recognizeFrame(
        fullFrame: Bitmap,
        cropRect: NormalizedRect?,
        sourceLangCode: String
    ): OcrResult = withContext(Dispatchers.Default) {
        val activeCrop = cropRect?.takeUnless { it.isFullFrame }
        val cropped = if (activeCrop != null) cropBitmap(fullFrame, activeCrop) else fullFrame
        val shouldRecycleCropped = cropped !== fullFrame

        try {
            val spec = if (sourceLangCode == LanguageCatalog.AUTO_DETECT_CODE) null else LanguageCatalog.findByCode(sourceLangCode)

            val rawResult = when {
                spec != null && spec.ocrEngineType == OcrEngineType.TESSERACT_PACK -> {
                    val tessCode = spec.tessCode
                    if (tessCode != null && isTesseractModelReady(tessCode)) {
                        runTesseract(cropped, tessCode, sourceLangCode, activeCrop)
                    } else {
                        // Fallback to Latin ML Kit if Tesseract pack is missing
                        runMlKitAutoOrScript(cropped, ScriptFamily.LATIN, activeCrop)
                    }
                }
                spec != null -> {
                    runMlKitAutoOrScript(cropped, spec.scriptFamily, activeCrop)
                }
                else -> {
                    // Auto-detect mode: run Latin, and if empty/non-Latin check Devanagari & downloaded Tesseract packs
                    runAutoDetectOcr(cropped, activeCrop)
                }
            }

            if (rawResult.text.isBlank()) {
                return@withContext rawResult
            }

            val finalLang = if (sourceLangCode == LanguageCatalog.AUTO_DETECT_CODE) {
                detectLanguageCode(rawResult.text, fallback = rawResult.detectedLangCode)
            } else {
                sourceLangCode
            }

            rawResult.copy(detectedLangCode = finalLang)
        } finally {
            if (shouldRecycleCropped && !cropped.isRecycled) {
                cropped.recycle()
            }
        }
    }

    suspend fun detectLanguageCode(text: String, fallback: String = "en"): String {
        if (text.isBlank()) return fallback
        val scriptGuess = guessLangByUnicodeScript(text)
        return try {
            val identified = languageIdentifier.identifyLanguage(text).await()
            if (identified == "und" || identified.isBlank()) {
                scriptGuess ?: fallback
            } else {
                identified.substringBefore("-")
            }
        } catch (_: Exception) {
            scriptGuess ?: fallback
        }
    }

    private suspend fun runAutoDetectOcr(bitmap: Bitmap, activeCrop: NormalizedRect?): OcrResult {
        val latin = runMlKitAutoOrScript(bitmap, ScriptFamily.LATIN, activeCrop)
        val dev = runMlKitAutoOrScript(bitmap, ScriptFamily.DEVANAGARI, activeCrop)

        // Prefer Devanagari if it contains Devanagari characters cleanly
        val devChars = dev.text.count { it.code in 0x0900..0x097F }
        if (devChars >= 2 && dev.confidence >= latin.confidence - 0.1f) {
            return dev.copy(detectedLangCode = "hi")
        }
        if (latin.text.isNotBlank()) return latin

        // Check CJK bundled models if Latin & Devanagari found nothing
        val chinese = runMlKitAutoOrScript(bitmap, ScriptFamily.CHINESE, activeCrop)
        if (chinese.text.isNotBlank()) return chinese.copy(detectedLangCode = "zh")

        val japanese = runMlKitAutoOrScript(bitmap, ScriptFamily.JAPANESE, activeCrop)
        if (japanese.text.isNotBlank()) return japanese.copy(detectedLangCode = "ja")

        val korean = runMlKitAutoOrScript(bitmap, ScriptFamily.KOREAN, activeCrop)
        if (korean.text.isNotBlank()) return korean.copy(detectedLangCode = "ko")

        return latin
    }

    private suspend fun runMlKitAutoOrScript(
        bitmap: Bitmap,
        scriptFamily: ScriptFamily,
        activeCrop: NormalizedRect?
    ): OcrResult {
        val recognizer = when (scriptFamily) {
            ScriptFamily.DEVANAGARI -> devanagariRecognizer
            ScriptFamily.CHINESE -> chineseRecognizer
            ScriptFamily.JAPANESE -> japaneseRecognizer
            ScriptFamily.KOREAN -> koreanRecognizer
            else -> latinRecognizer
        }

        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val mlResult = recognizer.process(inputImage).await()
        return parseMlKitText(mlResult, bitmap.width, bitmap.height, activeCrop, scriptFamily)
    }

    private fun parseMlKitText(
        visionText: Text,
        bmpWidth: Int,
        bmpHeight: Int,
        activeCrop: NormalizedRect?,
        scriptFamily: ScriptFamily
    ): OcrResult {
        val cleanText = FrameConsolidator.normalizeWhitespace(visionText.text)
        if (cleanText.isBlank()) {
            return OcrResult("", "en", 0f, emptySet(), null)
        }

        val unsureIndices = mutableSetOf<Int>()
        var wordIdx = 0
        var confSum = 0f
        var confCount = 0
        var unionRect: Rect? = null

        for (block in visionText.textBlocks) {
            block.boundingBox?.let { rect ->
                unionRect = if (unionRect == null) Rect(rect) else {
                    unionRect!!.union(rect)
                    unionRect
                }
            }
            for (line in block.lines) {
                for (element in line.elements) {
                    val conf = element.confidence.takeIf { it in 0f..1f } ?: 0.85f
                    confSum += conf
                    confCount++
                    if (conf < 0.65f) {
                        unsureIndices.add(wordIdx)
                    }
                    wordIdx++
                }
            }
        }

        val avgConf = if (confCount > 0) (confSum / confCount).coerceIn(0f, 1f) else 0.85f
        val normBox = unionRect?.let { r ->
            val localLeft = (r.left.toFloat() / max(1, bmpWidth)).coerceIn(0f, 1f)
            val localTop = (r.top.toFloat() / max(1, bmpHeight)).coerceIn(0f, 1f)
            val localRight = (r.right.toFloat() / max(1, bmpWidth)).coerceIn(0f, 1f)
            val localBottom = (r.bottom.toFloat() / max(1, bmpHeight)).coerceIn(0f, 1f)
            mapBackToFullFrame(
                NormalizedRect(localLeft, localTop, localRight, localBottom),
                activeCrop
            )
        }

        val defaultLang = when (scriptFamily) {
            ScriptFamily.DEVANAGARI -> "hi"
            ScriptFamily.CHINESE -> "zh"
            ScriptFamily.JAPANESE -> "ja"
            ScriptFamily.KOREAN -> "ko"
            else -> "en"
        }

        return OcrResult(
            text = cleanText,
            detectedLangCode = guessLangByUnicodeScript(cleanText) ?: defaultLang,
            confidence = avgConf,
            unsureWordIndices = unsureIndices,
            boundingBox = normBox
        )
    }

    private fun runTesseract(
        bitmap: Bitmap,
        tessCode: String,
        langCode: String,
        activeCrop: NormalizedRect?
    ): OcrResult {
        val enhanced = enhanceForOcr(bitmap)
        val tess = TessBaseAPI()
        return try {
            val initialized = tess.init(tessDataRootDir().absolutePath, tessCode)
            if (!initialized) {
                OcrResult("", langCode, 0f, emptySet(), null)
            } else {
                tess.setImage(enhanced)
                val rawText = FrameConsolidator.normalizeWhitespace(tess.utF8Text ?: "")
                val meanConf = (tess.meanConfidence().coerceIn(0, 100) / 100f)
                val words = rawText.split(" ").filter { it.isNotBlank() }
                val unsure = if (meanConf < 0.65f) words.indices.toSet() else emptySet()
                val box = activeCrop ?: NormalizedRect(0.08f, 0.74f, 0.92f, 0.94f)
                OcrResult(
                    text = rawText,
                    detectedLangCode = langCode,
                    confidence = meanConf,
                    unsureWordIndices = unsure,
                    boundingBox = box
                )
            }
        } catch (_: Throwable) {
            OcrResult("", langCode, 0f, emptySet(), null)
        } finally {
            runCatching { tess.recycle() }
            if (enhanced !== bitmap && !enhanced.isRecycled) enhanced.recycle()
        }
    }

    private fun enhanceForOcr(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        val cm = ColorMatrix().apply {
            setSaturation(0f)
            val contrast = 1.35f
            val translate = (-0.5f * contrast + 0.5f) * 255f
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    private fun cropBitmap(source: Bitmap, norm: NormalizedRect): Bitmap {
        val x = (norm.left * source.width).roundToInt().coerceIn(0, max(0, source.width - 2))
        val y = (norm.top * source.height).roundToInt().coerceIn(0, max(0, source.height - 2))
        val right = (norm.right * source.width).roundToInt().coerceIn(x + 2, source.width)
        val bottom = (norm.bottom * source.height).roundToInt().coerceIn(y + 2, source.height)
        val w = max(2, right - x)
        val h = max(2, bottom - y)
        return Bitmap.createBitmap(source, x, y, w, h)
    }

    private fun mapBackToFullFrame(local: NormalizedRect, activeCrop: NormalizedRect?): NormalizedRect {
        if (activeCrop == null) return local
        val cw = (activeCrop.right - activeCrop.left).coerceAtLeast(0.01f)
        val ch = (activeCrop.bottom - activeCrop.top).coerceAtLeast(0.01f)
        return NormalizedRect(
            left = (activeCrop.left + local.left * cw).coerceIn(0f, 1f),
            top = (activeCrop.top + local.top * ch).coerceIn(0f, 1f),
            right = (activeCrop.left + local.right * cw).coerceIn(0f, 1f),
            bottom = (activeCrop.top + local.bottom * ch).coerceIn(0f, 1f)
        )
    }

    private fun guessLangByUnicodeScript(text: String): String? {
        for (ch in text) {
            when (ch.code) {
                in 0x0900..0x097F -> return "hi"
                in 0x0980..0x09FF -> return "bn"
                in 0x0A00..0x0A7F -> return "pa"
                in 0x0A80..0x0AFF -> return "gu"
                in 0x0B80..0x0BFF -> return "ta"
                in 0x0C00..0x0C7F -> return "te"
                in 0x0C80..0x0CFF -> return "kn"
                in 0x0D00..0x0D7F -> return "ml"
                in 0x0600..0x06FF -> return "ar"
                in 0x0400..0x04FF -> return "ru"
                in 0x0E00..0x0E7F -> return "th"
                in 0x4E00..0x9FFF -> return "zh"
                in 0x3040..0x30FF -> return "ja"
                in 0xAC00..0xD7AF -> return "ko"
            }
        }
        return null
    }
}
