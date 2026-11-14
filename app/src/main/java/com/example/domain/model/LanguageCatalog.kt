package com.example.domain.model

import com.google.mlkit.nl.translate.TranslateLanguage

enum class ScriptFamily {
    LATIN,
    DEVANAGARI,
    CHINESE,
    JAPANESE,
    KOREAN,
    TELUGU,
    TAMIL,
    KANNADA,
    MALAYALAM,
    BENGALI,
    GUJARATI,
    GURMUKHI,
    ARABIC_URDU,
    CYRILLIC,
    THAI
}

enum class OcrEngineType {
    MLKIT_BUNDLED,
    TESSERACT_PACK
}

enum class AccuracyRating(val labelHinglish: String) {
    GOOD("Achhi (Good)"),
    AVERAGE("Theek (Average)"),
    LOW("Kamzor (Low)")
}

data class LanguageSpec(
    val code: String,
    val englishName: String,
    val nativeName: String,
    val scriptFamily: ScriptFamily,
    val ocrEngineType: OcrEngineType,
    val tessCode: String? = null,
    val tessSizeMb: Float = 0f,
    val mlkitTranslateCode: String? = null,
    val translateSizeMb: Float = 30f,
    val voskModelId: String? = null,
    val voskModelUrl: String? = null,
    val voskSizeMb: Float = 0f,
    val ocrAccuracy: AccuracyRating = AccuracyRating.GOOD,
    val speechAccuracy: AccuracyRating = AccuracyRating.AVERAGE,
    val isIndianLanguage: Boolean = false,
    val supportsRomanScript: Boolean = false
) {
    val ocrDownloadUrl: String?
        get() = tessCode?.let {
            "https://github.com/tesseract-ocr/tessdata_fast/raw/main/$it.traineddata"
        }

    val isOcrBundled: Boolean
        get() = ocrEngineType == OcrEngineType.MLKIT_BUNDLED

    val supportsOfflineTranslation: Boolean
        get() = mlkitTranslateCode != null

    val supportsOfflineSpeech: Boolean
        get() = voskModelUrl != null
}

object LanguageCatalog {

    val AUTO_DETECT_CODE = "auto"

