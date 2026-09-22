package com.saigro.editsai

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
                    Text(
                        "Last crash log available\n$lastCrashLog",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis
                    )
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

@Composable
private fun EditorScreen(padding: PaddingValues, onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        errorMessage = null
        selectedUri = null
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

    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = { picker.launch(arrayOf("video/*")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (selectedUri == null) "Select Video" else "Select Another Video")
        }

        selectedUri?.let { uri ->
            VideoPlayer(uri = uri, onError = {
                selectedUri = null
                errorMessage = it
            })
        } ?: Text(
            errorMessage ?: "Choose a video from your device to start playback.",
            style = MaterialTheme.typography.bodyLarge,
            color = if (errorMessage != null) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onBackground
        )

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back to Home")
        }
    }
}

private fun isReadableVideoUri(contentResolver: ContentResolver, uri: Uri): Boolean {
    return try {
        contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
    } catch (_: Exception) {
        false
    }
}

@Composable
private fun VideoPlayer(uri: Uri, onError: (String) -> Unit) {
    val context = LocalContext.current
    var player by remember(uri) { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember(uri) { mutableStateOf(false) }
    var currentPosition by remember(uri) { mutableLongStateOf(0L) }
    var duration by remember(uri) { mutableLongStateOf(0L) }
    var sliderPosition by remember(uri) { mutableFloatStateOf(0f) }

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
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    player = null
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
            }
            delay(250L)
        }
    }

    if (activePlayer != null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AndroidView(
                factory = { PlayerView(it).apply { player = activePlayer; useController = false } },
                update = { it.player = activePlayer },
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
            )
            Button(
                onClick = {
                    if (activePlayer.isPlaying) activePlayer.pause() else activePlayer.play()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isPlaying) "Pause" else "Play")
            }
            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it },
                onValueChangeFinished = {
                    if (duration > 0L) activePlayer.seekTo((sliderPosition * duration).toLong())
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
