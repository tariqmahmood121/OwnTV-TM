package tv.own.owntv.features.activation

import org.junit.Assert.assertEquals
import org.junit.Test

class XpActivationTest {
    private val time = 1_000_000L
    private val start = 500L
    private val policy = XpActivation.Policy(7, supported = true, enforced = true, blocked = false,
        permanent = false, serverTimeMs = time, expiresAtMs = time + XpActivation.DEFAULT_DURATION_MS,
        offlineGraceMs = null)
    private fun validation(p: XpActivation.Policy = policy, boot: Long = 1L) =
        XpActivation.Validation(p, start, boot)

    @Test fun missingSupportRemainsLegacyForGuestsAndAccounts() {
        assertEquals(XpActivation.Access.LEGACY, XpActivation.evaluate(null, null, start, 1))
        assertEquals(XpActivation.Access.LEGACY, XpActivation.evaluate(null, 7, start, 1))
    }

    @Test fun expiryUsesServerTimeAndElapsedTime() {
        assertEquals(XpActivation.Access.ACTIVE, XpActivation.evaluate(validation(), 7, start, 1))
        assertEquals(XpActivation.Access.EXPIRED, XpActivation.evaluate(validation(), 7,
            start + XpActivation.DEFAULT_DURATION_MS, 1))
        val shortened = validation(policy.copy(expiresAtMs = time + 50))
        assertEquals(XpActivation.Access.EXPIRED, XpActivation.evaluate(shortened, 7, start + 50, 1))
        val extended = validation(policy.copy(expiresAtMs = time + 2 * XpActivation.DEFAULT_DURATION_MS))
        assertEquals(XpActivation.Access.ACTIVE, XpActivation.evaluate(extended, 7,
            start + XpActivation.DEFAULT_DURATION_MS, 1))
    }

    @Test fun blockedOrDisconnectedAccountCannotUseGuestOrOtherProfile() {
        val blocked = validation(policy.copy(blocked = true))
        assertEquals(XpActivation.Access.BLOCKED, XpActivation.evaluate(XpActivation.update(blocked, null), null, 0, 2))
        assertEquals(XpActivation.Access.VALIDATION_REQUIRED, XpActivation.evaluate(validation(), null, start, 1))
        assertEquals(XpActivation.Access.VALIDATION_REQUIRED, XpActivation.evaluate(validation(), 8, start, 1))
        assertEquals(XpActivation.Access.VALIDATION_REQUIRED, XpActivation.evaluate(validation(), 7, 0, 2))
    }

    @Test fun newerVerifiedRenewalRestoresAccess() {
        val blocked = validation(policy.copy(blocked = true))
        val renewed = validation(policy.copy(serverTimeMs = time + 1, permanent = true, expiresAtMs = null))
        assertEquals(XpActivation.Access.ACTIVE, XpActivation.evaluate(XpActivation.update(blocked, renewed), 7, start, 1))
        assertEquals(blocked, XpActivation.update(blocked, validation()))
        assertEquals(blocked, XpActivation.update(blocked, validation(policy.copy(supported = false))))
    }
}
