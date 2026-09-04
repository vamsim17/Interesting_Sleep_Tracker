package com.example.interesting_sleep_tracker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.interesting_sleep_tracker.data.SleepRepository
import com.example.interesting_sleep_tracker.model.Phase
import com.example.interesting_sleep_tracker.model.SleepState
import com.example.interesting_sleep_tracker.service.SleepNotifications
import com.example.interesting_sleep_tracker.service.SleepTrackingService
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {},
) {
    val context = LocalContext.current
    val state by SleepRepository.state.collectAsStateWithLifecycle()

    var notificationsAllowed by remember {
        mutableStateOf(hasNotificationPermission(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsAllowed = granted
        if (granted) SleepTrackingService.start(context)
    }

    var sliderValue by remember { mutableFloatStateOf(state.intervalMinutes.toFloat()) }
    LaunchedEffect(state.intervalMinutes) { sliderValue = state.intervalMinutes.toFloat() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Sleep Tracker",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            SubtleThemeToggle(
                isDarkTheme = isDarkTheme,
                onToggle = onToggleTheme,
            )
        }
        Spacer(Modifier.height(24.dp))

        StatusCard(state)
        Spacer(Modifier.height(24.dp))

        IntervalCard(
            sliderValue = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                val minutes = SleepState.coerceInterval(sliderValue.roundToInt())
                sliderValue = minutes.toFloat()
                SleepRepository.update { it.copy(intervalMinutes = minutes) }
                if (state.isSessionRunning) SleepTrackingService.setInterval(context, minutes)
            },
        )
        Spacer(Modifier.height(32.dp))

        if (state.isSessionRunning) {
            PrimaryButton(
                label = "I'm awake",
                container = MaterialTheme.colorScheme.secondary,
            ) { SleepTrackingService.wake(context) }
        } else {
            PrimaryButton(
                label = "I am going to bed",
                container = MaterialTheme.colorScheme.primary,
            ) {
                if (hasNotificationPermission(context)) {
                    SleepTrackingService.start(context)
                } else {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        if ((!notificationsAllowed) && (!state.isSessionRunning) &&
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Allow notifications so the app can wake you for each check-in.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusCard(state: SleepState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state.phase) {
                Phase.TRACKING, Phase.AWAITING_ANSWER -> {
                    Text("Tracking your sleep", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(16.dp))
                    BigMetric(SleepNotifications.formatDuration(state.sleepMinutes), "sleep time")
                    Spacer(Modifier.height(12.dp))
                    BigMetric(state.score.toString(), "score / 100")
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "${state.asleepCount} of ${state.totalAnswers} check-ins asleep",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.phase == Phase.AWAITING_ANSWER) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Waiting for your answer…",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Phase.FINISHED, Phase.IDLE -> {
                    if ((state.lastSessionMinutes != null) && (state.lastSessionScore != null)) {
                        Text(
                            if (state.phase == Phase.FINISHED) "Session complete" else "Last Session",
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(16.dp))
                        BigMetric(
                            SleepNotifications.formatDuration(state.lastSessionMinutes),
                            "sleep time",
                        )
                        Spacer(Modifier.height(12.dp))
                        BigMetric(state.lastSessionScore.toString(), "score / 100")
                    } else {
                        Text("No sleep recorded yet", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Set your check-in interval, then tap “I am going to bed”.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BigMetric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun IntervalCard(
    sliderValue: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Check-in every", fontWeight = FontWeight.SemiBold)
                Text("${sliderValue.roundToInt()} min", fontWeight = FontWeight.SemiBold)
            }
            Slider(
                value = sliderValue,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = SleepState.MIN_INTERVAL_MINUTES.toFloat()..
                    SleepState.MAX_INTERVAL_MINUTES.toFloat(),
            )
            Text(
                "Adjustable any time – changes apply from the next check-in.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PrimaryButton(label: String, container: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container),
    ) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun hasNotificationPermission(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}

@Composable
fun SubtleThemeToggle(
    isDarkTheme: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onToggle,
        modifier = modifier.size(36.dp),
    ) {
        Text(
            text = if (isDarkTheme) "☼" else "☾",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
        )
    }
}
