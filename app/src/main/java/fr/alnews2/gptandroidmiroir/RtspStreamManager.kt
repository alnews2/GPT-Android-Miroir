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

        if (configuration.rearEnabled && configuration.frontEnabled &&
            !cameraController.isConcurrentSupported()
        ) {
            lastError =
                "Ce téléphone ne prend pas en charge la capture simultanée des caméras avant et arrière."
            return
        }

        createStreams(configuration)
        lastError = cameraController.lastError ?: lastError
        if (previewSurfaceReady) attachPreview(selectedCamera)
    }

    fun selectCamera(selection: CameraSelection) {
        if (selectedCamera == selection) return
        detachPreview(selectedCamera)
        selectedCamera = selection
        lastError = null
        attachPreview(selection)
        lastError = cameraController.lastError ?: lastError
    }

    fun stop() {
        detachPreview(selectedCamera)
        runCatching { rearStream?.stopStream() }
        runCatching { frontStream?.stopStream() }
        runCatching { rearStream?.release() }
        runCatching { frontStream?.release() }
        rearStream = null
        frontStream = null
        configuration = null
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

    private fun attachPreview(selection: CameraSelection) {
        if (!previewSurfaceReady) return

        val stream = when (selection) {
            CameraSelection.REAR -> rearStream
            CameraSelection.FRONT -> frontStream
        } ?: run {
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
        val stream = when (selection) {
            CameraSelection.REAR -> rearStream
            CameraSelection.FRONT -> frontStream
        } ?: return

        runCatching { stream.stopPreview() }
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
