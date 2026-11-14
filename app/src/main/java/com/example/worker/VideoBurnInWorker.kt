package com.example.worker

import android.app.Notification
import android.content.ContentValues
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.R
import com.example.VideoTextTranslatorApp
import com.example.domain.model.CaptionStyleConfig
import com.example.engine.TimeFormatters
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import kotlin.math.roundToInt

class VideoBurnInWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        val projectId = inputData.getLong(KEY_PROJECT_ID, 0L)
        val targetLang = inputData.getString(KEY_TARGET_LANG) ?: "hi"
        val styleJson = inputData.getString(KEY_STYLE_JSON) ?: return Result.failure()
        val style = runCatching { json.decodeFromString<CaptionStyleConfig>(styleJson) }
            .getOrDefault(CaptionStyleConfig())

        val app = applicationContext as VideoTextTranslatorApp
        val container = app.container
        val project = container.projectRepository.getProjectOnce(projectId) ?: return Result.failure()
        val segments = container.projectRepository
            .observeSegmentsWithTranslation(projectId, targetLang)
            .first()

        if (segments.isEmpty()) {
            return Result.failure(workDataOf(KEY_ERROR to "Video par lagane ke liye koi translated lines nahi mili."))
        }

        setForeground(createForegroundInfo(project.title, 0, "MP4 export shuru ho raha hai..."))

        val exportDir = File(applicationContext.filesDir, "videos").apply { mkdirs() }
        val safeName = project.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32).ifBlank { "translated" }
        val tempOut = File(exportDir, "${safeName}_${targetLang}_${System.currentTimeMillis()}.mp4")
        val startWall = System.currentTimeMillis()

        return try {
            container.videoBurnInEngine.exportBurnedVideo(
                videoUri = Uri.parse(project.videoUri),
                outputFile = tempOut,
                segments = segments,
                style = style
            ) { frac ->
                val pct = (frac * 100).roundToInt()
                val eta = if (frac > 0.03f) {
                    val elapsed = System.currentTimeMillis() - startWall
                    ((elapsed / frac - elapsed) / 1000L).toLong().coerceAtLeast(1L)
                } else -1L
                setForeground(
                    createForegroundInfo(
                        project.title,
                        pct,
                        "Video ban raha hai ($pct%) • ${TimeFormatters.formatEta(eta)}"
                    )
                )
                setProgress(
                    workDataOf(
                        KEY_PROGRESS to frac,
                        KEY_ETA_SECONDS to eta
                    )
                )
            }

            // Also publish a copy into user's Movies/VideoTextTranslator via MediaStore
            saveToPublicMovies(tempOut, "${safeName}_${targetLang}.mp4")

            Result.success(
                workDataOf(
                    KEY_OUTPUT_PATH to tempOut.absolutePath,
                    KEY_PROGRESS to 1f
                )
            )
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "MP4 export fail ho gaya."
            Result.failure(workDataOf(KEY_ERROR to msg))
        }
    }

    private fun saveToPublicMovies(sourceFile: File, displayName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/VideoTextTranslator")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = applicationContext.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return
            resolver.openOutputStream(uri)?.use { os ->
                FileInputStream(sourceFile).use { fis ->
                    fis.copyTo(os)
                }
            }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    }

    private fun createForegroundInfo(title: String, pct: Int, detail: String): ForegroundInfo {
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val notification: Notification = NotificationCompat.Builder(
            applicationContext,
            VideoTextTranslatorApp.CHANNEL_PROCESSING
        )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("MP4 Export • $title")
            .setContentText(detail)
            .setProgress(100, pct.coerceIn(0, 100), pct <= 0)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Roko", cancelIntent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_PROJECT_ID = "project_id"
        const val KEY_TARGET_LANG = "target_lang"
        const val KEY_STYLE_JSON = "style_json"
        const val KEY_PROGRESS = "progress"
        const val KEY_ETA_SECONDS = "eta_seconds"
        const val KEY_OUTPUT_PATH = "output_path"
        const val KEY_ERROR = "error"
        private const val NOTIFICATION_ID = 4402
    }
}
