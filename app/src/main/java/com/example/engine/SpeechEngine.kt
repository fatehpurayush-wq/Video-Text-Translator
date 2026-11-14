package com.example.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

class SpeechEngine(private val context: Context) {

    fun voskModelsDir(): File = File(context.filesDir, "vosk_models").apply { mkdirs() }

    fun getModelDirForLang(langCode: String): File? {
        val dir = File(voskModelsDir(), langCode.lowercase())
        if (!dir.exists()) return null
        // Vosk model directory contains conf/ or am/
        val directValid = File(dir, "conf").exists() || File(dir, "am").exists()
        if (directValid) return dir
        val sub = dir.listFiles()?.firstOrNull { it.isDirectory && (File(it, "conf").exists() || File(it, "am").exists()) }
        return sub
    }

    fun isSpeechModelReady(langCode: String): Boolean = getModelDirForLang(langCode) != null

    /**
     * Streams audio from video URI via MediaExtractor + MediaCodec -> 16kHz mono PCM -> Vosk offline Recognizer.
     * Works safely on 20-60 minute videos without buffering full PCM in memory.
     */
    suspend fun transcribeVideoAudio(
        videoUri: Uri,
        langCode: String,
        startFromMs: Long = 0L,
        maxDurationMs: Long? = null,
        onProgress: suspend (processedMs: Long) -> Unit = {}
    ): List<ConsolidatedSegment> = withContext(Dispatchers.Default) {
        val modelDir = getModelDirForLang(langCode)
            ?: throw IllegalStateException("Speech Pack ($langCode) download nahi hai")

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var model: Model? = null
        var recognizer: Recognizer? = null

        try {
            extractor.setDataSource(context, videoUri, null)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = fmt
                    break
                }
            }
            if (audioTrackIndex < 0 || audioFormat == null) {
                return@withContext emptyList()
            }

            extractor.selectTrack(audioTrackIndex)
            if (startFromMs > 0L) {
                extractor.seekTo(startFromMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            val mime = audioFormat.getString(MediaFormat.KEY_MIME)!!
            val sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 1

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            model = Model(modelDir.absolutePath)
            recognizer = Recognizer(model, 16000.0f).apply {
                setWords(true)
            }

            val segments = mutableListOf<ConsolidatedSegment>()
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val targetEndUs = maxDurationMs?.let { (startFromMs + it) * 1000L }
            var lastReportedMs = startFromMs

            while (!outputDone && coroutineContext.isActive) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        val sampleTimeUs = extractor.sampleTime
                        if (sampleSize < 0 || (targetEndUs != null && sampleTimeUs > targetEndUs)) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleSize, sampleTimeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && bufferInfo.size > 0) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)

                        val pcm16k = convertTo16kMonoPcm16(outBuf, sampleRate, channelCount)
                        if (pcm16k.isNotEmpty()) {
                            if (recognizer.acceptWaveForm(pcm16k, pcm16k.size)) {
                                parseVoskResult(recognizer.result, langCode, startFromMs)?.let {
                                    segments.add(it)
                                }
                            }
                        }
                    }

                    val currentMs = bufferInfo.presentationTimeUs / 1000L
                    if (currentMs - lastReportedMs >= 1500L) {
                        lastReportedMs = currentMs
                        onProgress(currentMs)
                    }

                    codec.releaseOutputBuffer(outIdx, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                }
            }

            parseVoskResult(recognizer.finalResult, langCode, startFromMs)?.let {
                segments.add(it)
            }
            segments
        } finally {
            runCatching { recognizer?.close() }
            runCatching { model?.close() }
            runCatching { codec?.stop(); codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun parseVoskResult(
        jsonStr: String?,
        langCode: String,
        timeOffsetMs: Long
    ): ConsolidatedSegment? {
        if (jsonStr.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(jsonStr)
            val text = obj.optString("text", "").trim()
            if (text.isBlank()) return null

            val wordsArr = obj.optJSONArray("result")
            var startMs = timeOffsetMs
            var endMs = timeOffsetMs + 1500L
            val unsure = mutableSetOf<Int>()
            var confSum = 0f
            var count = 0

            if (wordsArr != null && wordsArr.length() > 0) {
                val first = wordsArr.getJSONObject(0)
                val last = wordsArr.getJSONObject(wordsArr.length() - 1)
                startMs = timeOffsetMs + (first.optDouble("start", 0.0) * 1000.0).toLong()
                endMs = max(startMs + 500L, timeOffsetMs + (last.optDouble("end", 1.0) * 1000.0).toLong())
                for (i in 0 until wordsArr.length()) {
                    val w = wordsArr.getJSONObject(i)
                    val c = w.optDouble("conf", 0.85).toFloat()
                    confSum += c
                    count++
                    if (c < 0.65f) unsure.add(i)
                }
            }

            val avgConf = if (count > 0) (confSum / count).coerceIn(0f, 1f) else 0.8f
            ConsolidatedSegment(
                startTimeMs = startMs,
                endTimeMs = endMs,
                originalText = text,
                detectedLangCode = langCode,
                sourceType = "SPEECH",
                confidence = avgConf,
                unsureWordIndices = unsure,
                boundingBox = null
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun convertTo16kMonoPcm16(
        buffer: java.nio.ByteBuffer,
        srcSampleRate: Int,
        channels: Int
    ): ByteArray {
        val shortBuf = buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val totalShorts = shortBuf.remaining()
        if (totalShorts == 0) return ByteArray(0)

        val frames = totalShorts / max(1, channels)
        if (frames == 0) return ByteArray(0)

        val mono = ShortArray(frames)
        for (i in 0 until frames) {
            var sum = 0
            for (c in 0 until channels) {
                sum += shortBuf.get(i * channels + c).toInt()
            }
            mono[i] = (sum / max(1, channels)).toShort()
        }

        val outFrames = if (srcSampleRate == 16000) {
            mono
        } else {
            val ratio = srcSampleRate.toDouble() / 16000.0
            val outLen = max(1, (frames / ratio).roundToInt())
            ShortArray(outLen) { outIdx ->
                val srcIdx = (outIdx * ratio).toInt().coerceIn(0, frames - 1)
                mono[srcIdx]
            }
        }

        val bytes = ByteArray(outFrames.size * 2)
        for (i in outFrames.indices) {
            val s = outFrames[i].toInt()
            bytes[i * 2] = (s and 0xFF).toByte()
            bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return bytes
    }
}
