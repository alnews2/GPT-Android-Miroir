package fr.alnews2.gptandroidmiroir

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.graphics.Rect
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ConcurrentCameraController(context: Context) {
    private data class Target(val surface: Surface, val width: Int, val height: Int, val fps: Int)

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val cameraCallbackHandler = Handler(Looper.getMainLooper())
    private val ids = mutableMapOf<CameraSelection, String>()
    private val targets = mutableMapOf<CameraSelection, Target>()
    private val devices = mutableMapOf<CameraSelection, CameraDevice>()
    private val sessions = mutableMapOf<CameraSelection, CameraCaptureSession>()
    private val running = mutableSetOf<CameraSelection>()
    private data class ExposureSnapshot(val state: Int?, val exposureTimeNs: Long?, val sensitivityIso: Int?)
    private val lastExposureSnapshots = mutableMapOf<CameraSelection, ExposureSnapshot>()
    private val zoomRatios = mutableMapOf(CameraSelection.REAR to 1f, CameraSelection.FRONT to 1f)

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

    fun adjustZoom(selection: CameraSelection, factor: Float) {
        synchronized(this) {
            val cameraId = ids[selection] ?: return
            val chars = runCatching { cameraManager.getCameraCharacteristics(cameraId) }.getOrNull() ?: return
            val maxZoom = (chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f).coerceAtLeast(1f)
            val next = ((zoomRatios[selection] ?: 1f) * factor).coerceIn(1f, maxZoom)
            zoomRatios[selection] = next
            val device = devices[selection] ?: return
            val session = sessions[selection] ?: return
            val target = targets[selection] ?: return
            runCatching {
                val active = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                    configureAutomaticExposure(this, chars)
                    addTarget(target.surface)
                    if (active != null) {
                        val width = (active.width() / next).toInt().coerceAtLeast(1)
                        val height = (active.height() / next).toInt().coerceAtLeast(1)
                        val left = active.centerX() - width / 2
                        val top = active.centerY() - height / 2
                        set(CaptureRequest.SCALER_CROP_REGION, Rect(left, top, left + width, top + height))
                    }
                }
                session.setRepeatingRequest(
                    requestBuilder.build(),
                    object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: android.hardware.camera2.TotalCaptureResult
                        ) {
                            reportExposureResult(selection, result)
                        }
                    },
                    cameraCallbackHandler
                )
            }.onFailure {
                lastError = "Zoom " + selection.name.lowercase() + " impossible : " + (it.message ?: "erreur Camera2")
            }
        }
    }

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
        val configuration = SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR,
            listOf(OutputConfiguration(target.surface)),
            executor,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    synchronized(this@ConcurrentCameraController) {
                        sessions[selection] = session
                        runCatching {
                            val cameraId = ids[selection]
                                ?: error("Identifiant caméra introuvable")
                            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                                .apply {
                                    configureAutomaticExposure(this, characteristics)
                                    addTarget(target.surface)
                                    val zoom = zoomRatios[selection] ?: 1f
                                    val active = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                                    if (active != null && zoom > 1f) {
                                        val width = (active.width() / zoom).toInt().coerceAtLeast(1)
                                        val height = (active.height() / zoom).toInt().coerceAtLeast(1)
                                        val left = active.centerX() - width / 2
                                        val top = active.centerY() - height / 2
                                        set(CaptureRequest.SCALER_CROP_REGION, Rect(left, top, left + width, top + height))
                                    }
                                }.build()
                            session.setRepeatingRequest(
                                request,
                                object : CameraCaptureSession.CaptureCallback() {
                                    override fun onCaptureCompleted(
                                        session: CameraCaptureSession,
                                        request: CaptureRequest,
                                        result: android.hardware.camera2.TotalCaptureResult
                                    ) {
                                        reportExposureResult(selection, result)
                                    }
                                },
                                cameraCallbackHandler
                            )
                            Log.i(
                                TAG,
                                "${selection.name} AE request started: cameraId=$cameraId, " +
                                    "controlModes=${characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_MODES)?.contentToString()}, " +
                                    "aeModes=${characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)?.contentToString()}"
                            )
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

    /**
     * Force automatic exposure on each Camera2 request instead of relying on
     * device-specific defaults from TEMPLATE_RECORD. Only use modes advertised
     * by the camera so unusual devices can still start their capture session.
     */
    private fun reportExposureResult(
        selection: CameraSelection,
        result: android.hardware.camera2.TotalCaptureResult
    ) {
        val snapshot = ExposureSnapshot(
            state = result.get(CaptureResult.CONTROL_AE_STATE),
            exposureTimeNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME),
            sensitivityIso = result.get(CaptureResult.SENSOR_SENSITIVITY)
        )
        synchronized(lastExposureSnapshots) {
            val previous = lastExposureSnapshots[selection]
            val exposureChanged = previous?.exposureTimeNs != null && snapshot.exposureTimeNs != null &&
                relativeChange(previous.exposureTimeNs.toDouble(), snapshot.exposureTimeNs.toDouble()) >= 0.20
            val sensitivityChanged = previous?.sensitivityIso != null && snapshot.sensitivityIso != null &&
                relativeChange(previous.sensitivityIso.toDouble(), snapshot.sensitivityIso.toDouble()) >= 0.20
            if (previous == null || previous.state != snapshot.state || exposureChanged || sensitivityChanged) {
                Log.i(
                    TAG,
                    "${selection.name} AE result: state=${snapshot.state}, " +
                        "exposureNs=${snapshot.exposureTimeNs}, iso=${snapshot.sensitivityIso}"
                )
                lastExposureSnapshots[selection] = snapshot
            }
        }
    }

    private fun relativeChange(old: Double, current: Double): Double =
        if (old == 0.0) if (current == 0.0) 0.0 else 1.0 else kotlin.math.abs(current - old) / old

    private fun configureAutomaticExposure(
        request: CaptureRequest.Builder,
        characteristics: CameraCharacteristics
    ) {
        val controlModes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_MODES)
        if (controlModes?.contains(CaptureRequest.CONTROL_MODE_AUTO) == true) {
            request.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        }

        val aeModes = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)
        if (aeModes?.contains(CaptureRequest.CONTROL_AE_MODE_ON) == true) {
            request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        } else {
            Log.e(TAG, "Camera ${characteristics.get(CameraCharacteristics.LENS_FACING)} does not advertise AE_MODE_ON; AE modes=${aeModes?.contentToString()}")
        }
    }

    private fun closeAllLocked() {
        sessions.values.forEach { runCatching { it.close() } }
        devices.values.forEach { runCatching { it.close() } }
        sessions.clear()
        devices.clear()
        running.clear()
    }

    private companion object {
        const val TAG = "ConcurrentCameraController"
    }

    private class EmptySessionCallback : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) = Unit
        override fun onConfigureFailed(session: CameraCaptureSession) = Unit
    }
}
