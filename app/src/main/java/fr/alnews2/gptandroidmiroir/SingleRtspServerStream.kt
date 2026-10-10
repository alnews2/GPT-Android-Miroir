package fr.alnews2.gptandroidmiroir

import android.content.Context
import android.media.MediaCodec
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.encoder.input.sources.audio.NoAudioSource
import com.pedro.encoder.input.sources.video.Camera2Source
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
    }

    fun startStream() {
        super.startStream("")
        // RootEncoder owns the Camera2 capture request in single-camera mode.
        // Explicitly enable AE through its public API after the camera starts.
        (videoSource as Camera2Source).enableAutoExposure()
    }

    fun startPreview(view: OpenGlView) {
        super.startPreview(view, true)
    }

    fun switchCamera() {
        (videoSource as Camera2Source).switchCamera()
        if ((videoSource as Camera2Source).isRunning) {
            (videoSource as Camera2Source).enableAutoExposure()
        }
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
