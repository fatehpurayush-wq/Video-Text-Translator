package com.example.engine

import com.example.domain.model.NormalizedRect
import kotlin.math.max
import kotlin.math.min

data class RawOcrObservation(
    val timestampMs: Long,
    val text: String,
    val detectedLangCode: String,
    val confidence: Float,
    val unsureWordIndices: Set<Int>,
    val boundingBox: NormalizedRect?
)

data class ConsolidatedSegment(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val originalText: String,
    val detectedLangCode: String,
    val sourceType: String,
    val confidence: Float,
    val unsureWordIndices: Set<Int>,
    val boundingBox: NormalizedRect?
)

/**
 * Merges consecutive video frames containing the same subtitle/on-screen text into a single
 * reliable timed segment by voting on the highest-confidence observation and extending timestamps.
 */
object FrameConsolidator {

    fun consolidate(
        observations: List<RawOcrObservation>,
        frameIntervalMs: Long,
        minTextLength: Int
    ): List<ConsolidatedSegment> {
        val valid = observations
            .map { it.copy(text = normalizeWhitespace(it.text)) }
            .filter { it.text.length >= minTextLength }
            .sortedBy { it.timestampMs }

        if (valid.isEmpty()) return emptyList()

        val groups = mutableListOf<MutableList<RawOcrObservation>>()
        var currentGroup = mutableListOf(valid.first())

        for (i in 1 until valid.size) {
            val prev = currentGroup.last()
            val curr = valid[i]
            val gap = curr.timestampMs - prev.timestampMs
            val similarity = textSimilarity(prev.text, curr.text)

            // Same text appearing across consecutive frames (allow skipping 1 noisy frame)
            if (gap <= frameIntervalMs * 2.5 && similarity >= 0.78f) {
                currentGroup.add(curr)
            } else {
                groups.add(currentGroup)
                currentGroup = mutableListOf(curr)
            }
        }
        groups.add(currentGroup)

        val minDurationMs = max(700L, frameIntervalMs)
        return groups.map { group ->
            // Consensus: pick text with highest frequency * confidence score
            val bestObs = group
                .groupBy { it.text }
                .maxByOrNull { (_, list) ->
                    list.size * 1.2f + list.maxOf { it.confidence }
                }?.value?.maxByOrNull { it.confidence } ?: group.first()

            val startMs = group.first().timestampMs
            val endMs = max(startMs + minDurationMs, group.last().timestampMs + frameIntervalMs)
            val avgConf = group.map { it.confidence }.average().toFloat().coerceIn(0f, 1f)

            ConsolidatedSegment(
                startTimeMs = startMs,
                endTimeMs = endMs,
                originalText = bestObs.text,
                detectedLangCode = bestObs.detectedLangCode,
                sourceType = "OCR",
                confidence = avgConf,
                unsureWordIndices = bestObs.unsureWordIndices,
                boundingBox = bestObs.boundingBox
            )
        }
    }

    fun normalizeWhitespace(text: String): String =
        text.replace(Regex("[\\r\\n]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun textSimilarity(a: String, b: String): Float {
        val s1 = a.lowercase()
        val s2 = b.lowercase()
        if (s1 == s2) return 1f
        val maxLen = max(s1.length, s2.length)
        if (maxLen == 0) return 1f
        val dist = levenshtein(s1, s2)
        return (1f - dist.toFloat() / maxLen).coerceIn(0f, 1f)
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }
}
