package com.saigro.editsai

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLogger.install(applicationContext)
        setContent { EditSaiApp() }
    }
}

private val clipIdGenerator = AtomicLong(System.currentTimeMillis())
private fun nextClipId(): Long = clipIdGenerator.incrementAndGet()

private data class TimelineClip(
    val id: Long,
    val uri: Uri,
    val durationMs: Long = 0L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val cropPreset: String = "NONE"
)

private data class TimelineSnapshot(
    val clips: List<TimelineClip>,
    val selectedClipId: Long?
)

private object ProjectStorage {
    private const val PROJECT_FILE_NAME = "edit_sai_timeline_project.json"

    fun save(context: Context, clips: List<TimelineClip>, selectedClipId: Long?) {
        runCatching {
            val root = JSONObject()
            root.put("selectedClipId", selectedClipId ?: -1L)
            val arr = JSONArray()
            clips.forEach { clip ->
                val obj = JSONObject().apply {
                    put("id", clip.id)
                    put("uri", clip.uri.toString())
                    put("durationMs", clip.durationMs)
                    put("trimStartMs", clip.trimStartMs)
                    put("trimEndMs", clip.trimEndMs)
                    put("rotationDegrees", clip.rotationDegrees)
                    put("flipHorizontal", clip.flipHorizontal)
                    put("flipVertical", clip.flipVertical)
                    put("cropPreset", clip.cropPreset)
                }
                arr.put(obj)
            }
            root.put("clips", arr)
            File(context.filesDir, PROJECT_FILE_NAME).writeText(root.toString())
        }
    }

