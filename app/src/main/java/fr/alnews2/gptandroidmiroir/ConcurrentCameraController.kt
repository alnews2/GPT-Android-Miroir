package fr.alnews2.gptandroidmiroir

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import android.view.Surface
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ConcurrentCameraController(context: Context) {
    private data class Target(val surface: Surface, val width: Int, val height: Int, val fps: Int)

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val ids = mutableMapOf<CameraSelection, String>()
    private val targets = mutableMapOf<CameraSelection, Target>()
    private val devices = mutableMapOf<CameraSelection, CameraDevice>()
    private val sessions = mutableMapOf<CameraSelection, CameraCaptureSession>()
    private val running = mutableSetOf<CameraSelection>()

    @Volatile var lastError: String? = null
        private set

    init { discoverCameraIds() }

    fun isConcurrentSupported(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val rearId = ids[CameraSelection.REAR] ?: return false
        val frontId = ids[CameraSelection.FRONT] ?: return false
        return cameraManager.concurrentCameraIds.any { rearId in it && frontId in it }
    }

    fun register(selection: CameraSelection, surface: Surface, width: Int, height: Int, fps: Int) {
        synchronized(this) {
            targets[selection]?.surface?.release()
            targets[selection] = Target(surface, width, height, fps)
            if (targets.containsKey(CameraSelection.REAR) && targets.containsKey(CameraSelection.FRONT)) {
                openConcurrentCameras()
            }
        }
    }

    fun unregister(selection: CameraSelection) {
        synchronized(this) {
            sessions.remove(selection)?.close()
            devices.remove(selection)?.close()
            running.remove(selection)
            targets.remove(selection)
            if (targets.isEmpty()) closeAllLocked()
        }
    }

    fun isRunning(selection: CameraSelection): Boolean = synchronized(this) { selection in running }

    fun close() {
        synchronized(this) {
            closeAllLocked()
            targets.values.forEach { runCatching { it.surface.release() } }
            targets.clear()
        }
    }

    private fun discoverCameraIds() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            cameraManager.cameraIdList.forEach { id ->
                when (cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_BACK -> ids.putIfAbsent(CameraSelection.REAR, id)
                    CameraCharacteristics.LENS_FACING_FRONT -> ids.putIfAbsent(CameraSelection.FRONT, id)
                }
            }
        }.onFailure {
            lastError = "Impossible d'identifier les caméras : " + (it.message ?: "erreur Camera2")
        }
    }

    private fun openConcurrentCameras() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (!isConcurrentSupported()) {
            lastError = "Ce téléphone ne prend pas en charge la capture simultanée avant/arrière."
            return
        }
        if (devices.size == 2) return
        closeAllLocked()

        val rearId = ids[CameraSelection.REAR]
        val frontId = ids[CameraSelection.FRONT]
        val rearTarget = targets[CameraSelection.REAR]
        val frontTarget = targets[CameraSelection.FRONT]
        if (rearId == null || frontId == null || rearTarget == null || frontTarget == null) {
            lastError = "Caméras avant et arrière introuvables."
            return
        }

        runCatching {
            val configs = mapOf(
                rearId to SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    listOf(OutputConfiguration(rearTarget.surface)),
                    executor,
                    EmptySessionCallback()
                ),
                frontId to SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    listOf(OutputConfiguration(frontTarget.surface)),
                    executor,
                    EmptySessionCallback()
                )
            )
            check(cameraManager.isConcurrentSessionConfigurationSupported(configs)) {
                "La combinaison de résolution/flux demandée n'est pas supportée simultanément."
            }
            openCamera(CameraSelection.REAR, rearId)
            openCamera(CameraSelection.FRONT, frontId)
        }.onFailure {
            lastError = "Démarrage caméra simultanée impossible : " + (it.message ?: "erreur Camera2")
            closeAllLocked()
        }
    }

    private fun openCamera(selection: CameraSelection, cameraId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        cameraManager.openCamera(cameraId, executor, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                synchronized(this@ConcurrentCameraController) {
                    devices[selection] = camera
                    if (devices.size == 2) configureSessions()
                }
            }

            override fun onDisconnected(camera: CameraDevice) {
                synchronized(this@ConcurrentCameraController) {
                    devices.remove(selection)
                    running.remove(selection)
                }
                camera.close()
                lastError = "${selection.name.lowercase()}: caméra déconnectée"
            }

            override fun onError(camera: CameraDevice, error: Int) {
                synchronized(this@ConcurrentCameraController) {
                    devices.remove(selection)
                    running.remove(selection)
                }
                camera.close()
                lastError = "${selection.name.lowercase()}: erreur Camera2 $error"
            }
        })
    }

    private fun configureSessions() {
        val rearDevice = devices[CameraSelection.REAR] ?: return
        val frontDevice = devices[CameraSelection.FRONT] ?: return
        val rearTarget = targets[CameraSelection.REAR] ?: return
        val frontTarget = targets[CameraSelection.FRONT] ?: return
        createSession(CameraSelection.REAR, rearDevice, rearTarget)
        createSession(CameraSelection.FRONT, frontDevice, frontTarget)
    }

    private fun createSession(selection: CameraSelection, device: CameraDevice, target: Target) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val configuration = SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR,
            listOf(OutputConfiguration(target.surface)),
            executor,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    synchronized(this@ConcurrentCameraController) {
                        sessions[selection] = session
                        runCatching {
                            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                                .apply { addTarget(target.surface) }.build()
                            session.setRepeatingRequest(request, null, executor)
                            running += selection
                        }.onFailure {
                            lastError = "${selection.name.lowercase()}: " +
                                (it.message ?: "échec de la capture")
                            session.close()
                        }
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    lastError = "${selection.name.lowercase()}: échec de configuration Camera2"
                    session.close()
                }
            }
        )
        runCatching { device.createCaptureSession(configuration) }.onFailure {
            lastError = "${selection.name.lowercase()}: " +
                (it.message ?: "échec de création de session")
        }
    }

    private fun closeAllLocked() {
        sessions.values.forEach { runCatching { it.close() } }
        devices.values.forEach { runCatching { it.close() } }
        sessions.clear()
        devices.clear()
        running.clear()
    }

    private class EmptySessionCallback : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) = Unit
        override fun onConfigureFailed(session: CameraCaptureSession) = Unit
    }
}
