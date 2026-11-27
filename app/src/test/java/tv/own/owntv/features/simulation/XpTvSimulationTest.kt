package tv.own.owntv.features.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XpTvSimulationTest {
    @Test fun allResolutionsHaveTheSameAspectRatio() {
        for (resolution in XpTvResolution.entries) {
            assertEquals(16 * resolution.height, 9 * resolution.width)
            val scale = xpSimulationFit(1_000, 800, resolution)
            assertEquals(1_000f, resolution.width * scale, .01f)
            assertEquals(562.5f, resolution.height * scale, .01f)
        }
    }

    @Test fun viewportCanFitHeightAndHandleZeroSize() {
        assertEquals(.5f, xpSimulationFit(1_200, 540, XpTvResolution.FULL_HD), .001f)
        assertEquals(0f, xpSimulationFit(0, 540, XpTvResolution.FULL_HD), .001f)
    }

    @Test fun productionReleaseDoesNotExposeSimulation() {
        assertTrue(xpSimulationAvailable(true, false, "4.0 D"))
        assertTrue(xpSimulationAvailable(true, false, "3.1.0-qa6"))
        assertFalse(xpSimulationAvailable(true, false, "4.0"))
        assertFalse(xpSimulationAvailable(false, true, "4.0 D"))
    }
}
