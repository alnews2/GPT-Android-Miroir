package fr.alnews2.gptandroidmiroir

import android.content.Context
import android.hardware.camera2.CaptureResult
import android.media.MediaCodec
import android.util.Log
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.encoder.input.sources.audio.NoAudioSource
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.encoder.input.video.CameraCallbacks
import com.pedro.encoder.input.video.FrameCapturedCallback
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.base.StreamBase
import com.pedro.library.view.OpenGlView
import com.pedro.rtspserver.server.RtspServer
import com.pedro.rtspserver.util.RtspServerStreamClient
import java.nio.ByteBuffer

class SingleRtspServerStream(
    context: Context,
    connectChecker: ConnectChecker,
    port: Int
) : StreamBase(
    context,
    Camera2Source(context),
    NoAudioSource()
) {
    private val rtspServer = RtspServer(connectChecker, port)
    private var zoomRatio = 1f
    @Volatile private var needsAutomaticExposure = true
    private data class ExposureSnapshot(
        val aeState: Int?,
        val exposureTimeNs: Long?,
        val sensitivityIso: Int?
    )
    @Volatile private var lastExposureSnapshot: ExposureSnapshot? = null

    init {
        rtspServer.setOnlyVideo(true)

        val camera = videoSource as Camera2Source

        // RootEncoder requires setCustomRequest() to be called after the camera
        // has started. The first capture callback is late enough for its capture
        // session and repeating request to exist; apply AE there, not in init.
        camera.enableFrameCaptureCallback(object : FrameCapturedCallback {
            override fun onFrameCaptured(frameNumber: Long, timestamp: Long) {
                if (!needsAutomaticExposure) return

                // First use RootEncoder's supported AE API, then explicitly configure
                // the repeating request so AE cannot remain locked from a prior session.
                val aeEnabled = camera.enableAutoExposure()
                val requestApplied = aeEnabled && camera.setCustomRequest { request ->
                    request.set(
                        android.hardware.camera2.CaptureRequest.CONTROL_MODE,
                        android.hardware.camera2.CaptureRequest.CONTROL_MODE_AUTO
                    )
                    request.set(
                        android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE,
                        android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE_ON
                    )
                    request.set(
                        android.hardware.camera2.CaptureRequest.CONTROL_AE_LOCK,
                        false
                    )
                    request.set(
                        android.hardware.camera2.CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
                        0
                    )
                }

                if (aeEnabled && requestApplied && camera.isAutoExposureEnabled()) {
                    needsAutomaticExposure = false
                    Log.i(
                        TAG,
                        "Camera2 AE request applied: cameraId=${camera.getCurrentCameraId()}, " +
                            "frame=$frameNumber, controlMode=AUTO, aeMode=ON, aeLock=false, " +
                            "exposureCompensation=0, apiEnabled=${camera.isAutoExposureEnabled()}. " +
                            "RootEncoder 2.6.6 does not expose TotalCaptureResult for this single-camera source."
                    )
                } else {
                    Log.e(
                        TAG,
                        "Camera2 AE request incomplete: cameraId=${camera.getCurrentCameraId()}, " +
                            "running=${camera.isRunning()}, aeEnabled=$aeEnabled, " +
                            "requestApplied=$requestApplied, apiEnabled=${camera.isAutoExposureEnabled()}; will retry"
                    )
                }
            }
        })

        // RootEncoder 2.8.1 exposes completed Camera2 results. Log the actual
        // sensor values only when AE state changes or exposure/ISO moves materially.
        camera.setCustomOnCaptureCompletedCallback { _, _, result ->
            val snapshot = ExposureSnapshot(
                aeState = result.get(CaptureResult.CONTROL_AE_STATE),
                exposureTimeNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME),
                sensitivityIso = result.get(CaptureResult.SENSOR_SENSITIVITY)
            )
            val previous = lastExposureSnapshot
            val exposureChanged = previous?.exposureTimeNs != null &&
                snapshot.exposureTimeNs != null &&
                relativeChange(previous.exposureTimeNs.toDouble(), snapshot.exposureTimeNs.toDouble()) >= 0.20
            val sensitivityChanged = previous?.sensitivityIso != null &&
                snapshot.sensitivityIso != null &&
                relativeChange(previous.sensitivityIso.toDouble(), snapshot.sensitivityIso.toDouble()) >= 0.20

            if (previous == null || previous.aeState != snapshot.aeState ||
                exposureChanged || sensitivityChanged
            ) {
                Log.i(
                    TAG,
                    "Camera2 AE capture result: cameraId=${camera.getCurrentCameraId()}, " +
                        "state=${snapshot.aeState}, exposureNs=${snapshot.exposureTimeNs}, " +
                        "iso=${snapshot.sensitivityIso}"
                )
                lastExposureSnapshot = snapshot
            }
        }

        camera.setCameraCallback(object : CameraCallbacks {
            override fun onCameraOpened() {
                // A camera switch rebuilds the capture request and session.
                needsAutomaticExposure = true
                lastExposureSnapshot = null
                Log.i(TAG, "Camera2 opened: facing=${camera.getCameraFacing()}, id=${camera.getCurrentCameraId()}; waiting to enable AE")
            }

            override fun onCameraChanged(facing: CameraHelper.Facing) {
                needsAutomaticExposure = true
                lastExposureSnapshot = null
                Log.i(TAG, "Camera2 changed to $facing; automatic exposure will be applied to the new session")
            }

            override fun onCameraError(error: String) {
                Log.e(TAG, "Camera2 error: $error")
            }

            override fun onCameraDisconnected() {
                Log.w(TAG, "Camera2 disconnected")
            }
        })
    }

    fun startStream() {
        super.startStream("")
    }

    fun startPreview(view: OpenGlView) {
        super.startPreview(view, true)
    }

    fun switchCamera() {
        (videoSource as Camera2Source).switchCamera()
    }

    fun adjustZoom(factor: Float) {
        if (!factor.isFinite() || factor <= 0f) return
        zoomRatio = (zoomRatio * factor).coerceIn(1f, 8f)
        (videoSource as Camera2Source).setZoom(zoomRatio)
    }

    fun currentCamera(): CameraSelection =
        when ((videoSource as Camera2Source).getCameraFacing().name) {
            "FRONT" -> CameraSelection.FRONT
            else -> CameraSelection.REAR
        }

    override fun onAudioInfoImp(sampleRate: Int, isStereo: Boolean) {
        rtspServer.setAudioInfo(sampleRate, isStereo)
    }

    override fun startStreamImp(url: String) {
        rtspServer.startServer()
    }

    override fun stopStreamImp() {
        rtspServer.stopServer()
    }

    override fun onVideoInfoImp(
        sps: ByteBuffer,
        pps: ByteBuffer?,
        vps: ByteBuffer?
    ) {
        rtspServer.setVideoInfo(sps.duplicate(), pps?.duplicate(), vps?.duplicate())
    }

    override fun getVideoDataImp(videoBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        rtspServer.sendVideo(videoBuffer, info)
    }

    override fun getAudioDataImp(audioBuffer: ByteBuffer, info: MediaCodec.BufferInfo) = Unit

    override fun getStreamClient(): RtspServerStreamClient =
        RtspServerStreamClient(rtspServer)

    override fun setVideoCodecImp(codec: VideoCodec) {
        rtspServer.setVideoCodec(codec)
    }

    override fun setAudioCodecImp(codec: AudioCodec) {
        rtspServer.setAudioCodec(codec)
    }

    private fun relativeChange(old: Double, current: Double): Double =
        if (old == 0.0) if (current == 0.0) 0.0 else 1.0 else kotlin.math.abs(current - old) / old

    private companion object {
        const val TAG = "SingleRtspServerStream"
    }
}
