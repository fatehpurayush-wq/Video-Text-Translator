package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import com.example.ui.screens.LanguagePacksScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WizardWorkspaceScreen
import com.example.ui.theme.VideoTextTranslatorTheme

enum class AppTab(
    val labelHinglish: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    WORKSPACE("Naya Kaam", Icons.Filled.VideoFile, Icons.Outlined.VideoFile, "nav_workspace"),
    PROJECTS("Projects", Icons.Filled.FolderCopy, Icons.Outlined.FolderCopy, "nav_projects"),
    PACKS("Packs", Icons.Filled.CloudDownload, Icons.Outlined.CloudDownload, "nav_packs"),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            VideoTextTranslatorTheme(themeMode = settings.themeMode) {
                VideoTextTranslatorRoot(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun VideoTextTranslatorRoot(viewModel: MainViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val allProjects by viewModel.allProjects.collectAsStateWithLifecycle()
    val allPacks by viewModel.allPacks.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(AppTab.WORKSPACE) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Android 13+ notification permission for foreground background-work notifications
    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(bannerMessage) {
        val msg = bannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                AppTab.entries.forEach { tab ->
                    val selected = currentTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.labelHinglish
                            )
                        },
                        label = { Text(tab.labelHinglish) },
                        modifier = Modifier.testTag(tab.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                AppTab.WORKSPACE -> WizardWorkspaceScreen(viewModel = viewModel)
                AppTab.PROJECTS -> ProjectsScreen(
                    projects = allProjects,
                    packs = allPacks,
                    onReopenProject = { id ->
                        viewModel.openProject(id)
                        currentTab = AppTab.WORKSPACE
                    },
                    onDuplicateProject = { viewModel.duplicateProject(it) },
                    onRenameProject = { id, title -> viewModel.renameProject(id, title) },
                    onDeleteProject = { viewModel.deleteProject(it) },
                    onAddTargetLanguageToProject = { projectId, targetCode ->
                        viewModel.openProject(projectId)
                        viewModel.addTargetLanguageToExistingProject(targetCode)
                        currentTab = AppTab.WORKSPACE
                    },
                    onDownloadPack = { viewModel.startOrResumePack(it) },
                    onStartNewProject = {
                        viewModel.startNewProjectFlow()
                        currentTab = AppTab.WORKSPACE
                    }
                )
                AppTab.PACKS -> LanguagePacksScreen(
                    packs = allPacks,
                    wifiOnly = settings.wifiOnlyDownloads,
                    storageBytes = viewModel.calculatePackStorageBytes(),
                    onToggleWifiOnly = { viewModel.setWifiOnly(it) },
                    onDownloadOrResume = { viewModel.startOrResumePack(it) },
                    onPause = { viewModel.pausePack(it) },
                    onDelete = { viewModel.deletePack(it) },
                    onRefresh = { viewModel.refreshLanguagePacks() }
                )
                AppTab.SETTINGS -> SettingsScreen(
                    settings = settings,
                    packs = allPacks,
                    packStorageBytes = viewModel.calculatePackStorageBytes(),
                    onSetDefaultSource = { viewModel.setDefaultSourceLang(it) },
                    onSetDefaultTarget = { viewModel.setDefaultTargetLang(it) },
                    onSetFrameInterval = { viewModel.setFrameSamplingInterval(it) },
                    onSetMinTextLength = { viewModel.setMinTextLength(it) },
                    onSetWifiOnly = { viewModel.setWifiOnly(it) },
                    onSetTheme = { viewModel.setThemeMode(it) },
                    onSaveCaptionDefaults = { viewModel.saveDefaultCaptionStyle(it) },
                    onClearCache = { viewModel.clearAppCache() },
                    onDeleteAllPacks = { viewModel.deleteAllPacks() },
                    onDownloadPack = { viewModel.startOrResumePack(it) }
                )
            }
        }
    }
}
