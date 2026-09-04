package com.example.interesting_sleep_tracker.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.interesting_sleep_tracker.R
import com.example.interesting_sleep_tracker.service.SleepTrackingService
import kotlinx.coroutines.delay

/**
 * Full-screen prompt shown when an interval fires. Deliberately a plain bright-white screen so it
 * is unmistakable at night. Shows over the lock screen and turns the display on.
 */
class PromptActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        // The prompt must be answered, not dismissed: swallow back / back-gesture.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        val deadline = intent.getLongExtra(EXTRA_DEADLINE, 0L)

        setContent {
            val remainingMs by produceState(
                initialValue = (deadline - System.currentTimeMillis()).coerceAtLeast(0L),
                key1 = deadline,
            ) {
                while (true) {
                    value = (deadline - System.currentTimeMillis()).coerceAtLeast(0L)
                    if (deadline > 0L && value <= 0L) break
                    delay(500L)
                }
            }

            // The service's timeout alarm owns the reset; we just close when the window passes.
            LaunchedEffect(remainingMs) {
                if (deadline > 0L && remainingMs <= 0L) finish()
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.prompt_question),
                    color = Color.Black,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                if (deadline > 0L) {
                    Text(
                        text = stringResource(R.string.prompt_countdown, formatMs(remainingMs)),
                        color = Color(0xFF666666),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(48.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    PromptButton(
                        label = stringResource(R.string.answer_asleep),
                        container = Color(0xFF1B1B3A),
                        content = Color.White,
                    ) { answer(true) }
                    PromptButton(
                        label = stringResource(R.string.answer_not_asleep),
                        container = Color(0xFFE0E0E0),
                        content = Color.Black,
                    ) { answer(false) }
                }
            }
        }
    }

    private fun answer(asleep: Boolean) {
        SleepTrackingService.answer(this, asleep)
        finish()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        private const val EXTRA_DEADLINE = "extra_deadline"

        fun intent(context: Context, deadlineEpoch: Long): Intent =
            Intent(context, PromptActivity::class.java)
                .putExtra(EXTRA_DEADLINE, deadlineEpoch)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

@Composable
private fun RowScope.PromptButton(
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f).height(72.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}
