package fr.alnews2.gptandroidmiroir

import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.media.MediaCodec
import android.util.Log
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.encoder.input.sources.audio.NoAudioSource
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.encoder.input.video.CameraCallbacks
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

    init {
        rtspServer.setOnlyVideo(true)

        val camera = videoSource as Camera2Source

        // Apply AE to every Camera2 capture request. Calling enableAutoExposure()
        // after onCameraOpened() can still be too early: RootEncoder opens the
        // device and configures its repeating capture session asynchronously.
        // A custom request is retained by Camera2 and reapplied to subsequent
        // requests, including after the camera is switched.
        val customRequestAccepted = camera.setCustomRequest { request ->
            request.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        }
        if (customRequestAccepted) {
            Log.i(TAG, "Automatic exposure requested for every Camera2 capture request")
        } else {
            Log.e(TAG, "RootEncoder rejected the automatic-exposure capture request")
        }

        camera.setCameraCallback(object : CameraCallbacks {
            override fun onCameraOpened() {
                Log.i(TAG, "Camera2 opened: ${camera.getCameraFacing()}, automatic exposure request installed")
            }

            override fun onCameraChanged(facing: com.pedro.encoder.input.video.CameraHelper.Facing) {
                Log.i(TAG, "Camera2 changed to $facing; automatic exposure remains configured")
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