    val allLanguages: List<LanguageSpec> = listOf(
        LanguageSpec(
            code = "en",
            englishName = "English",
            nativeName = "English",
            scriptFamily = ScriptFamily.LATIN,
            ocrEngineType = OcrEngineType.MLKIT_BUNDLED,
            mlkitTranslateCode = TranslateLanguage.ENGLISH,
            voskModelId = "vosk-model-small-en-us-0.15",
            voskModelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            voskSizeMb = 40f,
            ocrAccuracy = AccuracyRating.GOOD,
            speechAccuracy = AccuracyRating.GOOD
        ),
        LanguageSpec(
            code = "hi",
            englishName = "Hindi",
            nativeName = "हिन्दी",
            scriptFamily = ScriptFamily.DEVANAGARI,
            ocrEngineType = OcrEngineType.MLKIT_BUNDLED,
            mlkitTranslateCode = TranslateLanguage.HINDI,
            voskModelId = "vosk-model-small-hi-0.22",
            voskModelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip",
            voskSizeMb = 42f,
            ocrAccuracy = AccuracyRating.GOOD,
            speechAccuracy = AccuracyRating.GOOD,
            isIndianLanguage = true,
            supportsRomanScript = true
        ),
        LanguageSpec(
            code = "mr",
            englishName = "Marathi",
            nativeName = "मराठी",
            scriptFamily = ScriptFamily.DEVANAGARI,
            ocrEngineType = OcrEngineType.MLKIT_BUNDLED,
            mlkitTranslateCode = TranslateLanguage.MARATHI,
            voskModelId = "vosk-model-small-hi-0.22",
            voskModelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip",
            voskSizeMb = 42f,
            ocrAccuracy = AccuracyRating.GOOD,
            speechAccuracy = AccuracyRating.AVERAGE,
            isIndianLanguage = true,
            supportsRomanScript = true
        ),
        LanguageSpec(
            code = "te",
            englishName = "Telugu",
            nativeName = "తెలుగు",
            scriptFamily = ScriptFamily.TELUGU,
            ocrEngineType = OcrEngineType.TESSERACT_PACK,
            tessCode = "tel",
            tessSizeMb = 3.2f,
            mlkitTranslateCode = TranslateLanguage.TELUGU,
            voskModelId = "vosk-model-small-te-0.42",
            voskModelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-te-0.42.zip",
            voskSizeMb = 58f,
            ocrAccuracy = AccuracyRating.AVERAGE,
            speechAccuracy = AccuracyRating.AVERAGE,
            isIndianLanguage = true,
            supportsRomanScript = true
        ),
        LanguageSpec(
            code = "ta",
            englishName = "Tamil",
            nativeName = "தமிழ்",
            scriptFamily = ScriptFamily.TAMIL,
            ocrEngineType = OcrEngineType.TESSERACT_PACK,
            tessCode = "tam",
            tessSizeMb = 3.5f,
            mlkitTranslateCode = TranslateLanguage.TAMIL,
            ocrAccuracy = AccuracyRating.AVERAGE,
            speechAccuracy = AccuracyRating.LOW,
            isIndianLanguage = true,
            supportsRomanScript = true
        ),
        LanguageSpec(
            code = "kn",
            englishName = "Kannada",
            nativeName = "ಕನ್ನಡ",
            scriptFamily = ScriptFamily.KANNADA,
            ocrEngineType = OcrEngineType.TESSERACT_PACK,
            tessCode = "kan",
            tessSizeMb = 3.8f,
            mlkitTranslateCode = null, // Not in ML Kit Translate offline models
            translateSizeMb = 0f,
            ocrAccuracy = AccuracyRating.AVERAGE,
            speechAccuracy = AccuracyRating.LOW,
            isIndianLanguage = true,
            supportsRomanScript = true
        ),
        LanguageSpec(
            code = "ml",
            englishName = "Malayalam",
            nativeName = "മലയാളം",
            scriptFamily = ScriptFamily.MALAYALAM,
            ocrEngineType = OcrEngineType.TESSERACT_PACK,
            tessCode = "mal",
            tessSizeMb = 4.1f,
            mlkitTranslateCode = null, // Not in ML Kit Translate offline models
            translateSizeMb = 0f,
            ocrAccuracy = AccuracyRating.AVERAGE,
            speechAccuracy = AccuracyRating.LOW,
            isIndianLanguage = true,
            supportsRomanScript = true
        ),
        LanguageSpec(
            code = "bn",
            englishName = "Bengali",
            nativeName = "বাংলা",
            scriptFamily = ScriptFamily.BENGALI,
            ocrEngineType = OcrEngineType.TESSERACT_PACK,
            tessCode = "ben",
            tessSizeMb = 2.9f,
            mlkitTranslateCode = TranslateLanguage.BENGALI,
            ocrAccuracy = AccuracyRating.AVERAGE,
            speechAccuracy = AccuracyRating.LOW,
            isIndianLanguage = true,
            supportsRomanScript = true
        ),
        LanguageSpec(
            code = "gu",
            englishName = "Gujarati",
            nativeName = "ગુજરાતી",
            scriptFamily = ScriptFamily.GUJARATI,
            ocrEngineType = OcrEngineType.TESSERACT_PACK,
            tessCode = "guj",
            tessSizeMb = 2.6f,
            mlkitTranslateCode = TranslateLanguage.GUJARATI,
            voskModelId = "vosk-model-small-gu-0.42",
            voskModelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-gu-0.42.zip",
            voskSizeMb = 100f,
            ocrAccuracy = AccuracyRating.AVERAGE,
            speechAccuracy = AccuracyRating.AVERAGE,
            isIndianLanguage = true,
            supportsRomanScript = true
        ),
        LanguageSpec(
            code = "pa",
            englishName = "Punjabi",
            nativeName = "ਪੰਜਾਬੀ",
            scriptFamily = ScriptFamily.GURMUKHI,
            ocrEngineType = OcrEngineType.TESSERACT_PACK,
            tessCode = "pan",
            tessSizeMb = 2.2f,
            mlkitTranslateCode = null, // Not in ML Kit Translate offline models
            translateSizeMb = 0f,
            ocrAccuracy = AccuracyRating.AVERAGE,
            speechAccuracy = AccuracyRating.LOW,
            isIndianLanguage = true,
            supportsRomanScript = true
        ),
        LanguageSpec(
            code = "ur",
            englishName = "Urdu",
            nativeName = "اردو",
            scriptFamily = ScriptFamily.ARABIC_URDU,
            ocrEngineType = OcrEngineType.TESSERACT_PACK,
            tessCode = "urd",
            tessSizeMb = 2.5f,
            mlkitTranslateCode = TranslateLanguage.URDU,
            ocrAccuracy = AccuracyRating.LOW,
            speechAccuracy = AccuracyRating.LOW,
            isIndianLanguage = true,
            supportsRomanScript = true
        ),
        LanguageSpec(
            code = "zh",
            englishName = "Chinese",
            nativeName = "中文",
            scriptFamily = ScriptFamily.CHINESE,
            ocrEngineType = OcrEngineType.MLKIT_BUNDLED,
            mlkitTranslateCode = TranslateLanguage.CHINESE,
            voskModelId = "vosk-model-small-cn-0.22",
            voskModelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip",
            voskSizeMb = 42f,
            ocrAccuracy = AccuracyRating.GOOD,
            speechAccuracy = AccuracyRating.GOOD
        ),
        LanguageSpec(
            code = "ja",
            englishName = "Japanese",
            nativeName = "日本語",
            scriptFamily = ScriptFamily.JAPANESE,
            ocrEngineType = OcrEngineType.MLKIT_BUNDLED,
            mlkitTranslateCode = TranslateLanguage.JAPANESE,
            voskModelId = "vosk-model-small-ja-0.22",
            voskModelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip",
            voskSizeMb = 48f,
            ocrAccuracy = AccuracyRating.GOOD,
            speechAccuracy = AccuracyRating.GOOD
        ),
        LanguageSpec(
            code = "ko",
            englishName = "Korean",
            nativeName = "한국어",
            scriptFamily = ScriptFamily.KOREAN,
            ocrEngineType = OcrEngineType.MLKIT_BUNDLED,
            mlkitTranslateCode = TranslateLanguage.KOREAN,
            voskModelId = "vosk-model-small-ko-0.22",
            voskModelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-ko-0.22.zip",
            voskSizeMb = 82f,
            ocrAccuracy = AccuracyRating.GOOD,
            speechAccuracy = AccuracyRating.AVERAGE
        ),
        LanguageSpec(
            code = "ar",
            englishName = "Arabic",
            nativeName = "العربية",
            scriptFamily = ScriptFamily.ARABIC_URDU,
            ocrEngineType = OcrEngineType.TESSERACT_PACK,
            tessCode = "ara",
            tessSizeMb = 2.8f,
            mlkitTranslateCode = TranslateLanguage.ARABIC,
            voskModelId = "vosk-model-ar-mgb2-0.4",
            voskModelUrl = "https://alphacephei.com/vosk/models/vosk-model-ar-mgb2-0.4.zip",
            voskSizeMb = 318f,
            ocrAccuracy = AccuracyRating.AVERAGE,
            speechAccuracy = AccuracyRating.AVERAGE
        ),
        LanguageSpec(
            code = "es",
            englishName = "Spanish",
            nativeName = "Español",
            scriptFamily = ScriptFamily.LATIN,
            ocrEngineType = OcrEngineType.MLKIT_BUNDLED,
            mlkitTranslateCode = TranslateLanguage.SPANISH,
            voskModelId = "vosk-model-small-es-0.42",
            voskModelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip",
            voskSizeMb = 39f,
            ocrAccuracy = AccuracyRating.GOOD,
            speechAccuracy = AccuracyRating.GOOD
        ),
        LanguageSpec(
            code = "fr",
            englishName = "French",
            nativeName = "Français",
            scriptFamily = ScriptFamily.LATIN,
            ocrEngineType = OcrEngineType.MLKIT_BUNDLED,
            mlkitTranslateCode = TranslateLanguage.FRENCH,
            voskModelId = "vosk-model-small-fr-0.22",
            voskModelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip",
            voskSizeMb = 41f,
            ocrAccuracy = AccuracyRating.GOOD,
            speechAccuracy = AccuracyRating.GOOD
        ),
        LanguageSpec(
            code = "de",
            englishName = "German",
            nativeName = "Deutsch",
            scriptFamily = ScriptFamily.LATIN,
            ocrEngineType = OcrEngineType.MLKIT_BUNDLED,
            mlkitTranslateCode = TranslateLanguage.GERMAN,
            voskModelId = "vosk-model-small-de-0.15",
            voskModelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip",
            voskSizeMb = 45f,
            ocrAccuracy = AccuracyRating.GOOD,
            speechAccuracy = AccuracyRating.GOOD
        ),
        LanguageSpec(
            code = "ru",
            englishName = "Russian",
            nativeName = "Русский",
            scriptFamily = ScriptFamily.CYRILLIC,
            ocrEngineType = OcrEngineType.TESSERACT_PACK,
            tessCode = "rus",
            tessSizeMb = 4.6f,
            mlkitTranslateCode = TranslateLanguage.RUSSIAN,
            voskModelId = "vosk-model-small-ru-0.22",
            voskModelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip",
            voskSizeMb = 45f,
            ocrAccuracy = AccuracyRating.GOOD,
            speechAccuracy = AccuracyRating.GOOD
        ),
        LanguageSpec(
            code = "pt",
            englishName = "Portuguese",
            nativeName = "Português",
            scriptFamily = ScriptFamily.LATIN,
            ocrEngineType = OcrEngineType.MLKIT_BUNDLED,
            mlkitTranslateCode = TranslateLanguage.PORTUGUESE,
            voskModelId = "vosk-model-small-pt-0.3",
            voskModelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip",
            voskSizeMb = 31f,
            ocrAccuracy = AccuracyRating.GOOD,
            speechAccuracy = AccuracyRating.GOOD
        ),
        LanguageSpec(
            code = "it",
            englishName = "Italian",
            nativeName = "Italiano",
            scriptFamily = ScriptFamily.LATIN,
            ocrEngineType = OcrEngineType.MLKIT_BUNDLED,
            mlkitTranslateCode = TranslateLanguage.ITALIAN,
            voskModelId = "vosk-model-small-it-0.22",
            voskModelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-it-0.22.zip",
            voskSizeMb = 48f,
            ocrAccuracy = AccuracyRating.GOOD,
            speechAccuracy = AccuracyRating.GOOD
        ),
        LanguageSpec(
            code = "id",
            englishName = "Indonesian",
            nativeName = "Bahasa Indonesia",
            scriptFamily = ScriptFamily.LATIN,
            ocrEngineType = OcrEngineType.MLKIT_BUNDLED,
            mlkitTranslateCode = TranslateLanguage.INDONESIAN,
            ocrAccuracy = AccuracyRating.GOOD,
            speechAccuracy = AccuracyRating.LOW
        ),
        LanguageSpec(
            code = "tr",
            englishName = "Turkish",
            nativeName = "Türkçe",
            scriptFamily = ScriptFamily.LATIN,
            ocrEngineType = OcrEngineType.MLKIT_BUNDLED,
            mlkitTranslateCode = TranslateLanguage.TURKISH,
            voskModelId = "vosk-model-small-tr-0.3",
            voskModelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-tr-0.3.zip",
            voskSizeMb = 35f,
            ocrAccuracy = AccuracyRating.GOOD,
            speechAccuracy = AccuracyRating.AVERAGE
        ),
        LanguageSpec(
            code = "vi",
            englishName = "Vietnamese",
            nativeName = "Tiếng Việt",
            scriptFamily = ScriptFamily.LATIN,
            ocrEngineType = OcrEngineType.MLKIT_BUNDLED,
            mlkitTranslateCode = TranslateLanguage.VIETNAMESE,
            voskModelId = "vosk-model-small-vn-0.4",
            voskModelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-vn-0.4.zip",
            voskSizeMb = 32f,
            ocrAccuracy = AccuracyRating.AVERAGE,
            speechAccuracy = AccuracyRating.AVERAGE
        ),
        LanguageSpec(
            code = "th",
            englishName = "Thai",
            nativeName = "ไทย",
            scriptFamily = ScriptFamily.THAI,
            ocrEngineType = OcrEngineType.TESSERACT_PACK,
            tessCode = "tha",
            tessSizeMb = 2.4f,
            mlkitTranslateCode = TranslateLanguage.THAI,
            ocrAccuracy = AccuracyRating.AVERAGE,
            speechAccuracy = AccuracyRating.LOW
        )
    )

    fun findByCode(code: String): LanguageSpec? =
        allLanguages.firstOrNull { it.code.equals(code, ignoreCase = true) }

    fun displayName(code: String): String {
        if (code == AUTO_DETECT_CODE) return "Auto Detect"
        return findByCode(code)?.englishName ?: code.uppercase()
    }

    fun isDirectPairOffline(sourceCode: String, targetCode: String): Boolean {
        val src = findByCode(sourceCode) ?: return false
        val tgt = findByCode(targetCode) ?: return false
        return src.supportsOfflineTranslation && tgt.supportsOfflineTranslation
    }

    /**
     * ML Kit translates directly between English and every supported language,
     * and pivots through English internally for non-English <-> non-English pairs.
     */
    fun usesEnglishPivot(sourceCode: String, targetCode: String): Boolean {
        if (sourceCode == AUTO_DETECT_CODE) return false
        return sourceCode != "en" && targetCode != "en" && isDirectPairOffline(sourceCode, targetCode)
    }
}