    fun load(context: Context): TimelineSnapshot? {
        return runCatching {
            val file = File(context.filesDir, PROJECT_FILE_NAME)
            if (!file.exists()) return@runCatching null
            val root = JSONObject(file.readText())
            val savedSelectedId = root.optLong("selectedClipId", -1L).takeIf { it > 0L }
            val arr = root.optJSONArray("clips") ?: return@runCatching null
            val loaded = mutableListOf<TimelineClip>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val uriStr = obj.optString("uri", "")
                if (uriStr.isBlank()) continue
                val uri = Uri.parse(uriStr)
                if (!isReadableVideoUri(context.contentResolver, uri)) continue
                val id = obj.optLong("id", nextClipId())
                val storedDuration = obj.optLong("durationMs", 0L).coerceAtLeast(0L)
                val duration = if (storedDuration > 0L) {
                    storedDuration
                } else {
                    readVideoDurationMs(context, uri)
                }
                val start = obj.optLong("trimStartMs", 0L).coerceAtLeast(0L)
                val rawEnd = obj.optLong("trimEndMs", duration)
                val end = if (duration > 0L) {
                    rawEnd.coerceIn((start + 100L).coerceAtMost(duration), duration)
                } else {
                    rawEnd.coerceAtLeast(start)
                }
                val rotation = ((obj.optInt("rotationDegrees", 0) % 360) + 360) % 360
                val flipH = obj.optBoolean("flipHorizontal", false)
                val flipV = obj.optBoolean("flipVertical", false)
                val crop = obj.optString("cropPreset", "NONE").ifBlank { "NONE" }
                loaded.add(
                    TimelineClip(
                        id = id,
                        uri = uri,
                        durationMs = duration,
                        trimStartMs = start,
                        trimEndMs = end,
                        rotationDegrees = rotation,
                        flipHorizontal = flipH,
                        flipVertical = flipV,
                        cropPreset = crop
                    )
                )
            }
            if (loaded.isEmpty()) return@runCatching null
            val resolvedSelected = savedSelectedId?.takeIf { id -> loaded.any { it.id == id } }
                ?: loaded.first().id
            TimelineSnapshot(loaded, resolvedSelected)
        }.getOrNull()
    }

    fun clear(context: Context) {
        runCatching {
            val file = File(context.filesDir, PROJECT_FILE_NAME)
            if (file.exists()) file.delete()
        }
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
                when (screen) {
                    AppScreen.HOME -> HomeScreen(padding) { screen = it }
                    AppScreen.EDITOR -> EditorScreen(padding) { screen = AppScreen.HOME }
                    AppScreen.NEW_PROJECT -> NewProjectScreen(
                        padding = padding,
                        onStartFresh = {
                            ProjectStorage.clear(context)
                            screen = AppScreen.EDITOR
                        },
                        onContinueProject = { screen = AppScreen.EDITOR },
                        onBack = { screen = AppScreen.HOME }
                    )
                    else -> PlaceholderScreen(padding, screen) { screen = AppScreen.HOME }
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
                "Phase 3 & 4 multi-clip timeline, trim, split, transforms, and export are available in Editor.",
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

@Composable
private fun NewProjectScreen(
    padding: PaddingValues,
    onStartFresh: () -> Unit,
    onContinueProject: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("New Project", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Start a clean multi-clip timeline or continue editing your autosaved timeline in the Editor."
        )
        Button(onClick = onStartFresh, modifier = Modifier.fillMaxWidth()) {
            Text("Start Fresh Timeline Project")
        }
        OutlinedButton(onClick = onContinueProject, modifier = Modifier.fillMaxWidth()) {
            Text("Continue Saved Project in Editor")
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back to Home")
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun EditorScreen(padding: PaddingValues, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedClipId by remember { mutableStateOf<Long?>(null) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    val timelineClips = remember { mutableStateListOf<TimelineClip>() }
    val undoStack = remember { mutableStateListOf<TimelineSnapshot>() }
    val redoStack = remember { mutableStateListOf<TimelineSnapshot>() }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var trimStartMs by remember { mutableLongStateOf(0L) }
    var trimEndMs by remember { mutableLongStateOf(0L) }
    var playheadMs by remember { mutableLongStateOf(0L) }
    var previewRequest by remember { mutableIntStateOf(0) }
    var exporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableFloatStateOf(0f) }
    var lastExportUri by remember { mutableStateOf<Uri?>(null) }
    var lastExportName by remember { mutableStateOf<String?>(null) }
    var activeTransformer by remember { mutableStateOf<Transformer?>(null) }
    var trimDragUndoRecorded by remember { mutableStateOf(false) }

    DisposableEffect(exporting) {
        onDispose { if (!exporting) activeTransformer?.cancel() }
    }

    fun selectClip(clip: TimelineClip?) {
        if (clip == null) {
            selectedClipId = null
            selectedUri = null
            durationMs = 0L
            trimStartMs = 0L
            trimEndMs = 0L
            playheadMs = 0L
            return
        }
        selectedClipId = clip.id
        selectedUri = clip.uri
        val detectedDuration = if (clip.durationMs > 0L) {
            clip.durationMs
        } else {
            readVideoDurationMs(context, clip.uri)
        }
        durationMs = detectedDuration
        trimStartMs = clip.trimStartMs.coerceAtLeast(0L)
        trimEndMs = if (clip.trimEndMs > clip.trimStartMs) {
            if (detectedDuration > 0L) clip.trimEndMs.coerceAtMost(detectedDuration) else clip.trimEndMs
        } else {
            detectedDuration
        }
        playheadMs = trimStartMs
    }

    fun applySnapshot(snapshot: TimelineSnapshot) {
        timelineClips.clear()
        timelineClips.addAll(snapshot.clips)
        val target = timelineClips.firstOrNull { it.id == snapshot.selectedClipId }
            ?: timelineClips.firstOrNull()
        selectClip(target)
        ProjectStorage.save(context, timelineClips.toList(), selectedClipId)
    }

    fun pushUndoSnapshot() {
        undoStack.add(TimelineSnapshot(timelineClips.toList(), selectedClipId))
        if (undoStack.size > 30) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    fun autosaveCurrentTimeline() {
        ProjectStorage.save(context, timelineClips.toList(), selectedClipId)
    }

    fun updateSelectedClipInTimeline(transform: (TimelineClip) -> TimelineClip) {
        val currentId = selectedClipId ?: return
        val index = timelineClips.indexOfFirst { it.id == currentId }
        if (index < 0) return
        val updated = transform(timelineClips[index])
        timelineClips[index] = updated
        autosaveCurrentTimeline()
    }

    LaunchedEffect(Unit) {
        if (timelineClips.isEmpty()) {
            val restored = withContext(Dispatchers.IO) { ProjectStorage.load(context) }
            if (restored != null && restored.clips.isNotEmpty()) {
                timelineClips.clear()
                timelineClips.addAll(restored.clips)
                val target = timelineClips.firstOrNull { it.id == restored.selectedClipId }
                    ?: timelineClips.firstOrNull()
                selectClip(target)
                statusMessage = "Restored saved project (${restored.clips.size} clip(s))"
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        errorMessage = null
        statusMessage = null
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
            pushUndoSnapshot()
            val detectedDuration = readVideoDurationMs(context, uri)
            val newClip = TimelineClip(
                id = nextClipId(),
                uri = uri,
                durationMs = detectedDuration,
                trimStartMs = 0L,
                trimEndMs = detectedDuration
            )
            timelineClips.add(newClip)
            selectClip(newClip)
            autosaveCurrentTimeline()
        } else {
            errorMessage = "Cannot read this video"
        }
    }

    val multiPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        errorMessage = null
        statusMessage = null
        val readable = uris.filter { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            } catch (_: IllegalArgumentException) {
            }
            isReadableVideoUri(context.contentResolver, uri)
        }
        if (readable.isNotEmpty()) {
            pushUndoSnapshot()
            var firstAdded: TimelineClip? = null
            readable.forEach { uri ->
                val detectedDuration = readVideoDurationMs(context, uri)
                val clip = TimelineClip(
                    id = nextClipId(),
                    uri = uri,
                    durationMs = detectedDuration,
                    trimStartMs = 0L,
                    trimEndMs = detectedDuration
                )
                timelineClips.add(clip)
                if (firstAdded == null) firstAdded = clip
            }
            if (selectedClipId == null && firstAdded != null) {
                selectClip(firstAdded)
            }
            autosaveCurrentTimeline()
            statusMessage = "Added ${readable.size} video(s) to timeline"
        } else if (uris.isNotEmpty()) {
            errorMessage = "Cannot read the selected videos"
        }
    }

    fun startVideoExport(clipsToExport: List<TimelineClip>, exportPrefix: String) {
        if (clipsToExport.isEmpty()) {
            errorMessage = "Select or add at least one readable video first"
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            errorMessage = "Trim export to public Movies requires Android 10 or newer"
            return
        }

        val unreadable = clipsToExport.firstOrNull { !isReadableVideoUri(context.contentResolver, it.uri) }
        if (unreadable != null) {
            errorMessage = "One or more timeline videos cannot be read"
            return
        }

        val editedItems = mutableListOf<EditedMediaItem>()
        for (clip in clipsToExport) {
            val clipDur = if (clip.durationMs > 0L) clip.durationMs else readVideoDurationMs(context, clip.uri)
            val start = clip.trimStartMs.coerceAtLeast(0L)
            val end = if (clip.trimEndMs > start) {
                if (clipDur > 0L) clip.trimEndMs.coerceAtMost(clipDur) else clip.trimEndMs
            } else {
                clipDur
            }
            if (end <= start) {
                errorMessage = "Start time must be before end time for all exported clips"
                return
            }

            val mediaItem = MediaItem.Builder()
                .setUri(clip.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(start)
                        .setEndPositionMs(end)
                        .build()
                )
                .build()

            val videoEffects = buildClipVideoEffects(clip)
            val editedBuilder = EditedMediaItem.Builder(mediaItem)
            if (videoEffects.isNotEmpty()) {
                editedBuilder.setEffects(Effects(emptyList<AudioProcessor>(), videoEffects))
            }
            editedItems.add(editedBuilder.build())
        }

        errorMessage = null
        statusMessage = null
        exporting = true
        exportProgress = 0f
        lastExportUri = null
        lastExportName = null

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "${exportPrefix}_${stamp}.mp4"
        val tempFile = File(
            context.cacheDir,
            "edit_sai_export_" + System.currentTimeMillis() + ".mp4"
        )

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
                                "Phase 3/4 export save failed"
                            )
                            errorMessage = "Export finished but could not save the video"
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
                        "Phase 3/4 video export failed"
                    )
                    errorMessage = "Export failed: " + (exportException.message ?: "unsupported video format")
                }
            })
            .build()

        activeTransformer = transformer
        try {
            if (editedItems.size == 1) {
                transformer.start(editedItems.first(), tempFile.absolutePath)
            } else {
                val sequence = EditedMediaItemSequence(editedItems)
                val composition = Composition.Builder(listOf(sequence))
                    .experimentalSetForceAudioTrack(true)
                    .build()
                transformer.start(composition, tempFile.absolutePath)
            }
        } catch (e: Exception) {
            tempFile.delete()
            activeTransformer = null
            exporting = false
            CrashLogger.logNonFatal(
                context,
                e,
                "Phase 3/4 video export could not start"
            )
            errorMessage = "Could not start video export"
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

    val activeClip = timelineClips.firstOrNull { it.id == selectedClipId }

    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { picker.launch(arrayOf("video/*")) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !exporting
            ) {
                Text(if (selectedUri == null) "Select Video" else "Select Another Video")
            }

            Button(
                onClick = { multiPicker.launch(arrayOf("video/*")) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !exporting
            ) {
                Text("Add Multiple Videos to Timeline")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (undoStack.isNotEmpty()) {
                            val current = TimelineSnapshot(timelineClips.toList(), selectedClipId)
                            redoStack.add(current)
                            val previous = undoStack.removeAt(undoStack.lastIndex)
                            applySnapshot(previous)
                            statusMessage = "Undid last edit"
                        }
                    },
                    enabled = !exporting && undoStack.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Undo (${undoStack.size})")
                }
                OutlinedButton(
                    onClick = {
                        if (redoStack.isNotEmpty()) {
                            val current = TimelineSnapshot(timelineClips.toList(), selectedClipId)
                            undoStack.add(current)
                            val next = redoStack.removeAt(redoStack.lastIndex)
                            applySnapshot(next)
                            statusMessage = "Redid edit"
                        }
                    },
                    enabled = !exporting && redoStack.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Redo (${redoStack.size})")
                }
            }

            statusMessage?.let { msg ->
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (timelineClips.isNotEmpty()) {
                val totalTimelineMs = timelineClips.fold(0L) { acc, clip ->
                    val end = if (clip.trimEndMs > clip.trimStartMs) clip.trimEndMs else clip.durationMs
                    acc + (end - clip.trimStartMs).coerceAtLeast(0L)
                }
                Text(
                    "Timeline (${timelineClips.size} clips • Total ${formatDuration(totalTimelineMs)})",
                    style = MaterialTheme.typography.titleMedium
                )
                timelineClips.forEachIndexed { index, clip ->
                    val isSelected = clip.id == selectedClipId
                    val clipEnd = if (clip.trimEndMs > clip.trimStartMs) clip.trimEndMs else clip.durationMs
                    val clipRangeLabel = if (clipEnd > 0L) {
                        " (${formatDuration(clip.trimStartMs)}-${formatDuration(clipEnd)})"
                    } else {
                        ""
                    }
                    val transformBadge = buildTransformBadge(clip)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                errorMessage = null
                                selectClip(clip)
                            },
                            enabled = !exporting,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "Clip ${index + 1}$clipRangeLabel$transformBadge${if (isSelected) " •" else ""}"
                            )
                        }
                        Button(
                            onClick = {
                                pushUndoSnapshot()
                                val duplicated = clip.copy(id = nextClipId())
                                timelineClips.add(index + 1, duplicated)
                                autosaveCurrentTimeline()
                            },
                            enabled = !exporting
                        ) { Text("Dup") }
                        Button(
                            onClick = {
                                if (index > 0) {
                                    pushUndoSnapshot()
                                    val item = timelineClips.removeAt(index)
                                    timelineClips.add(index - 1, item)
                                    autosaveCurrentTimeline()
                                }
                            },
                            enabled = !exporting && index > 0
                        ) { Text("↑") }
                        Button(
                            onClick = {
                                if (index < timelineClips.lastIndex) {
                                    pushUndoSnapshot()
                                    val item = timelineClips.removeAt(index)
                                    timelineClips.add(index + 1, item)
                                    autosaveCurrentTimeline()
                                }
                            },
                            enabled = !exporting && index < timelineClips.lastIndex
                        ) { Text("↓") }
                        Button(
                            onClick = {
                                pushUndoSnapshot()
                                val removedId = clip.id
                                timelineClips.removeAt(index)
                                if (selectedClipId == removedId) {
                                    selectClip(timelineClips.firstOrNull())
                                }
                                autosaveCurrentTimeline()
                            },
                            enabled = !exporting
                        ) { Text("Del") }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            autosaveCurrentTimeline()
                            statusMessage = "Project saved (${timelineClips.size} clip(s))"
                        },
                        enabled = !exporting,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Project")
                    }
                    OutlinedButton(
                        onClick = {
                            pushUndoSnapshot()
                            timelineClips.clear()
                            selectClip(null)
                            ProjectStorage.clear(context)
                            statusMessage = "Timeline cleared"
                        },
                        enabled = !exporting,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear Timeline")
                    }
                }

                Text(
                    "Timeline is autosaved on your device. Select any clip to trim, split, rotate/flip/crop, or export.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            selectedUri?.let { uri ->
                errorMessage?.let { message ->
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                VideoPlayer(
                    uri = uri,
                    clipId = selectedClipId ?: 0L,
                    trimStartMs = trimStartMs,
                    trimEndMs = trimEndMs,
                    rotationDegrees = activeClip?.rotationDegrees ?: 0,
                    flipHorizontal = activeClip?.flipHorizontal ?: false,
                    flipVertical = activeClip?.flipVertical ?: false,
                    cropPreset = activeClip?.cropPreset ?: "NONE",
                    previewRequest = previewRequest,
                    onPreviewFinished = {},
                    onPositionChanged = { pos -> playheadMs = pos },
                    onDurationChanged = { newDuration ->
                        if (newDuration != durationMs && newDuration > 0L) {
                            durationMs = newDuration
                            if (trimEndMs <= 0L || trimEndMs > newDuration) {
                                trimEndMs = newDuration
                            }
                            if (trimStartMs >= trimEndMs && newDuration > 1000L) {
                                trimStartMs = 0L
                                trimEndMs = newDuration
                            }
                            updateSelectedClipInTimeline { current ->
                                current.copy(
                                    durationMs = newDuration,
                                    trimStartMs = trimStartMs,
                                    trimEndMs = trimEndMs
                                )
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

            if (selectedUri != null && durationMs <= 0L && errorMessage == null) {
                Text(
                    "Preparing Trim & Export controls…",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (selectedUri != null && durationMs > 0L) {
                val safeEnd = trimEndMs.coerceIn(1L, durationMs)
                val validTrim = trimStartMs < safeEnd

                Text("Trim & Split Controls", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Start " + formatDuration(trimStartMs) +
                        "    Playhead " + formatDuration(playheadMs) +
                        "    End " + formatDuration(safeEnd)
                )

                Text("Start")
                Slider(
                    value = trimStartMs.toFloat().coerceIn(
                        0f,
                        (safeEnd - 100L).coerceAtLeast(0L).toFloat()
                    ),
                    onValueChange = {
                        if (!trimDragUndoRecorded) {
                            pushUndoSnapshot()
                            trimDragUndoRecorded = true
                        }
                        trimStartMs = it.toLong().coerceIn(
                            0L,
                            (safeEnd - 100L).coerceAtLeast(0L)
                        )
                        updateSelectedClipInTimeline { clip ->
                            clip.copy(
                                durationMs = durationMs,
                                trimStartMs = trimStartMs,
                                trimEndMs = safeEnd
                            )
                        }
                    },
                    onValueChangeFinished = {
                        trimDragUndoRecorded = false
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
                        if (!trimDragUndoRecorded) {
                            pushUndoSnapshot()
                            trimDragUndoRecorded = true
                        }
                        trimEndMs = it.toLong().coerceIn(
                            (trimStartMs + 100L).coerceAtMost(durationMs),
                            durationMs
                        )
                        updateSelectedClipInTimeline { clip ->
                            clip.copy(
                                durationMs = durationMs,
                                trimStartMs = trimStartMs,
                                trimEndMs = trimEndMs
                            )
                        }
                    },
                    onValueChangeFinished = {
                        trimDragUndoRecorded = false
                    },
                    valueRange = 0f..durationMs.toFloat(),
                    enabled = !exporting
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { if (validTrim) previewRequest++ },
                        modifier = Modifier.weight(1f),
                        enabled = validTrim && !exporting
                    ) {
                        Text("Preview Selection")
                    }

                    Button(
                        onClick = {
                            val currentId = selectedClipId
                            val index = timelineClips.indexOfFirst { it.id == currentId }
                            if (index < 0) return@Button
                            val clip = timelineClips[index]
                            val splitAt = playheadMs
                            if (splitAt <= trimStartMs + 200L || splitAt >= safeEnd - 200L) {
                                errorMessage =
                                    "Move the player slider between Start and End (at least 0.2s inside) to split at playhead"
                                return@Button
                            }
                            errorMessage = null
                            pushUndoSnapshot()
                            val firstPart = clip.copy(
                                durationMs = durationMs,
                                trimStartMs = trimStartMs,
                                trimEndMs = splitAt
                            )
                            val secondPart = clip.copy(
                                id = nextClipId(),
                                durationMs = durationMs,
                                trimStartMs = splitAt,
                                trimEndMs = safeEnd
                            )
                            timelineClips[index] = firstPart
                            timelineClips.add(index + 1, secondPart)
                            selectClip(firstPart)
                            autosaveCurrentTimeline()
                            statusMessage =
                                "Split clip at ${formatDuration(splitAt)} into Clip ${index + 1} and Clip ${index + 2}"
                        },
                        modifier = Modifier.weight(1f),
                        enabled = validTrim && !exporting && timelineClips.isNotEmpty()
                    ) {
                        Text("Split at Playhead")
                    }
                }

                if (activeClip != null) {
                    Text("Transform & Crop (Selected Clip)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Rotation: ${activeClip.rotationDegrees}° • " +
                            "Flip H: ${if (activeClip.flipHorizontal) "On" else "Off"} • " +
                            "Flip V: ${if (activeClip.flipVertical) "On" else "Off"} • " +
                            "Crop: ${activeClip.cropPreset}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                pushUndoSnapshot()
                                updateSelectedClipInTimeline { clip ->
                                    clip.copy(rotationDegrees = (clip.rotationDegrees + 90) % 360)
                                }
                            },
                            enabled = !exporting,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Rotate 90°")
                        }
                        OutlinedButton(
                            onClick = {
                                pushUndoSnapshot()
                                updateSelectedClipInTimeline { clip ->
                                    clip.copy(flipHorizontal = !clip.flipHorizontal)
                                }
                            },
                            enabled = !exporting,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Flip H")
                        }
                        OutlinedButton(
                            onClick = {
                                pushUndoSnapshot()
                                updateSelectedClipInTimeline { clip ->
                                    clip.copy(flipVertical = !clip.flipVertical)
                                }
                            },
                            enabled = !exporting,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Flip V")
                        }
                        OutlinedButton(
                            onClick = {
                                pushUndoSnapshot()
                                updateSelectedClipInTimeline { clip ->
                                    clip.copy(
                                        rotationDegrees = 0,
                                        flipHorizontal = false,
                                        flipVertical = false,
                                        cropPreset = "NONE"
                                    )
                                }
                            },
                            enabled = !exporting,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset")
                        }
                    }

                    val cropPresets = listOf("NONE", "16:9", "9:16", "1:1")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        cropPresets.forEach { preset ->
                            val isCurrent = activeClip.cropPreset == preset
                            if (isCurrent) {
                                Button(
                                    onClick = {},
                                    enabled = !exporting,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (preset == "NONE") "Full" else preset)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        pushUndoSnapshot()
                                        updateSelectedClipInTimeline { clip ->
                                            clip.copy(cropPreset = preset)
                                        }
                                    },
                                    enabled = !exporting,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (preset == "NONE") "Full" else preset)
                                }
                            }
                        }
                    }
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
                        val clipToExport = activeClip?.copy(
                            durationMs = durationMs,
                            trimStartMs = trimStartMs,
                            trimEndMs = end
                        ) ?: TimelineClip(
                            id = nextClipId(),
                            uri = uri,
                            durationMs = durationMs,
                            trimStartMs = trimStartMs,
                            trimEndMs = end
                        )
                        startVideoExport(listOf(clipToExport), "EDIT_SAI_trim")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = validTrim && !exporting
                ) {
                    Text("Export Trimmed Video (Selected Clip)")
                }

                if (timelineClips.size > 1) {
                    Button(
                        onClick = {
                            startVideoExport(timelineClips.toList(), "EDIT_SAI_timeline")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !exporting
                    ) {
                        Text("Export Full Timeline (${timelineClips.size} Clips Merged)")
                    }
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

@OptIn(UnstableApi::class)
private fun buildClipVideoEffects(clip: TimelineClip): List<Effect> {
    val effects = mutableListOf<Effect>()
    val normRot = ((clip.rotationDegrees % 360) + 360) % 360
    if (normRot != 0 || clip.flipHorizontal || clip.flipVertical) {
        val ccwDegrees = ((360 - normRot) % 360).toFloat()
        val scaleX = if (clip.flipHorizontal) -1f else 1f
        val scaleY = if (clip.flipVertical) -1f else 1f
        effects.add(
            ScaleAndRotateTransformation.Builder()
                .setScale(scaleX, scaleY)
                .setRotationDegrees(ccwDegrees)
                .build()
        )
    }
    val cropRatio = when (clip.cropPreset) {
        "16:9" -> 16f / 9f
        "9:16" -> 9f / 16f
        "1:1" -> 1f
        else -> null
    }
    if (cropRatio != null) {
        effects.add(
            Presentation.createForAspectRatio(
                cropRatio,
                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            )
        )
    }
    return effects
}

private fun buildTransformBadge(clip: TimelineClip): String {
    val tags = mutableListOf<String>()
    if (clip.rotationDegrees != 0) tags.add("${clip.rotationDegrees}°")
    if (clip.flipHorizontal) tags.add("FlipH")
    if (clip.flipVertical) tags.add("FlipV")
    if (clip.cropPreset != "NONE") tags.add(clip.cropPreset)
    return if (tags.isEmpty()) "" else " [${tags.joinToString(",")}]"
}

private fun readVideoDurationMs(context: Context, uri: Uri): Long {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?.coerceAtLeast(0L)
            ?: 0L
    } catch (_: Exception) {
        0L
    } finally {
        runCatching { retriever.release() }
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
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(
    uri: Uri,
    clipId: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    rotationDegrees: Int,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    cropPreset: String,
    previewRequest: Int,
    onPreviewFinished: () -> Unit,
    onPositionChanged: (Long) -> Unit,
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
                        onPositionChanged(currentPosition)
                        if (duration > 0L) onDurationChanged(duration)
                    }
                }

                override fun onTimelineChanged(
                    timeline: androidx.media3.common.Timeline,
                    reason: Int
                ) {
                    val timelineDuration = newPlayer.duration.coerceAtLeast(0L)
                    if (timelineDuration > 0L) {
                        duration = timelineDuration
                        onDurationChanged(timelineDuration)
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

    LaunchedEffect(activePlayer, clipId) {
        if (activePlayer == null) return@LaunchedEffect
        previewing = false
        val targetPos = trimStartMs.coerceAtLeast(0L)
        activePlayer.seekTo(targetPos)
        currentPosition = targetPos
        onPositionChanged(targetPos)
        if (duration > 0L) {
            sliderPosition = (targetPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        }
    }

    LaunchedEffect(activePlayer) {
        if (activePlayer == null) return@LaunchedEffect

        while (isActive) {
            currentPosition = activePlayer.currentPosition.coerceAtLeast(0L)
            duration = activePlayer.duration.coerceAtLeast(0L)
            onPositionChanged(currentPosition)

            if (duration > 0L) {
                sliderPosition =
                    (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                onDurationChanged(duration)
            }

            delay(200L)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .graphicsLayer(
                        rotationZ = rotationDegrees.toFloat(),
                        scaleX = if (flipHorizontal) -1f else 1f,
                        scaleY = if (flipVertical) -1f else 1f
                    )
            )

            if (cropPreset != "NONE" || rotationDegrees != 0 || flipHorizontal || flipVertical) {
                Text(
                    "Active preview transform: Rotation ${rotationDegrees}° • " +
                        "Flip H ${if (flipHorizontal) "On" else "Off"} • " +
                        "Flip V ${if (flipVertical) "On" else "Off"} • " +
                        "Crop $cropPreset",
                    style = MaterialTheme.typography.bodySmall
                )
            }

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
                    if (duration > 0L) {
                        val previewPos = (it * duration).toLong()
                        currentPosition = previewPos
                        onPositionChanged(previewPos)
                    }
                },
                onValueChangeFinished = {
                    if (duration > 0L) {
                        val target = (sliderPosition * duration).toLong()
                        activePlayer.seekTo(target)
                        currentPosition = target
                        onPositionChanged(target)
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
