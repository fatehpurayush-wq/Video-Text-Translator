package com.example

import com.example.domain.model.LanguageCatalog
import com.example.engine.FrameConsolidator
import com.example.engine.RawOcrObservation
import com.example.engine.RomanTransliterator
import com.example.engine.TimeFormatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun frameConsolidator_mergesRepeatedFramesAndPicksConsensus() {
        val observations = listOf(
            RawOcrObservation(0L, "Hello world", "en", 0.90f, emptySet(), null),
            RawOcrObservation(1000L, "Hello w0rld", "en", 0.60f, setOf(1), null),
            RawOcrObservation(2000L, "Hello world", "en", 0.95f, emptySet(), null),
            RawOcrObservation(5000L, "Next line here", "en", 0.92f, emptySet(), null)
        )
        val result = FrameConsolidator.consolidate(observations, frameIntervalMs = 1000L, minTextLength = 3)
        assertEquals(2, result.size)
        assertEquals("Hello world", result[0].originalText)
        assertEquals(0L, result[0].startTimeMs)
        assertEquals(3000L, result[0].endTimeMs)
        assertEquals("Next line here", result[1].originalText)
    }

    @Test
    fun romanTransliterator_convertsHindiDevanagariToReadableRoman() {
        val roman = RomanTransliterator.transliterate("नमस्ते भारत", "hi")
        assertTrue(roman.contains("namaste"))
        assertTrue(roman.contains("bhaarat"))
    }

    @Test
    fun timeFormatters_formatsAndParsesCorrectly() {
        assertEquals("01:05.5", TimeFormatters.formatShort(65_500L))
        assertEquals("00:01:05,500", TimeFormatters.formatSrt(65_500L))
        assertEquals("00:01:05.500", TimeFormatters.formatVtt(65_500L))
        assertEquals(65_500L, TimeFormatters.parseShortTimeToMs("01:05.5"))
    }

    @Test
    fun languageCatalog_reportsPivotAndOfflineAvailabilityAccurately() {
        assertTrue(LanguageCatalog.usesEnglishPivot("hi", "es"))
        assertFalse(LanguageCatalog.usesEnglishPivot("en", "hi"))
        assertFalse(LanguageCatalog.isDirectPairOffline("en", "kn"))
        assertTrue(LanguageCatalog.isDirectPairOffline("en", "hi"))
    }
}
