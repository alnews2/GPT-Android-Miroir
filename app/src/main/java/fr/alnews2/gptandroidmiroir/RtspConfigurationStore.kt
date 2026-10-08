package fr.alnews2.gptandroidmiroir

import android.content.Context

class RtspConfigurationStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): RtspConfiguration = RtspConfiguration(
        rearEnabled = preferences.getBoolean(KEY_REAR_ENABLED, true),
        frontEnabled = preferences.getBoolean(KEY_FRONT_ENABLED, true),
        rearPort = preferences.getInt(KEY_REAR_PORT, 8554),
        frontPort = preferences.getInt(KEY_FRONT_PORT, 8555),
        width = preferences.getInt(KEY_WIDTH, 1280),
        height = preferences.getInt(KEY_HEIGHT, 720),
        fps = preferences.getInt(KEY_FPS, 30),
        bitrate = preferences.getInt(KEY_BITRATE, 2_000_000)
    )

    fun save(configuration: RtspConfiguration) {
        preferences.edit()
            .putBoolean(KEY_REAR_ENABLED, configuration.rearEnabled)
            .putBoolean(KEY_FRONT_ENABLED, configuration.frontEnabled)
            .putInt(KEY_REAR_PORT, configuration.rearPort)
            .putInt(KEY_FRONT_PORT, configuration.frontPort)
            .putInt(KEY_WIDTH, configuration.width)
            .putInt(KEY_HEIGHT, configuration.height)
            .putInt(KEY_FPS, configuration.fps)
            .putInt(KEY_BITRATE, configuration.bitrate)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "rtsp_configuration"
        const val KEY_REAR_ENABLED = "rear_enabled"
        const val KEY_FRONT_ENABLED = "front_enabled"
        const val KEY_REAR_PORT = "rear_port"
        const val KEY_FRONT_PORT = "front_port"
        const val KEY_WIDTH = "width"
        const val KEY_HEIGHT = "height"
        const val KEY_FPS = "fps"
        const val KEY_BITRATE = "bitrate"
    }
}
