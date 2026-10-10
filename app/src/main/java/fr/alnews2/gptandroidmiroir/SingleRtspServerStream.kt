package fr.alnews2.gptandroidmiroir

import android.content.Context
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

    init {
        rtspServer.setOnlyVideo(true)

        val camera = videoSource as Camera2Source

        // RootEncoder requires setCustomRequest() to be called after the camera
        // has started. The first capture callback is late enough for its capture
        // session and repeating request to exist; apply AE there, not in init.
        camera.enableFrameCaptureCallback(object : FrameCapturedCallback {
            override fun onFrameCaptured(frameNumber: Long, timestamp: Long) {
                if (!needsAutomaticExposure) return

                val accepted = camera.enableAutoExposure()
                if (accepted) {
                    needsAutomaticExposure = false
                    Log.i(
                        TAG,
                        "Camera2 automatic exposure enabled: cameraId=${camera.getCurrentCameraId()}, " +
                            "frame=$frameNumber, enabled=${camera.isAutoExposureEnabled()}"
                    )
                } else {
                    Log.e(
                        TAG,
                        "Camera2 rejected automatic exposure: cameraId=${camera.getCurrentCameraId()}, " +
                            "running=${camera.isRunning()}; will retry"
                    )
                }
            }
        })

        camera.setCameraCallback(object : CameraCallbacks {
            override fun onCameraOpened() {
                // A camera switch rebuilds the capture request and session.
                needsAutomaticExposure = true
                Log.i(TAG, "Camera2 opened: facing=${camera.getCameraFacing()}, id=${camera.getCurrentCameraId()}; waiting to enable AE")
            }

            override fun onCameraChanged(facing: CameraHelper.Facing) {
                needsAutomaticExposure = true
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

    private companion object {
        const val TAG = "SingleRtspServerStream"
    }
}
