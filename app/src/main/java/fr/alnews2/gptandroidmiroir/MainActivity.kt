package fr.alnews2.gptandroidmiroir

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner

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
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraSelection by remember { mutableStateOf(CameraSelection.REAR) }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

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

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (permissionGranted) {
            CameraPreview(
                selection = cameraSelection,
                lifecycleOwner = lifecycleOwner,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = "Autorisation caméra requise",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        CameraMenu(
            currentSelection = cameraSelection,
            onCameraSelected = { cameraSelection = it },
            onQuit = onQuit,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding()
        )
    }
}

@Composable
private fun CameraMenu(
    currentSelection: CameraSelection,
    onCameraSelected: (CameraSelection) -> Unit,
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
    lifecycleOwner: LifecycleOwner,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            val cameraProvider = ProcessCameraProvider.getInstance(context).get()
            cameraProvider.unbindAll()
        }
    }

    LaunchedEffect(selection, lifecycleOwner) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        val selector = when (selection) {
            CameraSelection.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            CameraSelection.REAR -> CameraSelector.DEFAULT_BACK_CAMERA
        }

        cameraProvider.unbindAll()
        if (cameraProvider.hasCamera(selector)) {
            cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}
