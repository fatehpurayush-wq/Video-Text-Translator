package com.example.engine

import com.example.data.local.GlossaryRuleEntity
import com.example.domain.model.LanguageCatalog
import com.example.domain.model.TranslationStyle
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class TranslationOutcome(
    val translatedText: String,
    val romanText: String?,
    val usedPivotEnglish: Boolean,
    val errorHinglish: String? = null
)

class TranslationEngine {

    private val modelManager = RemoteModelManager.getInstance()

    suspend fun isTranslateModelDownloaded(langCode: String): Boolean {
        val spec = LanguageCatalog.findByCode(langCode) ?: return false
        val mlCode = spec.mlkitTranslateCode ?: return false
        return try {
            val model = TranslateRemoteModel.Builder(mlCode).build()
            modelManager.isModelDownloaded(model).await()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Translates a list of segments in context-aware sentence groups, preserving names/brands/numbers
     * and applying Glossary rules ("Meri words list") and TranslationStyle.
     */
    suspend fun translateBatch(
        texts: List<String>,
        sourceLangCode: String,
        targetLangCode: String,
        style: TranslationStyle,
        includeRomanScript: Boolean,
        glossaryRules: List<GlossaryRuleEntity>
    ): List<TranslationOutcome> = withContext(Dispatchers.Default) {
        if (texts.isEmpty()) return@withContext emptyList()

        if (sourceLangCode.equals(targetLangCode, ignoreCase = true)) {
            return@withContext texts.map { raw ->
                val styled = applyGlossaryPost(raw, glossaryRules, targetLangCode)
                val roman = if (includeRomanScript && LanguageCatalog.findByCode(targetLangCode)?.supportsRomanScript == true) {
                    RomanTransliterator.transliterate(styled, targetLangCode)
                } else null
                TranslationOutcome(styled, roman, usedPivotEnglish = false)
            }
        }

        val srcSpec = LanguageCatalog.findByCode(sourceLangCode)
        val tgtSpec = LanguageCatalog.findByCode(targetLangCode)
        val srcMl = srcSpec?.mlkitTranslateCode
        val tgtMl = tgtSpec?.mlkitTranslateCode

        if (srcMl == null || tgtMl == null) {
            val missingName = if (srcMl == null) (srcSpec?.englishName ?: sourceLangCode) else (tgtSpec?.englishName ?: targetLangCode)
            val err = "Yeh language pair ($missingName) offline translation ke liye uplabdh nahi hai."
            return@withContext texts.map { TranslationOutcome("", null, false, err) }
        }

        val srcReady = isTranslateModelDownloaded(sourceLangCode)
        val tgtReady = isTranslateModelDownloaded(targetLangCode)
        if (!srcReady || !tgtReady) {
            val missing = buildList {
                if (!srcReady) add(srcSpec.englishName)
                if (!tgtReady) add(tgtSpec.englishName)
            }.joinToString(" aur ")
            val err = "$missing ka Translation Pack download nahi hua hai. Language Packs screen se download karein."
            return@withContext texts.map { TranslationOutcome("", null, false, err) }
        }

        val usesPivot = LanguageCatalog.usesEnglishPivot(sourceLangCode, targetLangCode)
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(srcMl)
            .setTargetLanguage(tgtMl)
            .build()
        val translator = Translation.getClient(options)

        try {
            val results = mutableListOf<TranslationOutcome>()
            val applicableRules = glossaryRules.filter { it.targetLangCode == "*" || it.targetLangCode.equals(targetLangCode, ignoreCase = true) }

            // Context grouping: pair incomplete sentence lines with the next line to preserve story flow
            var idx = 0
            while (idx < texts.size) {
                val curr = texts[idx]
                val endsSentence = curr.trimEnd().let { it.endsWith(".") || it.endsWith("?") || it.endsWith("!") || it.endsWith("।") }

                if (!endsSentence && idx + 1 < texts.size && style != TranslationStyle.LITERAL) {
                    val next = texts[idx + 1]
                    val combined = "$curr ‖ $next"
                    val protected = protectGlossaryTerms(combined, applicableRules)
                    val translatedCombined = translator.translate(protected.maskedText).await()
                    val restoredCombined = restoreGlossaryTerms(translatedCombined, protected.placeholders)

                    val parts = restoredCombined.split("‖")
                    val part1: String
                    val part2: String
                    if (parts.size >= 2) {
                        part1 = parts[0].trim()
                        part2 = parts.drop(1).joinToString(" ").trim()
                    } else {
                        // Translate individually if separator collapsed, using combined hint
                        val p1 = protectGlossaryTerms(curr, applicableRules)
                        val p2 = protectGlossaryTerms(next, applicableRules)
                        part1 = restoreGlossaryTerms(translator.translate(p1.maskedText).await(), p1.placeholders)
                        part2 = restoreGlossaryTerms(translator.translate(p2.maskedText).await(), p2.placeholders)
                    }

                    results.add(finalizeOutcome(part1, targetLangCode, style, includeRomanScript, usesPivot, applicableRules))
                    results.add(finalizeOutcome(part2, targetLangCode, style, includeRomanScript, usesPivot, applicableRules))
                    idx += 2
                } else {
                    val protected = protectGlossaryTerms(curr, applicableRules)
                    val rawTrans = translator.translate(protected.maskedText).await()
                    val restored = restoreGlossaryTerms(rawTrans, protected.placeholders)
                    results.add(finalizeOutcome(restored, targetLangCode, style, includeRomanScript, usesPivot, applicableRules))
                    idx += 1
                }
            }
            results
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Offline translation mein error aaya"
            texts.map { TranslationOutcome("", null, usesPivot, msg) }
        } finally {
            runCatching { translator.close() }
        }
    }

    suspend fun translateSingle(
        text: String,
        sourceLangCode: String,
        targetLangCode: String,
        style: TranslationStyle,
        includeRomanScript: Boolean,
        glossaryRules: List<GlossaryRuleEntity>
    ): TranslationOutcome {
        return translateBatch(
            texts = listOf(text),
            sourceLangCode = sourceLangCode,
            targetLangCode = targetLangCode,
            style = style,
            includeRomanScript = includeRomanScript,
            glossaryRules = glossaryRules
        ).firstOrNull() ?: TranslationOutcome("", null, false, "Translation khali mila")
    }

    private fun finalizeOutcome(
        rawTranslated: String,
        targetLangCode: String,
        style: TranslationStyle,
        includeRomanScript: Boolean,
        usesPivot: Boolean,
        rules: List<GlossaryRuleEntity>
    ): TranslationOutcome {
        val withRules = applyGlossaryPost(rawTranslated, rules, targetLangCode)
        val styled = applyStyleAdaptation(withRules, targetLangCode, style)
        val roman = if (includeRomanScript && LanguageCatalog.findByCode(targetLangCode)?.supportsRomanScript == true) {
            RomanTransliterator.transliterate(styled, targetLangCode)
        } else null
        return TranslationOutcome(styled, roman, usesPivot, null)
    }

    private data class ProtectedText(
        val maskedText: String,
        val placeholders: Map<String, String>
    )

    private fun protectGlossaryTerms(
        text: String,
        rules: List<GlossaryRuleEntity>
    ): ProtectedText {
        var working = text
        val map = LinkedHashMap<String, String>()
        var tokenIndex = 0

        // Protect explicit glossary terms (longest first)
        for (rule in rules.sortedByDescending { it.sourceTerm.length }) {
            if (rule.sourceTerm.isBlank()) continue
            val regex = Regex("\\b${Regex.escape(rule.sourceTerm)}\\b", RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(working)) {
                val token = "GLOSS$tokenIndex"
                tokenIndex++
                map[token] = rule.targetTerm
                working = regex.replace(working, token)
            }
        }
        return ProtectedText(working, map)
    }

    private fun restoreGlossaryTerms(
        translated: String,
        placeholders: Map<String, String>
    ): String {
        var working = translated
        for ((token, replacement) in placeholders) {
            working = working.replace(token, replacement, ignoreCase = true)
        }
        return working.replace(Regex("\\s+"), " ").trim()
    }

    private fun applyGlossaryPost(
        text: String,
        rules: List<GlossaryRuleEntity>,
        targetLangCode: String
    ): String {
        var working = text
        val applicable = rules.filter { it.targetLangCode == "*" || it.targetLangCode.equals(targetLangCode, ignoreCase = true) }
        for (rule in applicable.sortedByDescending { it.sourceTerm.length }) {
            if (rule.sourceTerm.isBlank()) continue
            val regex = Regex("\\b${Regex.escape(rule.sourceTerm)}\\b", RegexOption.IGNORE_CASE)
            working = regex.replace(working, rule.targetTerm)
        }
        return working
    }

    private fun applyStyleAdaptation(
        text: String,
        targetLangCode: String,
        style: TranslationStyle
    ): String {
        if (text.isBlank()) return text
        return when (style) {
            TranslationStyle.SIMPLE, TranslationStyle.LITERAL -> text.trim()
            TranslationStyle.FORMAL -> {
                if (targetLangCode == "hi") {
                    text.replace("तुम ", "आप ")
                        .replace("तुम्हारा", "आपका")
                        .replace("तुम्हें", "आपको")
                        .replace("करो", "कीजिए")
                        .trim()
                } else if (targetLangCode == "en") {
                    text.replace("gonna", "going to")
                        .replace("wanna", "want to")
                        .replace("can't", "cannot")
                        .trim()
                } else text.trim()
            }
            TranslationStyle.CASUAL -> {
                if (targetLangCode == "hi") {
                    text.replace("कृपया ", "")
                        .replace("आपका", "तुम्हारा")
                        .replace("कीजिए", "करो")
                        .trim()
                } else text.trim()
            }
        }
    }
}
