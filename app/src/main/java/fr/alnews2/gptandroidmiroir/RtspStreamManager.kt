package fr.alnews2.gptandroidmiroir

import android.content.Context
import android.view.SurfaceHolder
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.library.view.OpenGlView

class RtspStreamManager(
    private val context: Context,
    private val previewView: OpenGlView
) {
    private val cameraController = ConcurrentCameraController(context)
    private var rearStream: ConcurrentRtspServerStream? = null
    private var frontStream: ConcurrentRtspServerStream? = null
    private var configuration: RtspConfiguration? = null
    private var selectedCamera = CameraSelection.REAR
    private var previewSurfaceReady = false
    private var concurrentMode = false

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

        concurrentMode = configuration.rearEnabled &&
            configuration.frontEnabled &&
            cameraController.isConcurrentSupported()

        if (concurrentMode) {
            createStreams(configuration)
        } else {
            // Fallback for phones that cannot capture both cameras simultaneously.
            createSelectedStream(configuration)
        }

        lastError = cameraController.lastError ?: lastError
        if (previewSurfaceReady) attachPreview(selectedCamera)
    }

    fun selectCamera(selection: CameraSelection) {
        if (selectedCamera == selection && (concurrentMode || streamFor(selection) != null)) {
            return
        }

        detachPreview(selectedCamera)
        selectedCamera = selection
        lastError = null

        if (!concurrentMode) {
            stopSelectedStream()
            configuration?.let { createSelectedStream(it) }
        }

        if (previewSurfaceReady) attachPreview(selection)
        lastError = cameraController.lastError ?: lastError
    }

    fun stop() {
        detachPreview(selectedCamera)
        stopStream(CameraSelection.REAR)
        stopStream(CameraSelection.FRONT)
        configuration = null
        concurrentMode = false
        cameraController.close()
    }

    fun isRunning(selection: CameraSelection): Boolean = when (selection) {
        CameraSelection.REAR -> rearStream?.isStreaming == true
        CameraSelection.FRONT -> frontStream?.isStreaming == true
    }

    fun endpoint(selection: CameraSelection): String? = when (selection) {
        CameraSelection.REAR -> rearStream?.getStreamClient()?.getEndPointConnection()
        CameraSelection.FRONT -> frontStream?.getStreamClient()?.getEndPointConnection()
    }

    private fun createSelectedStream(configuration: RtspConfiguration) {
        val enabled = when (selectedCamera) {
            CameraSelection.REAR -> configuration.rearEnabled
            CameraSelection.FRONT -> configuration.frontEnabled
        }

        if (!enabled) {
            lastError = when (selectedCamera) {
                CameraSelection.REAR -> "Le flux caméra arrière est désactivé."
                CameraSelection.FRONT -> "Le flux caméra avant est désactivé."
            }
            return
        }

        val port = when (selectedCamera) {
            CameraSelection.REAR -> configuration.rearPort
            CameraSelection.FRONT -> configuration.frontPort
        }

        val stream = createStream(selectedCamera, port, configuration)
        when (selectedCamera) {
            CameraSelection.REAR -> rearStream = stream
            CameraSelection.FRONT -> frontStream = stream
        }
    }

    private fun createStreams(configuration: RtspConfiguration) {
        if (configuration.rearEnabled) {
            rearStream = createStream(CameraSelection.REAR, configuration.rearPort, configuration)
        }
        if (configuration.frontEnabled) {
            frontStream = createStream(CameraSelection.FRONT, configuration.frontPort, configuration)
        }
    }

    private fun createStream(
        selection: CameraSelection,
        port: Int,
        configuration: RtspConfiguration
    ): ConcurrentRtspServerStream? {
        return runCatching {
            val stream = ConcurrentRtspServerStream(
                context = context,
                connectChecker = checker(selection),
                cameraSelection = selection,
                controller = cameraController,
                port = port
            )
            check(
                stream.prepareVideo(
                    configuration.width,
                    configuration.height,
                    configuration.bitrate,
                    configuration.fps,
                    2,
                    0
                )
            ) {
                "encodeur H.264 indisponible"
            }
            check(stream.prepareAudio(44_100, true, 64_000)) {
                "préparation audio indisponible"
            }
            stream.setVideoCodec(VideoCodec.H264)
            stream.startStream()
            stream
        }.onFailure { error ->
            lastError = selection.name.lowercase() + ": " +
                (error.message ?: "échec du démarrage RTSP")
        }.getOrNull()
    }

    private fun streamFor(selection: CameraSelection): ConcurrentRtspServerStream? =
        when (selection) {
            CameraSelection.REAR -> rearStream
            CameraSelection.FRONT -> frontStream
        }

    private fun stopSelectedStream() {
        stopStream(selectedCamera)
    }

    private fun stopStream(selection: CameraSelection) {
        detachPreview(selection)
        when (selection) {
            CameraSelection.REAR -> {
                runCatching { rearStream?.stopStream() }
                runCatching { rearStream?.release() }
                rearStream = null
            }
            CameraSelection.FRONT -> {
                runCatching { frontStream?.stopStream() }
                runCatching { frontStream?.release() }
                frontStream = null
            }
        }
    }

    private fun attachPreview(selection: CameraSelection) {
        if (!previewSurfaceReady) return

        val stream = streamFor(selection) ?: run {
            lastError = selection.name.lowercase() + ": flux RTSP non disponible"
            return
        }

        runCatching {
            stream.startPreview(previewView)
        }.onFailure { error ->
            lastError = selection.name.lowercase() + ": " +
                (error.message ?: "échec de l'affichage de la prévisualisation")
        }
    }

    private fun detachPreview(selection: CameraSelection) {
        streamFor(selection)?.let { stream ->
            runCatching { stream.stopPreview() }
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
