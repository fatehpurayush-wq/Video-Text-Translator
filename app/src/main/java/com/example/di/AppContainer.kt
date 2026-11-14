package com.example.di

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.repository.LanguagePackRepository
import com.example.data.repository.ProjectRepository
import com.example.engine.ExportEngine
import com.example.engine.OcrEngine
import com.example.engine.SpeechEngine
import com.example.engine.TranslationEngine
import com.example.engine.VideoBurnInEngine
import com.example.engine.VideoMetadataHelper

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }
    val settingsDataStore: SettingsDataStore by lazy { SettingsDataStore(appContext) }

    val ocrEngine: OcrEngine by lazy { OcrEngine(appContext) }
    val speechEngine: SpeechEngine by lazy { SpeechEngine(appContext) }
    val translationEngine: TranslationEngine by lazy { TranslationEngine() }
    val videoMetadataHelper: VideoMetadataHelper by lazy { VideoMetadataHelper(appContext) }
    val exportEngine: ExportEngine by lazy { ExportEngine(appContext) }
    val videoBurnInEngine: VideoBurnInEngine by lazy { VideoBurnInEngine(appContext) }

    val languagePackRepository: LanguagePackRepository by lazy {
        LanguagePackRepository(
            context = appContext,
            packDao = database.languagePackDao(),
            settingsDataStore = settingsDataStore,
            ocrEngine = ocrEngine,
            speechEngine = speechEngine
        )
    }

    val projectRepository: ProjectRepository by lazy {
        ProjectRepository(
            projectDao = database.projectDao(),
            segmentDao = database.segmentDao(),
            translationDao = database.translationDao(),
            glossaryDao = database.glossaryDao(),
            translationEngine = translationEngine
        )
    }
}
