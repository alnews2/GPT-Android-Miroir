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
    private var singleStream: SingleRtspServerStream? = null
    private var configuration: RtspConfiguration? = null
    private var selectedCamera = CameraSelection.REAR
    private var previewSurfaceReady = false
    private var singleCameraMode = false

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
        selectedCamera = CameraSelection.REAR

        val bothEnabled = configuration.rearEnabled && configuration.frontEnabled
        singleCameraMode = !bothEnabled || !cameraController.isConcurrentSupported()

        if (singleCameraMode) {
            val selection = if (configuration.rearEnabled) {
                CameraSelection.REAR
            } else {
                CameraSelection.FRONT
            }
            selectedCamera = selection
            singleStream = createSingleStream(selection, configuration)
        } else {
            createStreams(configuration)
        }

        lastError = cameraController.lastError ?: lastError
        if (previewSurfaceReady) attachPreview(selectedCamera)
    }

    fun selectCamera(selection: CameraSelection) {
        if (selectedCamera == selection) return
        val config = configuration ?: return

        if (singleCameraMode) {
            detachPreview(selectedCamera)
            singleStream?.let {
                runCatching { it.stopStream() }
                runCatching { it.release() }
            }
            singleStream = null

            selectedCamera = selection
            lastError = null
            singleStream = createSingleStream(selection, config)
            if (previewSurfaceReady) attachPreview(selection)
            return
        }

        detachPreview(selectedCamera)
        selectedCamera = selection
        lastError = null
        attachPreview(selection)
        lastError = cameraController.lastError ?: lastError
    }

    fun stop() {
        detachPreview(selectedCamera)
        runCatching { singleStream?.stopStream() }
        runCatching { rearStream?.stopStream() }
        runCatching { frontStream?.stopStream() }
        runCatching { singleStream?.release() }
        runCatching { rearStream?.release() }
        runCatching { frontStream?.release() }
        singleStream = null
        rearStream = null
        frontStream = null
        configuration = null
        singleCameraMode = false
        cameraController.close()
    }

    fun isRunning(selection: CameraSelection): Boolean {
        if (singleCameraMode) {
            return selectedCamera == selection && singleStream?.isStreaming == true
        }
        return when (selection) {
            CameraSelection.REAR -> rearStream?.isStreaming == true
            CameraSelection.FRONT -> frontStream?.isStreaming == true
        }
    }

    fun endpoint(selection: CameraSelection): String? {
        if (singleCameraMode) {
            if (selectedCamera != selection) return null
            return singleStream?.getStreamClient()?.getEndPointConnection()
        }
        return when (selection) {
            CameraSelection.REAR -> rearStream?.getStreamClient()?.getEndPointConnection()
            CameraSelection.FRONT -> frontStream?.getStreamClient()?.getEndPointConnection()
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

    private fun createSingleStream(
        selection: CameraSelection,
        configuration: RtspConfiguration
    ): SingleRtspServerStream? {
        val port = when (selection) {
            CameraSelection.REAR -> configuration.rearPort
            CameraSelection.FRONT -> configuration.frontPort
        }

        return runCatching {
            val stream = SingleRtspServerStream(
                context = context,
                connectChecker = checker(selection),
                port = port
            )
            // Camera2Source defaults to the rear camera. Select the front camera
            // before preparing and starting the stream when requested.
            if (selection == CameraSelection.FRONT) {
                stream.switchCamera()
            }
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

        val stream = if (singleCameraMode) {
            if (selectedCamera != selection) return
            singleStream
        } else {
            when (selection) {
                CameraSelection.REAR -> rearStream
                CameraSelection.FRONT -> frontStream
            }
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
        val stream = if (singleCameraMode) {
            singleStream
        } else {
            when (selection) {
                CameraSelection.REAR -> rearStream
                CameraSelection.FRONT -> frontStream
            }
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
