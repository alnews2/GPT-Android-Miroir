package fr.alnews2.gptandroidmiroir

data class RtspConfiguration(
    val rearEnabled: Boolean = true,
    val frontEnabled: Boolean = true,
    val rearPort: Int = 8554,
    val frontPort: Int = 8555,
    val width: Int = 1280,
    val height: Int = 720,
    val fps: Int = 30,
    val bitrate: Int = 2_000_000
) {
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (!rearEnabled && !frontEnabled) errors += "Au moins un flux RTSP doit être activé."
        if (rearEnabled && rearPort !in 1024..65535) errors += "Le port arrière doit être compris entre 1024 et 65535."
        if (frontEnabled && frontPort !in 1024..65535) errors += "Le port avant doit être compris entre 1024 et 65535."
        if (rearEnabled && frontEnabled && rearPort == frontPort) errors += "Les ports avant et arrière doivent être différents."
        if (width !in 320..3840 || height !in 240..2160) errors += "La résolution doit être comprise entre 320x240 et 3840x2160."
        if (fps !in 1..60) errors += "La fréquence doit être comprise entre 1 et 60 FPS."
        if (bitrate !in 250_000..20_000_000) errors += "Le débit doit être compris entre 250 kbit/s et 20 Mbit/s."
        return errors
    }
}
