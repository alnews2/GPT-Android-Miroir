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
import com.pedro.library.base.StreamBase
import com.pedro.library.view.OpenGlView
import com.pedro.rtspserver.server.RtspServer
import com.pedro.rtspserver.util.RtspServerStreamClient
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

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
    private val autoExposureAttempted = AtomicBoolean(false)

    init {
        rtspServer.setOnlyVideo(true)
        // Camera2 opens and configures its capture session asynchronously.
        // Wait for the first completed capture before applying AE: onCameraOpened()
        // fires before the repeating capture session is necessarily ready.
        (videoSource as Camera2Source).setCustomOnCaptureCompletedCallback { _, _, _ ->
            if (autoExposureAttempted.compareAndSet(false, true)) {
                val camera = videoSource as Camera2Source
                val enabled = camera.enableAutoExposure()
                if (enabled) {
                    Log.i("SingleRtspServerStream", "Automatic exposure enabled for ${camera.getCameraFacing()}")
                } else {
                    Log.w("SingleRtspServerStream", "RootEncoder could not enable automatic exposure for ${camera.getCameraFacing()}")
                }
            }
        }
        (videoSource as Camera2Source).setCameraCallback(object : CameraCallbacks {
            override fun onCameraOpened() {
                autoExposureAttempted.set(false)
            }

            override fun onCameraChanged(facing: com.pedro.encoder.input.video.CameraHelper.Facing) = Unit
            override fun onCameraError(error: String) {
                Log.e("SingleRtspServerStream", "Camera2 error: $error")
            }
            override fun onCameraDisconnected() {
                Log.w("SingleRtspServerStream", "Camera2 disconnected")
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
        // switchCamera() closes and reopens Camera2 asynchronously. The same
        // onCameraOpened callback reapplies automatic exposure to the new lens.
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
}
