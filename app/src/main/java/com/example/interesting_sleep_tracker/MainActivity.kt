package com.example.interesting_sleep_tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.interesting_sleep_tracker.ui.HomeScreen
import com.example.interesting_sleep_tracker.ui.theme.AppTheme
import com.example.interesting_sleep_tracker.ui.theme.Interesting_Sleep_TrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var selectedTheme by rememberSaveable { mutableStateOf(AppTheme.DARK) }

            Interesting_Sleep_TrackerTheme(appTheme = selectedTheme) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreen(
                        selectedTheme = selectedTheme,
                        onSelectTheme = { selectedTheme = it },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
