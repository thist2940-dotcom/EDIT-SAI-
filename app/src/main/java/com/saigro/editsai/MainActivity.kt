package com.saigro.editsai

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLogger.install(applicationContext)
        setContent { EditSaiApp() }
    }
}

private enum class AppScreen(val label: String) {
    HOME("EDIT SAI"), NEW_PROJECT("New Project"), EDITOR("Editor"),
    ANIMATION("Animation Studio"), EFFECTS("Effects Library"),
    ASSETS("Asset Manager"), AI("AI Tools"), SETTINGS("Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSaiApp() {
    var screen by remember { mutableStateOf(AppScreen.HOME) }
    val context = LocalContext.current
    val lastCrashLog = remember { CrashLogger.readLatest(context) }

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text(screen.label) }) }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize()) {
                if (!lastCrashLog.isNullOrBlank()) {
                    SelectionContainer {
                        Text(
                            "Last crash log available\n$lastCrashLog",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                if (screen == AppScreen.HOME) {
                    HomeScreen(padding) { screen = it }
                } else if (screen == AppScreen.EDITOR) {
                    EditorScreen(padding) { screen = AppScreen.HOME }
                } else {
                    PlaceholderScreen(padding, screen) { screen = AppScreen.HOME }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(padding: PaddingValues, onOpen: (AppScreen) -> Unit) {
    val actions = listOf(
        AppScreen.NEW_PROJECT, AppScreen.EDITOR, AppScreen.ANIMATION,
        AppScreen.EFFECTS, AppScreen.ASSETS, AppScreen.AI, AppScreen.SETTINGS
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 24.dp)
    ) {
        item {
            Text("Your creative workspace", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Phase 2 video import and playback is now available in Editor.",
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
            )
        }
        items(actions) { action ->
            Button(onClick = { onOpen(action) }, modifier = Modifier.fillMaxWidth()) {
                Text(action.label)
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun EditorScreen(padding: PaddingValues, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var trimStartMs by remember { mutableLongStateOf(0L) }
    var trimEndMs by remember { mutableLongStateOf(0L) }
    var previewRequest by remember { mutableIntStateOf(0) }
    var exporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableFloatStateOf(0f) }
    var lastExportUri by remember { mutableStateOf<Uri?>(null) }
    var lastExportName by remember { mutableStateOf<String?>(null) }
    var activeTransformer by remember { mutableStateOf<Transformer?>(null) }
    DisposableEffect(exporting) {
        onDispose { if (!exporting) activeTransformer?.cancel() }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        errorMessage = null
        selectedUri = null
        durationMs = 0L
        trimStartMs = 0L
        trimEndMs = 0L
        lastExportUri = null
        lastExportName = null
        if (uri == null) return@rememberLauncherForActivityResult

        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
        } catch (_: IllegalArgumentException) {
        }

        if (isReadableVideoUri(context.contentResolver, uri)) {
            selectedUri = uri
        } else {
            errorMessage = "Cannot read this video"
        }
    }

    LaunchedEffect(exporting, activeTransformer) {
        if (!exporting) return@LaunchedEffect
        val transformer = activeTransformer ?: return@LaunchedEffect
        val holder = ProgressHolder()
        while (isActive && exporting) {
            val state = runCatching { transformer.getProgress(holder) }.getOrNull()
            if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                exportProgress = (holder.progress / 100f).coerceIn(0f, 1f)
            }
            delay(300L)
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { picker.launch(arrayOf("video/*")) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !exporting
            ) {
                Text(if (selectedUri == null) "Select Video" else "Select Another Video")
            }

            selectedUri?.let { uri ->
                VideoPlayer(
                    uri = uri,
                    trimStartMs = trimStartMs,
                    trimEndMs = trimEndMs,
                    previewRequest = previewRequest,
                    onPreviewFinished = {},
                    onDurationChanged = { newDuration ->
                        if (newDuration != durationMs) {
                            durationMs = newDuration
                            if (trimEndMs <= 0L || trimEndMs > newDuration) {
                                trimEndMs = newDuration
                            }
                            if (trimStartMs >= trimEndMs && newDuration > 1000L) {
                                trimStartMs = 0L
                                trimEndMs = newDuration
                            }
                        }
                    },
                    onError = {
                        selectedUri = null
                        errorMessage = it
                    }
                )
            } ?: Text(
                errorMessage ?: "Choose a video from your device to start playback.",
                style = MaterialTheme.typography.bodyLarge,
                color = if (errorMessage != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onBackground
                }
            )

            if (selectedUri != null && durationMs > 0L) {
                val safeEnd = trimEndMs.coerceIn(1L, durationMs)
                val validTrim = trimStartMs < safeEnd

                Text("Trim selection", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Start " + formatDuration(trimStartMs) +
                        "    End " + formatDuration(safeEnd)
                )

                Text("Start")
                Slider(
                    value = trimStartMs.toFloat().coerceIn(
                        0f,
                        (safeEnd - 100L).coerceAtLeast(0L).toFloat()
                    ),
                    onValueChange = {
                        trimStartMs = it.toLong().coerceIn(
                            0L,
                            (safeEnd - 100L).coerceAtLeast(0L)
                        )
                    },
                    valueRange = 0f..safeEnd.toFloat(),
                    enabled = !exporting
                )

                Text("End")
                Slider(
                    value = safeEnd.toFloat().coerceIn(
                        (trimStartMs + 100L).coerceAtMost(durationMs).toFloat(),
                        durationMs.toFloat()
                    ),
                    onValueChange = {
                        trimEndMs = it.toLong().coerceIn(
                            (trimStartMs + 100L).coerceAtMost(durationMs),
                            durationMs
                        )
                    },
                    valueRange = 0f..durationMs.toFloat(),
                    enabled = !exporting
                )

                Button(
                    onClick = { if (validTrim) previewRequest++ },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = validTrim && !exporting
                ) {
                    Text("Preview Selection")
                }

                Button(
                    onClick = {
                        val uri = selectedUri
                        val end = trimEndMs

                        if (uri == null || durationMs <= 0L) {
                            errorMessage = "Select a readable video first"
                            return@Button
                        }
                        if (trimStartMs >= end) {
                            errorMessage = "Start time must be before end time"
                            return@Button
                        }
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            errorMessage =
                                "Trim export to public Movies requires Android 10 or newer"
                            return@Button
                        }

                        errorMessage = null
                        exporting = true
                        exportProgress = 0f
                        lastExportUri = null
                        lastExportName = null

                        val stamp = SimpleDateFormat(
                            "yyyyMMdd_HHmmss",
                            Locale.US
                        ).format(Date())
                        val fileName = "EDIT_SAI_trim_" + stamp + ".mp4"
                        val tempFile = File(
                            context.cacheDir,
                            "edit_sai_export_" + System.currentTimeMillis() + ".mp4"
                        )

                        val mediaItem = MediaItem.Builder()
                            .setUri(uri)
                            .setClippingConfiguration(
                                MediaItem.ClippingConfiguration.Builder()
                                    .setStartPositionMs(trimStartMs)
                                    .setEndPositionMs(end)
                                    .build()
                            )
                            .build()
                        val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()

                        val transformer = Transformer.Builder(context)
                            .addListener(object : Transformer.Listener {
                                override fun onCompleted(
                                    composition: Composition,
                                    result: ExportResult
                                ) {
                                    scope.launch {
                                        try {
                                            val savedUri = withContext(Dispatchers.IO) {
                                                saveExportToMediaStore(
                                                    context,
                                                    tempFile,
                                                    fileName
                                                )
                                            }
                                            tempFile.delete()
                                            activeTransformer = null
                                            exporting = false
                                            exportProgress = 1f
                                            lastExportUri = savedUri
                                            lastExportName = fileName

                                            val actionResult = snackbarHostState.showSnackbar(
                                                message = "Saved " + fileName,
                                                actionLabel = "Open"
                                            )
                                            if (actionResult == SnackbarResult.ActionPerformed) {
                                                openVideoUri(
                                                    context,
                                                    savedUri
                                                ) { errorMessage = it }
                                            }
                                        } catch (e: Exception) {
                                            tempFile.delete()
                                            activeTransformer = null
                                            exporting = false
                                            CrashLogger.logNonFatal(
                                                context,
                                                e,
                                                "Phase 3 export save failed"
                                            )
                                            errorMessage =
                                                "Export finished but could not save the video"
                                        }
                                    }
                                }

                                override fun onError(
                                    composition: Composition,
                                    result: ExportResult,
                                    exportException: ExportException
                                ) {
                                    tempFile.delete()
                                    activeTransformer = null
                                    exporting = false
                                    CrashLogger.logNonFatal(
                                        context,
                                        exportException,
                                        "Phase 3 trim export failed"
                                    )
                                    errorMessage = "Could not export this trimmed video"
                                }
                            })
                            .build()

                        activeTransformer = transformer
                        try {
                            transformer.start(
                                editedMediaItem,
                                tempFile.absolutePath
                            )
                        } catch (e: Exception) {
                            tempFile.delete()
                            activeTransformer = null
                            exporting = false
                            CrashLogger.logNonFatal(
                                context,
                                e,
                                "Phase 3 trim export could not start"
                            )
                            errorMessage = "Could not start video export"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = validTrim && !exporting
                ) {
                    Text("Export Trimmed Video")
                }

                if (exporting) {
                    LinearProgressIndicator(
                        progress = exportProgress,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (exportProgress <= 0f) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        Text(
                            (exportProgress * 100f).toInt().toString() + "%"
                        )
                    }
                }

                lastExportName?.let {
                    Text("Last export: " + it)
                }

                lastExportUri?.let { uri ->
                    Button(
                        onClick = {
                            openVideoUri(context, uri) {
                                errorMessage = it
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Last Export")
                    }
                }
            }

            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                enabled = !exporting
            ) {
                Text("Back to Home")
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

private fun isReadableVideoUri(contentResolver: ContentResolver, uri: Uri): Boolean {
    return try {
        contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
    } catch (_: Exception) {
        false
    }
}

private fun saveExportToMediaStore(
    context: Context,
    tempFile: File,
    fileName: String
): Uri {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        throw IllegalStateException(
            "Public Movies export requires Android 10 or newer"
        )
    }

    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(
            MediaStore.Video.Media.RELATIVE_PATH,
            Environment.DIRECTORY_MOVIES + File.separator + "EDIT_SAI"
        )
        put(MediaStore.Video.Media.IS_PENDING, 1)
    }

    val uri = resolver.insert(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        values
    ) ?: throw IllegalStateException(
        "Could not create the gallery export"
    )

    try {
        resolver.openOutputStream(uri)?.use { output ->
            tempFile.inputStream().use { input ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException(
            "Could not write the gallery export"
        )

        resolver.update(
            uri,
            ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            },
            null,
            null
        )

        return uri
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        throw e
    }
}

private fun openVideoUri(
    context: Context,
    uri: Uri,
    onError: (String) -> Unit
) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "video/mp4")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    } catch (_: ActivityNotFoundException) {
        onError("No video app is available to open the export")
    }
}

private fun Long.toSliderValue(duration: Long): Float =
    if (duration > 0L) (toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

@Composable
private fun PlaceholderScreen(padding: PaddingValues, screen: AppScreen, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(screen.label, style = MaterialTheme.typography.headlineMedium)
        Text("This Phase 1 placeholder is ready for a future feature implementation.")
        Button(onClick = onBack) { Text("Back to Home") }
    }
}@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(
    uri: Uri,
    trimStartMs: Long,
    trimEndMs: Long,
    previewRequest: Int,
    onPreviewFinished: () -> Unit,
    onDurationChanged: (Long) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    var player by remember(uri) { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember(uri) { mutableStateOf(false) }
    var currentPosition by remember(uri) { mutableLongStateOf(0L) }
    var duration by remember(uri) { mutableLongStateOf(0L) }
    var sliderPosition by remember(uri) { mutableFloatStateOf(0f) }
    var previewing by remember(uri) { mutableStateOf(false) }

    DisposableEffect(uri) {
        var createdPlayer: ExoPlayer? = null
        var listener: Player.Listener? = null

        try {
            val dataSourceFactory = DefaultDataSource.Factory(context)
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            val newPlayer = ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
            createdPlayer = newPlayer

            val newListener = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        duration = newPlayer.duration.coerceAtLeast(0L)
                        currentPosition = newPlayer.currentPosition.coerceAtLeast(0L)
                        sliderPosition = currentPosition.toSliderValue(duration)
                        onDurationChanged(duration)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    player = null
                    previewing = false
                    onError("Cannot play this video")
                }
            }

            listener = newListener
            newPlayer.addListener(newListener)
            newPlayer.setMediaItem(MediaItem.fromUri(uri))
            newPlayer.prepare()
            newPlayer.playWhenReady = true
            player = newPlayer
        } catch (_: Exception) {
            createdPlayer?.release()
            player = null
            onError("Cannot read this video")
        }

        onDispose {
            listener?.let { createdPlayer?.removeListener(it) }
            createdPlayer?.release()
            if (player === createdPlayer) player = null
        }
    }

    val activePlayer = player

    LaunchedEffect(activePlayer) {
        if (activePlayer == null) return@LaunchedEffect

        while (isActive) {
            currentPosition = activePlayer.currentPosition.coerceAtLeast(0L)
            duration = activePlayer.duration.coerceAtLeast(0L)

            if (duration > 0L) {
                sliderPosition =
                    (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                onDurationChanged(duration)
            }

            delay(250L)
        }
    }

    LaunchedEffect(activePlayer, previewRequest) {
        if (
            activePlayer == null ||
            previewRequest == 0 ||
            trimStartMs >= trimEndMs
        ) {
            return@LaunchedEffect
        }

        activePlayer.seekTo(trimStartMs)
        activePlayer.play()
        previewing = true
    }

    LaunchedEffect(activePlayer, previewing, trimStartMs, trimEndMs) {
        if (activePlayer == null || !previewing) return@LaunchedEffect

        while (isActive && previewing) {
            if (activePlayer.currentPosition >= trimEndMs) {
                activePlayer.pause()
                activePlayer.seekTo(trimStartMs)
                previewing = false
                onPreviewFinished()
                break
            }
            delay(80L)
        }
    }

    if (activePlayer != null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        player = activePlayer
                        useController = false
                    }
                },
                update = { it.player = activePlayer },
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
            )

            Button(
                onClick = {
                    previewing = false
                    if (activePlayer.isPlaying) activePlayer.pause()
                    else activePlayer.play()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isPlaying) "Pause" else "Play")
            }

            Slider(
                value = sliderPosition,
                onValueChange = {
                    previewing = false
                    sliderPosition = it
                },
                onValueChangeFinished = {
                    if (duration > 0L) {
                        activePlayer.seekTo(
                            (sliderPosition * duration).toLong()
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = duration > 0L
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatDuration(currentPosition))
                Text(formatDuration(duration))
            }
        }
    }
}

