package fr.alnews2.gptandroidmiroir

import android.graphics.SurfaceTexture
import android.os.Build
import android.view.Surface
import com.pedro.encoder.input.sources.video.VideoSource

class ConcurrentCameraSource(
    private val cameraSelection: CameraSelection,
    private val controller: ConcurrentCameraController
) : VideoSource() {
    override fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            throw IllegalStateException("La capture simultanée avant/arrière nécessite Android 11 ou supérieur.")
        }
        return true
    }

    override fun start(surfaceTexture: SurfaceTexture) {
        this.surfaceTexture = surfaceTexture
        surfaceTexture.setDefaultBufferSize(width, height)
        controller.register(cameraSelection, Surface(surfaceTexture), width, height, fps)
    }

    override fun stop() { controller.unregister(cameraSelection) }
    override fun release() { controller.unregister(cameraSelection) }
    override fun isRunning(): Boolean = controller.isRunning(cameraSelection)
}
