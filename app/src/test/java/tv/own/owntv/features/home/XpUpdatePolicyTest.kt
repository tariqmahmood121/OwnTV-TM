package tv.own.owntv.features.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XpUpdatePolicyTest {
    @Test fun updateOnlyWhenNewerAndSupported() {
        assertTrue(XpUpdatePolicy.eligible(100089, 100090, 1))
        assertFalse(XpUpdatePolicy.eligible(100089, 100089, 1))
        assertFalse(XpUpdatePolicy.eligible(100090, 100089, 1))
        assertFalse(XpUpdatePolicy.eligible(100089, 100090, 100090))
    }
    @Test fun checksAtMostEveryDayUnlessManuallyRequested() {
        val now = 1730000000000L
        assertTrue(XpUpdatePolicy.due(now, 0L, true))
        assertFalse(XpUpdatePolicy.due(now, now - 1000L, true))
        assertFalse(XpUpdatePolicy.due(now, now - XpUpdatePolicy.DAY_MS + 1, true))
        assertTrue(XpUpdatePolicy.due(now, now - XpUpdatePolicy.DAY_MS, true))
        assertFalse(XpUpdatePolicy.due(now, 0L, false))
        assertTrue(XpUpdatePolicy.due(now, now + 1000L, true))
    }
}
