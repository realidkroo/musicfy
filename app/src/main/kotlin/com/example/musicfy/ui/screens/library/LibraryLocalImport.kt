// LibraryLocalImport.kt
//
// The "Add local music" action on the Downloaded screen. Moved out of NavigationBuilder.kt's old
// LibraryTabScreen rather than rewritten — the import pipeline (permission grant, tag extraction,
// artwork extraction, artist dedup, format row) is exactly right and untouched; only the trigger
// UI around it changed, from a row in the old bare list to a card matching the rebuilt screens.

package com.example.musicfy.ui.screens.library

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.LocalDatabase
import com.example.musicfy.db.entities.ArtistEntity
import com.example.musicfy.db.entities.FormatEntity
import com.example.musicfy.db.entities.SongArtistMap
import com.example.musicfy.db.entities.SongEntity
import com.example.musicfy.ui.screens.extractAudioMetadata
import com.example.musicfy.ui.screens.search.SearchColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.UUID

@Composable
fun LibraryAddLocalMusicCard(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()

    var importProgress by remember { mutableFloatStateOf(-1f) }
    var isImporting by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris: List<Uri> ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            isImporting = true
            importProgress = 0f
            coroutineScope.launch {
                var successCount = 0
                var failCount = 0
                val total = uris.size
                for ((index, uri) in uris.withIndex()) {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )

                        val metadata = withContext(Dispatchers.IO) {
                            extractAudioMetadata(context, uri)
                        }

                        val songId = "LOCAL_${UUID.randomUUID()}"
                        val thumbnailUrl = withContext(Dispatchers.IO) {
                            metadata.embeddedArtwork?.let { artworkData ->
                                try {
                                    val artworkFile = java.io.File(
                                        context.cacheDir,
                                        "artwork_${songId.hashCode()}.jpg",
                                    )
                                    artworkFile.writeBytes(artworkData)
                                    Uri.fromFile(artworkFile).toString()
                                } catch (_: Exception) {
                                    null
                                }
                            }
                        }

                        withContext(Dispatchers.IO) {
                            database.transaction {
                                insert(
                                    SongEntity(
                                        id = songId,
                                        title = metadata.title,
                                        duration = metadata.durationSeconds,
                                        thumbnailUrl = thumbnailUrl,
                                        albumName = metadata.album,
                                        isLocal = true,
                                        inLibrary = LocalDateTime.now(),
                                        localUri = uri.toString(),
                                    ),
                                )

                                val artistName = metadata.artist
                                val artistId = artistByName(artistName)?.id
                                    ?: ArtistEntity.generateArtistId()
                                insert(
                                    ArtistEntity(
                                        id = artistId,
                                        name = artistName,
                                        isLocal = true,
                                    ),
                                )
                                insert(
                                    SongArtistMap(
                                        songId = songId,
                                        artistId = artistId,
                                        position = 0,
                                    ),
                                )

                                val mimeType = metadata.mimeType ?: "audio/mpeg"
                                val codecs = when {
                                    mimeType.contains("flac") -> "flac"
                                    mimeType.contains("opus") || mimeType.contains("ogg") -> "opus"
                                    mimeType.contains("mp4") || mimeType.contains("m4a") || mimeType.contains("aac") -> "mp4a.40.2"
                                    mimeType.contains("wav") -> "pcm"
                                    else -> "mp3"
                                }
                                upsert(
                                    FormatEntity(
                                        id = songId,
                                        itag = -1,
                                        mimeType = mimeType,
                                        codecs = codecs,
                                        bitrate = metadata.bitrate ?: 0,
                                        sampleRate = metadata.sampleRate,
                                        contentLength = 0L,
                                        loudnessDb = null,
                                        perceptualLoudnessDb = null,
                                        playbackUrl = null,
                                    ),
                                )
                            }
                        }
                        successCount++
                    } catch (e: Exception) {
                        failCount++
                    }
                    importProgress = (index + 1).toFloat() / total
                }
                isImporting = false
                importProgress = -1f

                val message = if (failCount == 0) {
                    "Imported $successCount song${if (successCount != 1) "s" else ""}"
                } else {
                    "Imported $successCount, failed $failCount"
                }
                snackbarHostState.showSnackbar(message)
            }
        },
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(SearchColors.Tile)
            .clickable(enabled = !isImporting) {
                launcher.launch(
                    arrayOf(
                        "audio/*",
                        "audio/mpeg",
                        "audio/mp4",
                        "audio/flac",
                        "audio/ogg",
                        "audio/wav",
                        "audio/x-wav",
                        "audio/aac",
                    ),
                )
            }
            .padding(18.dp),
    ) {
        Text(
            text = if (isImporting) {
                "Importing… ${(importProgress.coerceAtLeast(0f) * 100).toInt()}%"
            } else {
                "Add local music"
            },
            color = SearchColors.Primary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
