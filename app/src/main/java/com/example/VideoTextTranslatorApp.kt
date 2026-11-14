package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VideoTextTranslatorApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannels()
        appScope.launch {
            container.languagePackRepository.syncCatalogAndDiskState()
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val processingChannel = NotificationChannel(
                CHANNEL_PROCESSING,
                "Video Text Processing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background video text reading, speech recognition, and MP4 export progress"
            }
            nm.createNotificationChannel(processingChannel)
        }
    }

    companion object {
        const val CHANNEL_PROCESSING = "vtt_processing_channel"
    }
}
