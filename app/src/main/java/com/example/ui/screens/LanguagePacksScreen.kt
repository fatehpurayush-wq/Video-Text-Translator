package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.LanguagePackEntity
import com.example.domain.model.AccuracyRating
import com.example.domain.model.PackCategory
import com.example.domain.model.PackStatus

@Composable
fun LanguagePacksScreen(
    packs: List<LanguagePackEntity>,
    wifiOnly: Boolean,
    storageBytes: Long,
    onToggleWifiOnly: (Boolean) -> Unit,
    onDownloadOrResume: (String) -> Unit,
    onPause: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(PackCategory.TRANSLATION) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        onRefresh()
    }

    val filteredPacks = remember(packs, selectedCategory, searchQuery) {
        packs.filter { p ->
            p.category == selectedCategory.name &&
                (searchQuery.isBlank() || p.displayName.contains(searchQuery, ignoreCase = true) || p.langCode.contains(searchQuery, ignoreCase = true))
        }
    }

    val storageMb = storageBytes / (1024.0 * 1024.0)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Offline Language Packs", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = String.format("Storage: %.1f MB use ho raha hai", storageMb),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Wi-Fi Only Switch Banner
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Sirf Wi-Fi par download", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Mobile data bachane ke liye on rakhein",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = wifiOnly,
                    onCheckedChange = onToggleWifiOnly,
                    modifier = Modifier.testTag("packs_wifi_only_switch")
                )
            }
        }

        // Category Tabs
        TabRow(selectedTabIndex = PackCategory.entries.indexOf(selectedCategory)) {
            PackCategory.entries.forEach { cat ->
                Tab(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    text = { Text(cat.labelHinglish, maxLines = 1) }
                )
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Bhasha khojein...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        val categoryGuide = when (selectedCategory) {
            PackCategory.OCR -> "English, Latin, Hindi, Marathi (Devanagari), Chinese, Japanese aur Korean pehle se ready hain. Telugu, Tamil, Bengali aadi niche se download karein."
            PackCategory.TRANSLATION -> "Jis pair ke dono packs downloaded honge woh 100% bina internet translate honge. Non-English pairs English ke through translate hote hain."
            PackCategory.SPEECH -> "Video mein bole hue shabd offline sunne ke liye speech pack ek baar download karein."
        }
        Text(
            text = categoryGuide,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            items(filteredPacks, key = { it.id }) { pack ->
                PackItemCard(
                    pack = pack,
                    onDownloadOrResume = { onDownloadOrResume(pack.id) },
                    onPause = { onPause(pack.id) },
                    onDelete = { onDelete(pack.id) }
                )
            }
        }
    }
}

@Composable
private fun PackItemCard(
    pack: LanguagePackEntity,
    onDownloadOrResume: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit
) {
    val status = runCatching { PackStatus.valueOf(pack.status) }.getOrDefault(PackStatus.NOT_DOWNLOADED)
    val accuracy = runCatching { AccuracyRating.valueOf(pack.accuracyRating) }.getOrDefault(AccuracyRating.AVERAGE)
    val sizeMb = pack.sizeBytes / (1024f * 1024f)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pack_card_${pack.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(pack.displayName, style = MaterialTheme.typography.titleSmall)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (sizeMb > 0.1f) String.format("%.1f MB", sizeMb) else "Built-in",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when (accuracy) {
                                AccuracyRating.GOOD -> MaterialTheme.colorScheme.tertiaryContainer
                                AccuracyRating.AVERAGE -> MaterialTheme.colorScheme.secondaryContainer
                                AccuracyRating.LOW -> MaterialTheme.colorScheme.errorContainer
                            }
                        ) {
                            Text(
                                text = "Accuracy: ${accuracy.labelHinglish}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                when (status) {
                    PackStatus.BUILT_IN, PackStatus.READY -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = "Ready",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(status.labelHinglish, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                            if (status == PackStatus.READY) {
                                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete pack", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    PackStatus.DOWNLOADING -> {
                        OutlinedButton(onClick = onPause) {
                            Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pause")
                        }
                    }
                    PackStatus.PAUSED -> {
                        Row {
                            Button(onClick = onDownloadOrResume) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Resume")
                            }
                            IconButton(onClick = onDelete) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    PackStatus.UNAVAILABLE -> {
                        Text(
                            text = status.labelHinglish,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    PackStatus.NOT_DOWNLOADED, PackStatus.ERROR -> {
                        Button(onClick = onDownloadOrResume) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Download")
                        }
                    }
                }
            }

            if (status == PackStatus.DOWNLOADING || (status == PackStatus.PAUSED && pack.downloadedBytes > 0L)) {
                val frac = if (pack.sizeBytes > 0L) (pack.downloadedBytes.toFloat() / pack.sizeBytes).coerceIn(0f, 1f) else 0f
                LinearProgressIndicator(
                    progress = { frac },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (!pack.errorMessage.isNullOrBlank()) {
                Text(
                    text = pack.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
