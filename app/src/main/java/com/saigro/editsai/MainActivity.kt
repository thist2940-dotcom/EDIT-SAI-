package com.saigro.editsai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
@androidx.compose.runtime.Composable
private fun EditSaiApp() {
    var screen by remember { mutableStateOf(AppScreen.HOME) }
    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text(screen.label) }) }
        ) { padding ->
            if (screen == AppScreen.HOME) {
                HomeScreen(padding) { screen = it }
            } else {
                PlaceholderScreen(padding, screen) { screen = AppScreen.HOME }
            }
        }
    }
}

@androidx.compose.runtime.Composable
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
                "Phase 1 foundation is ready. Editing tools will be added in later phases.",
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
            )
        }
        items(actions) { action ->
            Button(
                onClick = { onOpen(action) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(action.label) }
        }
    }
}

@androidx.compose.runtime.Composable
private fun PlaceholderScreen(
    padding: PaddingValues,
    screen: AppScreen,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(screen.label, style = MaterialTheme.typography.headlineMedium)
        Text("This Phase 1 placeholder is ready for a future feature implementation.")
        Button(onClick = onBack) { Text("Back to Home") }
    }
}
