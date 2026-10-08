package fr.alnews2.gptandroidmiroir

import android.content.Context
import android.view.SurfaceHolder
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.view.OpenGlView
import com.pedro.rtspserver.RtspServerCamera2

class RtspStreamManager(
    private val context: Context,
    private val rearView: OpenGlView,
    private val frontView: OpenGlView
) {
    private var rearStream: RtspServerCamera2? = null
    private var frontStream: RtspServerCamera2? = null
    private var configuration: RtspConfiguration? = null
    private var rearSurfaceReady = false
    private var frontSurfaceReady = false

    var lastError: String? = null
        private set

    init {
        rearView.holder.addCallback(surfaceCallback(CameraSelection.REAR))
        frontView.holder.addCallback(surfaceCallback(CameraSelection.FRONT))
    }

    fun start(configuration: RtspConfiguration) {
        stop()
        lastError = null
        this.configuration = configuration
        tryStart(CameraSelection.REAR)
        tryStart(CameraSelection.FRONT)
    }

    fun stop() {
        runCatching { rearStream?.stopStream() }
        runCatching { frontStream?.stopStream() }
        rearStream = null
        frontStream = null
        configuration = null
    }

    fun isRunning(selection: CameraSelection): Boolean = when (selection) {
        CameraSelection.REAR -> rearStream?.isStreaming == true
        CameraSelection.FRONT -> frontStream?.isStreaming == true
    }

    fun endpoint(selection: CameraSelection): String? = when (selection) {
        CameraSelection.REAR -> rearStream?.getStreamClient()?.getEndPointConnection()
        CameraSelection.FRONT -> frontStream?.getStreamClient()?.getEndPointConnection()
    }

    private fun surfaceCallback(selection: CameraSelection) = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            setSurfaceReady(selection, true)
            tryStart(selection)
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            if (holder.surface.isValid) {
                setSurfaceReady(selection, true)
                tryStart(selection)
            }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            setSurfaceReady(selection, false)
            stop(selection)
        }
    }

    private fun setSurfaceReady(selection: CameraSelection, ready: Boolean) {
        when (selection) {
            CameraSelection.REAR -> rearSurfaceReady = ready
            CameraSelection.FRONT -> frontSurfaceReady = ready
        }
    }

    private fun tryStart(selection: CameraSelection) {
        val currentConfiguration = configuration ?: return
        val surfaceReady = when (selection) {
            CameraSelection.REAR -> rearSurfaceReady
            CameraSelection.FRONT -> frontSurfaceReady
        }
        if (!surfaceReady || !isEnabled(selection, currentConfiguration) || isRunning(selection)) return

        val view = when (selection) {
            CameraSelection.REAR -> rearView
            CameraSelection.FRONT -> frontView
        }
        val port = when (selection) {
            CameraSelection.REAR -> currentConfiguration.rearPort
            CameraSelection.FRONT -> currentConfiguration.frontPort
        }

        createStream(selection, port, view, currentConfiguration)?.let { stream ->
            when (selection) {
                CameraSelection.REAR -> rearStream = stream
                CameraSelection.FRONT -> frontStream = stream
            }
        }
    }

    private fun isEnabled(selection: CameraSelection, configuration: RtspConfiguration): Boolean =
        when (selection) {
            CameraSelection.REAR -> configuration.rearEnabled
            CameraSelection.FRONT -> configuration.frontEnabled
        }

    private fun stop(selection: CameraSelection) {
        when (selection) {
            CameraSelection.REAR -> {
                runCatching { rearStream?.stopStream() }
                rearStream = null
            }
            CameraSelection.FRONT -> {
                runCatching { frontStream?.stopStream() }
                frontStream = null
            }
        }
    }

    private fun createStream(
        selection: CameraSelection,
        port: Int,
        view: OpenGlView,
        configuration: RtspConfiguration
    ): RtspServerCamera2? {
        return runCatching {
            val stream = RtspServerCamera2(view, checker(selection), port)
            val rotation = CameraHelper.getCameraOrientation(context)
            check(
                stream.prepareVideo(
                    configuration.width,
                    configuration.height,
                    configuration.fps,
                    configuration.bitrate,
                    2,
                    rotation
                )
            ) {
                "encodeur H.264 indisponible"
            }
            stream.getStreamClient().setOnlyVideo(true)
            if (selection == CameraSelection.FRONT) {
                stream.switchCamera()
            }
            stream.startStream()
            stream
        }.onFailure { error ->
            lastError = selection.name.lowercase() + ": " +
                (error.message ?: "échec du démarrage RTSP")
        }.getOrNull()
    }

    private fun checker(selection: CameraSelection) = object : ConnectChecker {
        override fun onConnectionStarted(url: String) = Unit
        override fun onConnectionSuccess() = Unit
        override fun onConnectionFailed(reason: String) {
            lastError = selection.name.lowercase() + ": " + reason
        }
        override fun onNewBitrate(bitrate: Long) = Unit
        override fun onDisconnect() = Unit
        override fun onAuthError() = Unit
        override fun onAuthSuccess() = Unit
    }
}
