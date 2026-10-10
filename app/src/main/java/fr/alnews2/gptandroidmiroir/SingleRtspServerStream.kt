package fr.alnews2.gptandroidmiroir

import android.content.Context
import android.media.MediaCodec
import android.os.Handler
import android.os.Looper
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
    private val cameraHandler = Handler(Looper.getMainLooper())

    init {
        rtspServer.setOnlyVideo(true)
        // Camera2 opens and configures its capture session asynchronously.
        // Retry briefly after onCameraOpened(), because RootEncoder can notify
        // that the device is open before its repeating capture session is ready.
        (videoSource as Camera2Source).setCameraCallback(object : CameraCallbacks {
            override fun onCameraOpened() {
                autoExposureAttempted.set(false)
                enableAutoExposureWhenReady(attempt = 0)
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

    private fun enableAutoExposureWhenReady(attempt: Int) {
        if (autoExposureAttempted.get()) return
        val camera = videoSource as Camera2Source
        if (camera.isRunning() && camera.enableAutoExposure()) {
            autoExposureAttempted.set(true)
            Log.i("SingleRtspServerStream", "Automatic exposure enabled for ${camera.getCameraFacing()}")
            return
        }
        if (attempt < 10) {
            cameraHandler.postDelayed({ enableAutoExposureWhenReady(attempt + 1) }, 100L)
        } else {
            Log.w("SingleRtspServerStream", "Automatic exposure could not be enabled after camera startup")
        }
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
