package com.example.engine

/**
 * Offline algorithmic Unicode-to-Roman transliterator for Indian scripts:
 * Devanagari (Hindi/Marathi), Bengali, Gujarati, Gurmukhi (Punjabi),
 * Tamil, Telugu, Kannada, Malayalam, and Urdu/Arabic.
 */
object RomanTransliterator {

    fun isIndianOrRtlScript(text: String): Boolean {
        return text.any { ch ->
            val code = ch.code
            code in 0x0900..0x0D7F || code in 0x0600..0x06FF
        }
    }

    fun transliterate(text: String, langCode: String): String {
        if (text.isBlank()) return ""
        return when (langCode.lowercase()) {
            "hi", "mr" -> transliterateIndicBlock(text, 0x0900)
            "bn" -> transliterateIndicBlock(text, 0x0980)
            "pa" -> transliterateIndicBlock(text, 0x0A00)
            "gu" -> transliterateIndicBlock(text, 0x0A80)
            "ta" -> transliterateIndicBlock(text, 0x0B80)
            "te" -> transliterateIndicBlock(text, 0x0C00)
            "kn" -> transliterateIndicBlock(text, 0x0C80)
            "ml" -> transliterateIndicBlock(text, 0x0D00)
            "ur", "ar" -> transliterateUrduArabic(text)
            else -> if (isIndianOrRtlScript(text)) transliterateAuto(text) else text
        }.replace(Regex("\\s+"), " ").trim()
    }

