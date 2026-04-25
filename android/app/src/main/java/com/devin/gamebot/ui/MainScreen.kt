package com.devin.gamebot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devin.gamebot.BuildConfig

@Composable
fun MainScreen(
    onRequestCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onOpenAccessibility: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("AI Game Bot — Phase 0", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Mục tiêu Phase 0: verify capture + accessibility hoạt động trên thiết bị thật.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("Backend: ${BuildConfig.BACKEND_URL}", style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(8.dp))
            Text("1. Cấp quyền Accessibility (làm 1 lần)", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onOpenAccessibility) {
                Text("Mở Accessibility Settings")
            }

            Spacer(Modifier.height(8.dp))
            Text("2. Test capture màn hình", style = MaterialTheme.typography.titleMedium)
            Text(
                "Bấm Start → cấp quyền MediaProjection → switch sang game → app sẽ lưu " +
                    "screenshot vào /sdcard/Pictures/AIGameBot/. Mở file để verify FLAG_SECURE.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onRequestCapture) { Text("Start capture") }
            OutlinedButton(onClick = onStopCapture) { Text("Stop capture") }
        }
    }
}
