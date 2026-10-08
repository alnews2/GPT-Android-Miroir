package fr.alnews2.gptandroidmiroir

import android.content.Context
import com.pedro.common.ConnectChecker
import com.pedro.library.view.OpenGlView
import com.pedro.rtspserver.RtspServerCamera2

class RtspStreamManager(
    private val context: Context,
    private val rearView: OpenGlView,
    private val frontView: OpenGlView
) {
    private var rearStream: RtspServerCamera2? = null
    private var frontStream: RtspServerCamera2? = null

    var lastError: String? = null
        private set

    fun start(configuration: RtspConfiguration) {
        stop()
        lastError = null
        if (configuration.rearEnabled) {
            rearStream = createStream(CameraSelection.REAR, configuration.rearPort, rearView, configuration)
        }
        if (configuration.frontEnabled) {
            frontStream = createStream(CameraSelection.FRONT, configuration.frontPort, frontView, configuration)
        }
    }

    fun stop() {
        runCatching { rearStream?.stopStream() }
        runCatching { frontStream?.stopStream() }
        rearStream = null
        frontStream = null
    }

    fun isRunning(selection: CameraSelection): Boolean =
        when (selection) {
            CameraSelection.REAR -> rearStream?.isStreaming == true
            CameraSelection.FRONT -> frontStream?.isStreaming == true
        }

    fun endpoint(selection: CameraSelection): String? =
        when (selection) {
            CameraSelection.REAR -> rearStream?.getStreamClient()?.getEndPointConnection()
            CameraSelection.FRONT -> frontStream?.getStreamClient()?.getEndPointConnection()
        }

    private fun createStream(
        selection: CameraSelection,
        port: Int,
        view: OpenGlView,
        configuration: RtspConfiguration
    ): RtspServerCamera2? {
        return runCatching {
            val stream = RtspServerCamera2(view, checker(selection), port)
            check(
                stream.prepareVideo(
                    configuration.width,
                    configuration.height,
                    configuration.fps,
                    configuration.bitrate,
                    2,
                    0
                )
            ) { "encodeur H.264 indisponible" }

            stream.getStreamClient().setOnlyVideo(true)
            if (selection == CameraSelection.FRONT) {
                stream.switchCamera()
            }
            stream.startStream()
            stream
        }.onFailure {
            lastError = "${selection.name.lowercase()}: ${it.message ?: "échec du démarrage RTSP"}"
        }.getOrNull()
    }

    private fun checker(selection: CameraSelection) = object : ConnectChecker {
        override fun onConnectionStarted(url: String) = Unit
        override fun onConnectionSuccess() = Unit
        override fun onConnectionFailed(reason: String) {
            lastError = "${selection.name.lowercase()}: $reason"
        }
        override fun onNewBitrate(bitrate: Long) = Unit
        override fun onDisconnect() = Unit
        override fun onAuthError() = Unit
        override fun onAuthSuccess() = Unit
    }
}