    private fun transliterateAuto(text: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val code = text[i].code
            when (code) {
                in 0x0900..0x097F -> {
                    val end = takeBlockEnd(text, i, 0x0900..0x097F)
                    sb.append(transliterateIndicBlock(text.substring(i, end), 0x0900))
                    i = end
                }
                in 0x0980..0x09FF -> {
                    val end = takeBlockEnd(text, i, 0x0980..0x09FF)
                    sb.append(transliterateIndicBlock(text.substring(i, end), 0x0980))
                    i = end
                }
                in 0x0A00..0x0A7F -> {
                    val end = takeBlockEnd(text, i, 0x0A00..0x0A7F)
                    sb.append(transliterateIndicBlock(text.substring(i, end), 0x0A00))
                    i = end
                }
                in 0x0A80..0x0AFF -> {
                    val end = takeBlockEnd(text, i, 0x0A80..0x0AFF)
                    sb.append(transliterateIndicBlock(text.substring(i, end), 0x0A80))
                    i = end
                }
                in 0x0B80..0x0BFF -> {
                    val end = takeBlockEnd(text, i, 0x0B80..0x0BFF)
                    sb.append(transliterateIndicBlock(text.substring(i, end), 0x0B80))
                    i = end
                }
                in 0x0C00..0x0C7F -> {
                    val end = takeBlockEnd(text, i, 0x0C00..0x0C7F)
                    sb.append(transliterateIndicBlock(text.substring(i, end), 0x0C00))
                    i = end
                }
                in 0x0C80..0x0CFF -> {
                    val end = takeBlockEnd(text, i, 0x0C80..0x0CFF)
                    sb.append(transliterateIndicBlock(text.substring(i, end), 0x0C80))
                    i = end
                }
                in 0x0D00..0x0D7F -> {
                    val end = takeBlockEnd(text, i, 0x0D00..0x0D7F)
                    sb.append(transliterateIndicBlock(text.substring(i, end), 0x0D00))
                    i = end
                }
                in 0x0600..0x06FF -> {
                    val end = takeBlockEnd(text, i, 0x0600..0x06FF)
                    sb.append(transliterateUrduArabic(text.substring(i, end)))
                    i = end
                }
                else -> {
                    sb.append(text[i])
                    i++
                }
            }
        }
        return sb.toString()
    }

    private fun takeBlockEnd(text: String, start: Int, range: IntRange): Int {
        var idx = start
        while (idx < text.length && text[idx].code in range) idx++
        return idx
    }

    /**
     * Unified Indic Unicode block transliterator (all Indic blocks share parallel offsets 0x00..0x7F).
     */
    private fun transliterateIndicBlock(text: String, base: Int): String {
        val sb = StringBuilder()
        val len = text.length
        var i = 0
        while (i < len) {
            val ch = text[i]
            val offset = ch.code - base
            if (offset !in 0x00..0x7F) {
                sb.append(ch)
                i++
                continue
            }

            // Punctuation / Danda / Digits
            when (offset) {
                0x64 -> { sb.append('.'); i++; continue }
                0x65 -> { sb.append(".."); i++; continue }
                in 0x66..0x6F -> { sb.append(('0'.code + (offset - 0x66)).toChar()); i++; continue }
                0x01, 0x02 -> { sb.append("n"); i++; continue } // Chandrabindu / Anusvara
                0x03 -> { sb.append("h"); i++; continue } // Visarga
                0x3C -> { i++; continue } // Nukta handled with preceding consonant or ignored
            }

            // Independent Vowels (0x05..0x14)
            val indepVowel = independentVowel(offset)
            if (indepVowel != null) {
                sb.append(indepVowel)
                i++
                continue
            }

            // Consonants (0x15..0x39)
            val consonant = consonantBase(offset, base)
            if (consonant != null) {
                var baseCons = consonant
                var nextIdx = i + 1
                // Check Nukta (0x3C)
                if (nextIdx < len && text[nextIdx].code - base == 0x3C) {
                    baseCons = applyNukta(offset) ?: baseCons
                    nextIdx++
                }

                // Lookahead for matra or halant/virama (0x4D)
                if (nextIdx < len) {
                    val nextOffset = text[nextIdx].code - base
                    val matra = dependentMatra(nextOffset)
                    if (nextOffset == 0x4D) {
                        // Virama suppresses inherent 'a'
                        sb.append(baseCons)
                        i = nextIdx + 1
                        continue
                    } else if (matra != null) {
                        sb.append(baseCons).append(matra)
                        i = nextIdx + 1
                        continue
                    }
                }

                // Schwa deletion heuristic at word end (for Hindi/Marathi/Gujarati/Punjabi)
                val isWordEnd = nextIdx >= len || !isIndicLetter(text[nextIdx], base)
                val schwa = if (isWordEnd && (base == 0x0900 || base == 0x0A00 || base == 0x0A80)) "" else "a"
                sb.append(baseCons).append(schwa)
                i = nextIdx
                continue
            }

            // Standalone matra fallback
            val matra = dependentMatra(offset)
            if (matra != null) {
                sb.append(matra)
                i++
                continue
            }

            i++
        }
        return sb.toString()
    }

    private fun isIndicLetter(ch: Char, base: Int): Boolean {
        val off = ch.code - base
        return off in 0x01..0x5F
    }

    private fun independentVowel(offset: Int): String? = when (offset) {
        0x05 -> "a"
        0x06 -> "aa"
        0x07 -> "i"
        0x08 -> "ee"
        0x09 -> "u"
        0x0A -> "oo"
        0x0B -> "ri"
        0x0E, 0x0F, 0x0D -> "e"
        0x10 -> "ai"
        0x12, 0x13, 0x11 -> "o"
        0x14 -> "au"
        else -> null
    }

    private fun dependentMatra(offset: Int): String? = when (offset) {
        0x3E -> "aa"
        0x3F -> "i"
        0x40 -> "ee"
        0x41 -> "u"
        0x42 -> "oo"
        0x43 -> "ri"
        0x46, 0x47, 0x45 -> "e"
        0x48 -> "ai"
        0x4A, 0x4B, 0x49 -> "o"
        0x4C -> "au"
        else -> null
    }

    private fun consonantBase(offset: Int, base: Int): String? = when (offset) {
        0x15 -> "k"
        0x16 -> "kh"
        0x17 -> "g"
        0x18 -> "gh"
        0x19 -> "ng"
        0x1A -> "ch"
        0x1B -> "chh"
        0x1C -> "j"
        0x1D -> "jh"
        0x1E -> "ny"
        0x1F -> "t"
        0x20 -> "th"
        0x21 -> "d"
        0x22 -> "dh"
        0x23 -> "n"
        0x24 -> "t"
        0x25 -> "th"
        0x26 -> "d"
        0x27 -> "dh"
        0x28 -> "n"
        0x29 -> "n"
        0x2A -> "p"
        0x2B -> "ph"
        0x2C -> if (base == 0x0980) "b" else "b"
        0x2D -> "bh"
        0x2E -> "m"
        0x2F -> "y"
        0x30 -> "r"
        0x31 -> "r"
        0x32 -> "l"
        0x33 -> "l"
        0x34 -> "zh"
        0x35 -> if (base == 0x0980) "b" else "v"
        0x36 -> "sh"
        0x37 -> "sh"
        0x38 -> "s"
        0x39 -> "h"
        0x58 -> "q"
        0x59 -> "kh"
        0x5A -> "gh"
        0x5B -> "z"
        0x5C -> "r"
        0x5D -> "rh"
        0x5E -> "f"
        0x5F -> "y"
        else -> null
    }

    private fun applyNukta(offset: Int): String? = when (offset) {
        0x15 -> "q"
        0x16 -> "kh"
        0x17 -> "gh"
        0x1C -> "z"
        0x21 -> "r"
        0x22 -> "rh"
        0x2B -> "f"
        else -> null
    }

    private fun transliterateUrduArabic(text: String): String {
        val map = mapOf(
            'ا' to "a", 'آ' to "aa", 'ب' to "b", 'پ' to "p", 'ت' to "t", 'ٹ' to "t",
            'ث' to "s", 'ج' to "j", 'چ' to "ch", 'ح' to "h", 'خ' to "kh", 'د' to "d",
            'ڈ' to "d", 'ذ' to "z", 'ر' to "r", 'ڑ' to "r", 'ز' to "z", 'ژ' to "zh",
            'س' to "s", 'ش' to "sh", 'ص' to "s", 'ض' to "z", 'ط' to "t", 'ظ' to "z",
            'ع' to "a", 'غ' to "gh", 'ف' to "f", 'ق' to "q", 'ک' to "k", 'ك' to "k",
            'گ' to "g", 'ل' to "l", 'م' to "m", 'ن' to "n", 'ں' to "n", 'و' to "o",
            'ہ' to "h", 'ھ' to "h", 'ی' to "i", 'ي' to "i", 'ے' to "e", 'ئ' to "y",
            'ء' to "'", '؟' to "?", '،' to ",", '۔' to "."
        )
        val sb = StringBuilder()
        for (ch in text) {
            when (ch) {
                in map -> sb.append(map[ch])
                in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
