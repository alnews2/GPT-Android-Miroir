package fr.alnews2.gptandroidmiroir

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RtspConfigurationTest {

    @Test
    fun defaultConfigurationSupportsTwoDistinctStreams() {
        val configuration = RtspConfiguration()
        assertTrue(configuration.rearEnabled)
        assertTrue(configuration.frontEnabled)
        assertTrue(configuration.validate().isEmpty())
        assertTrue(configuration.rearPort != configuration.frontPort)
    }

    @Test
    fun identicalPortsAreRejectedWhenBothStreamsAreEnabled() {
        val configuration = RtspConfiguration(rearPort = 8554, frontPort = 8554)
        assertTrue(configuration.validate().any { it.contains("différents") })
    }

    @Test
    fun invalidPortIsRejected() {
        val configuration = RtspConfiguration(rearPort = 80)
        assertFalse(configuration.validate().isEmpty())
    }

    @Test
    fun atLeastOneStreamMustBeEnabled() {
        val configuration = RtspConfiguration(rearEnabled = false, frontEnabled = false)
        assertTrue(configuration.validate().any { it.contains("Au moins") })
    }

    @Test
    fun videoParametersAreValidated() {
        val configuration = RtspConfiguration(width = 100, fps = 120, bitrate = 100)
        val errors = configuration.validate()
        assertTrue(errors.size >= 3)
    }
}
