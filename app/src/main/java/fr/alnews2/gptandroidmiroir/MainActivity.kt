package fr.alnews2.gptandroidmiroir

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.pedro.library.view.OpenGlView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CameraApp(onQuit = { finishAndRemoveTask() })
        }
    }
}

@Composable
private fun CameraApp(onQuit: () -> Unit) {
    val context = LocalContext.current
    val store = remember { RtspConfigurationStore(context) }
    var configuration by remember { mutableStateOf(store.load()) }
    var cameraSelection by remember { mutableStateOf(CameraSelection.REAR) }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var showRtspConfiguration by remember { mutableStateOf(false) }
    var streamError by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!permissionGranted) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Autorisation caméra requise",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }

    val rearView = remember { OpenGlView(context) }
    val frontView = remember { OpenGlView(context) }
    val streamManager = remember { RtspStreamManager(context, rearView, frontView) }

    LaunchedEffect(configuration) {
        val errors = configuration.validate()
        if (errors.isEmpty()) {
            streamManager.start(configuration)
            streamError = streamManager.lastError
        } else {
            streamError = errors.joinToString("\n")
        }
    }

    DisposableEffect(Unit) {
        onDispose { streamManager.stop() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(
            selection = cameraSelection,
            rearView = rearView,
            frontView = frontView,
            modifier = Modifier.fillMaxSize()
        )

        if (streamError != null) {
            Text(
                text = streamError!!,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }

        CameraMenu(
            onCameraSelected = { cameraSelection = it },
            onConfigureRtsp = { showRtspConfiguration = true },
            onQuit = onQuit,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding()
        )
    }

    if (showRtspConfiguration) {
        RtspConfigurationDialog(
            initialConfiguration = configuration,
            onDismiss = { showRtspConfiguration = false },
            onSave = { newConfiguration ->
                val errors = newConfiguration.validate()
                if (errors.isEmpty()) {
                    store.save(newConfiguration)
                    configuration = newConfiguration
                    showRtspConfiguration = false
                } else {
                    streamError = errors.joinToString("\n")
                }
            }
        )
    }
}

@Composable
private fun CameraMenu(
    onCameraSelected: (CameraSelection) -> Unit,
    onConfigureRtsp: () -> Unit,
    onQuit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = Color.White
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Caméra avant") },
                onClick = {
                    expanded = false
                    onCameraSelected(CameraSelection.FRONT)
                }
            )
            DropdownMenuItem(
                text = { Text("Caméra arrière") },
                onClick = {
                    expanded = false
                    onCameraSelected(CameraSelection.REAR)
                }
            )
            DropdownMenuItem(
                text = { Text("Configurer RTSP") },
                onClick = {
                    expanded = false
                    onConfigureRtsp()
                }
            )
            DropdownMenuItem(
                text = { Text("Quitter l'application") },
                onClick = {
                    expanded = false
                    onQuit()
                }
            )
        }
    }
}

@Composable
private fun CameraPreview(
    selection: CameraSelection,
    rearView: OpenGlView,
    frontView: OpenGlView,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        AndroidView(
            factory = { rearView },
            modifier = Modifier.fillMaxSize().then(
                if (selection == CameraSelection.REAR) Modifier else Modifier
            )
        )
        AndroidView(
            factory = { frontView },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun RtspConfigurationDialog(
    initialConfiguration: RtspConfiguration,
    onDismiss: () -> Unit,
    onSave: (RtspConfiguration) -> Unit
) {
    var rearEnabled by remember { mutableStateOf(initialConfiguration.rearEnabled) }
    var frontEnabled by remember { mutableStateOf(initialConfiguration.frontEnabled) }
    var rearPort by remember { mutableStateOf(initialConfiguration.rearPort.toString()) }
    var frontPort by remember { mutableStateOf(initialConfiguration.frontPort.toString()) }
    var width by remember { mutableStateOf(initialConfiguration.width.toString()) }
    var height by remember { mutableStateOf(initialConfiguration.height.toString()) }
    var fps by remember { mutableStateOf(initialConfiguration.fps.toString()) }
    var bitrate by remember { mutableStateOf((initialConfiguration.bitrate / 1_000_000.0).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurer RTSP") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Flux caméra arrière")
                    Switch(checked = rearEnabled, onCheckedChange = { rearEnabled = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Flux caméra avant")
                    Switch(checked = frontEnabled, onCheckedChange = { frontEnabled = it })
                }
                OutlinedTextField(
                    value = rearPort,
                    onValueChange = { rearPort = it },
                    label = { Text("Port arrière") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = frontPort,
                    onValueChange = { frontPort = it },
                    label = { Text("Port avant") },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = width,
                        onValueChange = { width = it },
                        label = { Text("Largeur") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = height,
                        onValueChange = { height = it },
                        label = { Text("Hauteur") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = fps,
                    onValueChange = { fps = it },
                    label = { Text("FPS") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = bitrate,
                    onValueChange = { bitrate = it },
                    label = { Text("Débit vidéo (Mbit/s)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        RtspConfiguration(
                            rearEnabled = rearEnabled,
                            frontEnabled = frontEnabled,
                            rearPort = rearPort.toIntOrNull() ?: -1,
                            frontPort = frontPort.toIntOrNull() ?: -1,
                            width = width.toIntOrNull() ?: -1,
                            height = height.toIntOrNull() ?: -1,
                            fps = fps.toIntOrNull() ?: -1,
                            bitrate = ((bitrate.toDoubleOrNull() ?: -1.0) * 1_000_000).toInt()
                        )
                    )
                }
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
