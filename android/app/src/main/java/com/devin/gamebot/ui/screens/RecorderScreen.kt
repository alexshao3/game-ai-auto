package com.devin.gamebot.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devin.gamebot.service.RecorderService
import com.devin.gamebot.ui.vm.RecorderViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecorderScreen(
    onBack: () -> Unit,
    onRecipeReady: (Long) -> Unit,
    vm: RecorderViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    var sessionName by remember { mutableStateOf("") }

    val pendingStart = remember { mutableStateOf<Pair<Long, File>?>(null) }
    val mpm = remember { context.getSystemService(MediaProjectionManager::class.java) }

    val projectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val pending = pendingStart.value
        if (result.resultCode == Activity.RESULT_OK && result.data != null && pending != null) {
            RecorderService.start(
                context = context,
                resultCode = result.resultCode,
                data = result.data!!,
                sessionId = pending.first,
                framesDir = pending.second,
            )
            vm.sessionStarted()
        } else {
            vm.reset()
        }
        pendingStart.value = null
    }

    LaunchedEffect(state) {
        if (state is RecorderViewModel.State.Done) {
            onRecipeReady((state as RecorderViewModel.State.Done).recipeId)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Record task") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Đặt tên task, bấm Start, mở game và làm task 1 lần. Bấm Stop khi xong.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = sessionName,
                onValueChange = { sessionName = it },
                label = { Text("Task name (vd. Nhận quà đăng nhập)") },
                enabled = state is RecorderViewModel.State.Idle,
            )
            Spacer(Modifier.height(8.dp))

            when (val s = state) {
                RecorderViewModel.State.Idle -> {
                    Button(
                        onClick = {
                            vm.startSession(sessionName.ifBlank { "Untitled" }) { sessionId, dir ->
                                pendingStart.value = sessionId to dir
                                projectionLauncher.launch(mpm.createScreenCaptureIntent())
                            }
                        },
                        enabled = sessionName.isNotBlank(),
                    ) { Text("Start recording") }
                    OutlinedButton(onClick = onBack) { Text("Cancel") }
                }
                is RecorderViewModel.State.Preparing -> {
                    Text("Đang chờ cấp quyền MediaProjection…")
                }
                is RecorderViewModel.State.Recording -> {
                    Text(
                        "Đang record: ${s.name}\nMở game và làm task. Sau đó bấm Stop ở đây hoặc trên notification.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = {
                        RecorderService.stop(context)
                        vm.stopAndAnalyse()
                    }) { Text("Stop & Analyse") }
                }
                is RecorderViewModel.State.Generating -> {
                    Text("Đang sinh recipe (gửi frames cho Gemini)…")
                    Spacer(Modifier.height(8.dp))
                    CircularProgressIndicator()
                }
                is RecorderViewModel.State.Done -> {
                    Text("Recipe đã sinh. Đang mở…")
                }
                is RecorderViewModel.State.Error -> {
                    Text(
                        "Lỗi: ${s.message}",
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = { vm.reset() }) { Text("Try again") }
                }
            }
        }
    }
}
