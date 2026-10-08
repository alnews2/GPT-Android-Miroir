package fr.alnews2.gptandroidmiroir

import android.content.Context
import android.view.SurfaceHolder
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.view.OpenGlView
import com.pedro.rtspserver.RtspServerCamera2

class RtspStreamManager(
    private val context: Context,
    private val previewView: OpenGlView
) {
    private var rearStream: RtspServerCamera2? = null
    private var frontStream: RtspServerCamera2? = null
    private var configuration: RtspConfiguration? = null
    private var selectedCamera = CameraSelection.REAR
    private var previewSurfaceReady = false

    var lastError: String? = null
        private set

    init {
        previewView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                previewSurfaceReady = true
                attachPreview(selectedCamera)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                if (holder.surface.isValid) {
                    previewSurfaceReady = true
                    attachPreview(selectedCamera)
                }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                previewSurfaceReady = false
                detachPreview(selectedCamera)
            }
        })
    }

    fun start(configuration: RtspConfiguration) {
        stop()
        lastError = null
        this.configuration = configuration
        createStreams(configuration)
        if (previewSurfaceReady) attachPreview(selectedCamera)
    }

    fun selectCamera(selection: CameraSelection) {
        if (selectedCamera == selection) return
        detachPreview(selectedCamera)
        selectedCamera = selection
        if (previewSurfaceReady) attachPreview(selection)
    }

    fun stop() {
        detachPreview(selectedCamera)
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

    private fun createStreams(configuration: RtspConfiguration) {
        if (configuration.rearEnabled) {
            rearStream = createStream(
                CameraSelection.REAR,
                configuration.rearPort,
                configuration
            )
        }
        if (configuration.frontEnabled) {
            frontStream = createStream(
                CameraSelection.FRONT,
                configuration.frontPort,
                configuration
            )
        }
    }

    private fun createStream(
        selection: CameraSelection,
        port: Int,
        configuration: RtspConfiguration
    ): RtspServerCamera2? {
        return runCatching {
            // Background mode: no camera preview surface is required to start the RTSP server.
            val stream = RtspServerCamera2(context, checker(selection), port)
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

    private fun attachPreview(selection: CameraSelection) {
        if (!previewSurfaceReady) return
        val stream = when (selection) {
            CameraSelection.REAR -> rearStream
            CameraSelection.FRONT -> frontStream
        } ?: return

        runCatching {
            stream.replaceView(previewView)
        }.onFailure { error ->
            lastError = selection.name.lowercase() + ": " +
                (error.message ?: "échec de l'affichage de la prévisualisation")
        }
    }

    private fun detachPreview(selection: CameraSelection) {
        val stream = when (selection) {
            CameraSelection.REAR -> rearStream
            CameraSelection.FRONT -> frontStream
        } ?: return

        runCatching {
            stream.replaceView(context)
        }
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
