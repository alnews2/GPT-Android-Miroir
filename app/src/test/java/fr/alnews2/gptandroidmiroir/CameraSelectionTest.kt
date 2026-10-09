package fr.alnews2.gptandroidmiroir

import org.junit.Assert.assertNotEquals
import org.junit.Test

class CameraSelectionTest {
    @Test
    fun frontAndRearAreDistinct() {
        assertNotEquals(CameraSelection.FRONT, CameraSelection.REAR)
    }
}
