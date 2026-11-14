package com.example.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import com.example.data.local.LanguagePackDao
import com.example.data.local.LanguagePackEntity
import com.example.data.local.SettingsDataStore
import com.example.domain.model.LanguageCatalog
import com.example.domain.model.OcrEngineType
import com.example.domain.model.PackCategory
import com.example.domain.model.PackStatus
import com.example.engine.OcrEngine
import com.example.engine.SpeechEngine
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

class LanguagePackRepository(
    private val context: Context,
    private val packDao: LanguagePackDao,
    private val settingsDataStore: SettingsDataStore,
    private val ocrEngine: OcrEngine,
    private val speechEngine: SpeechEngine
) {
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val modelManager = RemoteModelManager.getInstance()

    val allPacksFlow: Flow<List<LanguagePackEntity>> = packDao.observeAllPacks()

    suspend fun syncCatalogAndDiskState() = withContext(Dispatchers.IO) {
        val existingMap = packDao.getAllPacksOnce().associateBy { it.id }
        val entries = mutableListOf<LanguagePackEntity>()

        for (lang in LanguageCatalog.allLanguages) {
            // 1. OCR Pack
            val ocrId = "ocr_${lang.code}"
            val ocrPrev = existingMap[ocrId]
            val ocrState = when (lang.ocrEngineType) {
                OcrEngineType.MLKIT_BUNDLED -> Triple(PackStatus.BUILT_IN, 0L, null)
                OcrEngineType.TESSERACT_PACK -> {
                    val tessCode = lang.tessCode
                    if (tessCode != null && ocrEngine.isTesseractModelReady(tessCode)) {
                        val f = File(ocrEngine.tessDataRootDir(), "tessdata/$tessCode.traineddata")
                        Triple(PackStatus.READY, f.length(), f.absolutePath)
                    } else if (ocrPrev?.status == PackStatus.PAUSED.name) {
                        Triple(PackStatus.PAUSED, ocrPrev.downloadedBytes, ocrPrev.localPath)
                    } else {
                        Triple(PackStatus.NOT_DOWNLOADED, 0L, null)
                    }
                }
            }
            entries.add(
                LanguagePackEntity(
                    id = ocrId,
                    category = PackCategory.OCR.name,
                    langCode = lang.code,
                    displayName = "${lang.englishName} (${lang.nativeName})",
                    sizeBytes = (lang.tessSizeMb * 1024 * 1024).toLong(),
                    downloadedBytes = ocrState.second,
                    status = ocrState.first.name,
                    accuracyRating = lang.ocrAccuracy.name,
                    localPath = ocrState.third
                )
            )

            // 2. Translation Pack
            val transId = "trans_${lang.code}"
            val mlCode = lang.mlkitTranslateCode
            val transStatus = if (mlCode == null) {
                PackStatus.UNAVAILABLE
            } else {
                val model = TranslateRemoteModel.Builder(mlCode).build()
                val downloaded = runCatching { modelManager.isModelDownloaded(model).await() }.getOrDefault(false)
                if (downloaded) PackStatus.READY else PackStatus.NOT_DOWNLOADED
            }
            val transBytes = (lang.translateSizeMb * 1024 * 1024).toLong()
            entries.add(
                LanguagePackEntity(
                    id = transId,
                    category = PackCategory.TRANSLATION.name,
                    langCode = lang.code,
                    displayName = "${lang.englishName} (${lang.nativeName})",
                    sizeBytes = transBytes,
                    downloadedBytes = if (transStatus == PackStatus.READY) transBytes else 0L,
                    status = transStatus.name,
                    accuracyRating = if (mlCode != null) "GOOD" else "LOW"
                )
            )

            // 3. Speech Pack
            val speechId = "speech_${lang.code}"
            val speechPrev = existingMap[speechId]
            val speechStatus = when {
                lang.voskModelUrl == null -> PackStatus.UNAVAILABLE
                speechEngine.isSpeechModelReady(lang.code) -> PackStatus.READY
                speechPrev?.status == PackStatus.PAUSED.name -> PackStatus.PAUSED
                else -> PackStatus.NOT_DOWNLOADED
            }
            val speechBytes = (lang.voskSizeMb * 1024 * 1024).toLong()
            entries.add(
                LanguagePackEntity(
                    id = speechId,
                    category = PackCategory.SPEECH.name,
                    langCode = lang.code,
                    displayName = "${lang.englishName} (${lang.nativeName})",
                    sizeBytes = speechBytes,
                    downloadedBytes = if (speechStatus == PackStatus.READY) speechBytes else (speechPrev?.downloadedBytes ?: 0L),
                    status = speechStatus.name,
                    accuracyRating = lang.speechAccuracy.name,
                    localPath = speechEngine.getModelDirForLang(lang.code)?.absolutePath
                )
            )
        }

        packDao.upsertPacks(entries)
    }

    fun startOrResumeDownload(packId: String) {
        if (activeJobs[packId]?.isActive == true) return
        val job = repoScope.launch {
            val pack = packDao.getPackById(packId) ?: return@launch
            val wifiOnly = settingsDataStore.settingsFlow.first().wifiOnlyDownloads

            val netError = checkNetworkAndStorage(wifiOnly, pack.sizeBytes)
            if (netError != null) {
                packDao.updatePackState(
                    id = packId,
                    status = PackStatus.ERROR.name,
                    downloadedBytes = pack.downloadedBytes,
                    errorMessage = netError
                )
                return@launch
            }

            when (pack.category) {
                PackCategory.TRANSLATION.name -> downloadMlKitTranslation(pack, wifiOnly)
                PackCategory.OCR.name -> downloadTesseractPack(pack)
                PackCategory.SPEECH.name -> downloadVoskSpeechPack(pack)
            }
        }
        activeJobs[packId] = job
    }

    fun pauseDownload(packId: String) {
        activeJobs.remove(packId)?.cancel()
        repoScope.launch {
            val pack = packDao.getPackById(packId) ?: return@launch
            packDao.updatePackState(
                id = packId,
                status = PackStatus.PAUSED.name,
                downloadedBytes = pack.downloadedBytes,
                localPath = pack.localPath,
                errorMessage = null
            )
        }
    }

    suspend fun deletePack(packId: String) = withContext(Dispatchers.IO) {
        activeJobs.remove(packId)?.cancel()
        val pack = packDao.getPackById(packId) ?: return@withContext
        when (pack.category) {
            PackCategory.TRANSLATION.name -> {
                val mlCode = LanguageCatalog.findByCode(pack.langCode)?.mlkitTranslateCode
                if (mlCode != null) {
                    val model = TranslateRemoteModel.Builder(mlCode).build()
                    runCatching { modelManager.deleteDownloadedModel(model).await() }
                }
            }
            PackCategory.OCR.name -> {
                val tessCode = LanguageCatalog.findByCode(pack.langCode)?.tessCode
                if (tessCode != null) {
                    File(ocrEngine.tessDataRootDir(), "tessdata/$tessCode.traineddata").delete()
                    File(ocrEngine.tessDataRootDir(), "tessdata/$tessCode.traineddata.part").delete()
                }
            }
            PackCategory.SPEECH.name -> {
                File(speechEngine.voskModelsDir(), pack.langCode).deleteRecursively()
                File(speechEngine.voskModelsDir(), "${pack.langCode}.zip.part").delete()
            }
        }
        val newStatus = if (pack.status == PackStatus.BUILT_IN.name || pack.status == PackStatus.UNAVAILABLE.name) {
            pack.status
        } else {
            PackStatus.NOT_DOWNLOADED.name
        }
        packDao.updatePackState(packId, newStatus, 0L, null, null)
    }

    suspend fun deleteAllDownloadedPacks() = withContext(Dispatchers.IO) {
        val packs = packDao.getAllPacksOnce()
        for (p in packs) {
            if (p.status == PackStatus.READY.name || p.status == PackStatus.PAUSED.name || p.status == PackStatus.DOWNLOADING.name) {
                deletePack(p.id)
            }
        }
    }

    fun calculateDownloadedStorageBytes(packs: List<LanguagePackEntity>): Long {
        var total = 0L
        val tessDir = File(ocrEngine.tessDataRootDir(), "tessdata")
        if (tessDir.exists()) {
            total += tessDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
        val voskDir = speechEngine.voskModelsDir()
        if (voskDir.exists()) {
            total += voskDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
        // ML Kit models are stored in internal app storage (~30MB each when READY)
        total += packs.filter { it.category == PackCategory.TRANSLATION.name && it.status == PackStatus.READY.name }
            .sumOf { it.sizeBytes }
        return total
    }

    private suspend fun downloadMlKitTranslation(pack: LanguagePackEntity, wifiOnly: Boolean) {
        val mlCode = LanguageCatalog.findByCode(pack.langCode)?.mlkitTranslateCode ?: return
        packDao.updatePackState(pack.id, PackStatus.DOWNLOADING.name, 0L, null, null)
        try {
            val model = TranslateRemoteModel.Builder(mlCode).build()
            val conditions = DownloadConditions.Builder().apply {
                if (wifiOnly) requireWifi()
            }.build()
            modelManager.download(model, conditions).await()
            packDao.updatePackState(pack.id, PackStatus.READY.name, pack.sizeBytes, null, null)
        } catch (e: CancellationException) {
            packDao.updatePackState(pack.id, PackStatus.PAUSED.name, 0L, null, null)
        } catch (e: Exception) {
            val msg = friendlyDownloadError(e)
            packDao.updatePackState(pack.id, PackStatus.ERROR.name, 0L, null, msg)
        }
    }

    private suspend fun downloadTesseractPack(pack: LanguagePackEntity) {
        val spec = LanguageCatalog.findByCode(pack.langCode) ?: return
        val tessCode = spec.tessCode ?: return
        val urlStr = spec.ocrDownloadUrl ?: return

        val dir = File(ocrEngine.tessDataRootDir(), "tessdata").apply { mkdirs() }
        val partFile = File(dir, "$tessCode.traineddata.part")
        val finalFile = File(dir, "$tessCode.traineddata")

        try {
            downloadWithResume(urlStr, partFile, pack.id, pack.sizeBytes)
            if (finalFile.exists()) finalFile.delete()
            partFile.renameTo(finalFile)
            packDao.updatePackState(
                id = pack.id,
                status = PackStatus.READY.name,
                downloadedBytes = finalFile.length(),
                localPath = finalFile.absolutePath,
                errorMessage = null
            )
        } catch (_: CancellationException) {
            packDao.updatePackState(pack.id, PackStatus.PAUSED.name, partFile.length(), partFile.absolutePath, null)
        } catch (e: Exception) {
            packDao.updatePackState(pack.id, PackStatus.ERROR.name, partFile.length(), partFile.absolutePath, friendlyDownloadError(e))
        }
    }

    private suspend fun downloadVoskSpeechPack(pack: LanguagePackEntity) {
        val spec = LanguageCatalog.findByCode(pack.langCode) ?: return
        val urlStr = spec.voskModelUrl ?: return

        val baseDir = speechEngine.voskModelsDir()
        val partZip = File(baseDir, "${pack.langCode}.zip.part")
        val targetLangDir = File(baseDir, pack.langCode)

        try {
            downloadWithResume(urlStr, partZip, pack.id, pack.sizeBytes)
            packDao.updatePackState(pack.id, PackStatus.DOWNLOADING.name, pack.sizeBytes, null, null)
            if (targetLangDir.exists()) targetLangDir.deleteRecursively()
            targetLangDir.mkdirs()
            unzipFile(partZip, targetLangDir)
            partZip.delete()
            packDao.updatePackState(
                id = pack.id,
                status = PackStatus.READY.name,
                downloadedBytes = pack.sizeBytes,
                localPath = targetLangDir.absolutePath,
                errorMessage = null
            )
        } catch (_: CancellationException) {
            packDao.updatePackState(pack.id, PackStatus.PAUSED.name, partZip.length(), partZip.absolutePath, null)
        } catch (e: Exception) {
            packDao.updatePackState(pack.id, PackStatus.ERROR.name, partZip.length(), partZip.absolutePath, friendlyDownloadError(e))
        }
    }

    private suspend fun downloadWithResume(
        urlStr: String,
        destPartFile: File,
        packId: String,
        fallbackTotalBytes: Long
    ) = withContext(Dispatchers.IO) {
        var existingBytes = if (destPartFile.exists()) destPartFile.length() else 0L
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 25_000
            instanceFollowRedirects = true
            if (existingBytes > 0L) {
                setRequestProperty("Range", "bytes=$existingBytes-")
            }
        }
        conn.connect()
        val responseCode = conn.responseCode
        val append = responseCode == HttpURLConnection.HTTP_PARTIAL
        if (!append && existingBytes > 0L) {
            existingBytes = 0L
            destPartFile.delete()
        }
        if (responseCode !in 200..299) {
            throw IllegalStateException("Server error ($responseCode)")
        }

        val contentLen = conn.contentLengthLong.takeIf { it > 0 } ?: fallbackTotalBytes
        val totalExpected = if (append && contentLen > 0) existingBytes + contentLen else contentLen

        packDao.updatePackState(packId, PackStatus.DOWNLOADING.name, existingBytes, destPartFile.absolutePath, null)

        BufferedInputStream(conn.inputStream).use { input ->
            RandomAccessFile(destPartFile, "rw").use { raf ->
                if (append) raf.seek(existingBytes) else raf.setLength(0L)
                val buffer = ByteArray(16 * 1024)
                var downloaded = existingBytes
                var lastUiUpdate = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    raf.write(buffer, 0, read)
                    downloaded += read
                    val now = System.currentTimeMillis()
                    if (now - lastUiUpdate > 350L) {
                        lastUiUpdate = now
                        packDao.updatePackState(
                            id = packId,
                            status = PackStatus.DOWNLOADING.name,
                            downloadedBytes = downloaded.coerceAtMost(totalExpected.coerceAtLeast(downloaded)),
                            localPath = destPartFile.absolutePath,
                            errorMessage = null
                        )
                    }
                }
            }
        }
        conn.disconnect()
    }

    private fun unzipFile(zipFile: File, targetDir: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry = zis.nextEntry
            val buffer = ByteArray(16 * 1024)
            while (entry != null) {
                val outFile = File(targetDir, entry.name)
                if (!outFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                    throw SecurityException("Invalid zip entry path")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        var len = zis.read(buffer)
                        while (len > 0) {
                            fos.write(buffer, 0, len)
                            len = zis.read(buffer)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    fun checkNetworkAndStorage(wifiOnly: Boolean, requiredBytes: Long): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return "Internet nahi chal raha! Pack download karne ke liye internet chalu karein."
        }
        if (wifiOnly && !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        ) {
            return "Sirf Wi-Fi par download chalu hai. Wi-Fi connect karein ya Settings mein switch band karein."
        }
        val stat = StatFs(context.filesDir.absolutePath)
        val availBytes = stat.availableBytes
        val need = (requiredBytes + 25L * 1024L * 1024L)
        if (availBytes < need) {
            val needMb = (need / (1024L * 1024L)).coerceAtLeast(30L)
            return "Phone mein storage kam hai! Kam se kam ${needMb} MB jagah khali karein."
        }
        return null
    }

    private fun friendlyDownloadError(e: Exception): String {
        val msg = e.localizedMessage.orEmpty()
        return when {
            msg.contains("UnknownHost", ignoreCase = true) || msg.contains("Network", ignoreCase = true) ->
                "Internet connection toot gaya. Dobara koshish karein."
            msg.contains("ENOSPC", ignoreCase = true) ->
                "Phone mein jagah bhar gayi hai. Storage khali karein."
            else -> "Download ruk gaya: ${msg.ifBlank { "Dobara koshish karein" }}"
        }
    }
}
