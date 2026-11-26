package tv.own.owntv.features.activation

/** Future client contract. QA has no configured provider, signing key or enforcement entry point. */
internal object XpActivation {
    const val DEFAULT_DURATION_MS = 30 * 24 * 60 * 60 * 1_000L

    enum class Access { LEGACY, ACTIVE, EXPIRED, BLOCKED, VALIDATION_REQUIRED }

    data class Policy(
        val accountId: Long,
        val supported: Boolean,
        val enforced: Boolean,
        val blocked: Boolean,
        val permanent: Boolean,
        val serverTimeMs: Long,
        val expiresAtMs: Long?,
        val offlineGraceMs: Long?,
        val administratorMessage: String? = null,
    )

    data class Validation(
        val policy: Policy,
        val monotonicAtValidation: Long,
        val bootSession: Long,
    )

    /** A provider must authenticate the complete payload before returning it to the policy evaluator. */
    fun acceptsSignature(payload: ByteArray, signature: ByteArray, key: java.security.PublicKey): Boolean =
        runCatching {
            java.security.Signature.getInstance("SHA256withECDSA").run {
                initVerify(key)
                update(payload)
                verify(signature)
            }
        }.getOrDefault(false)

    fun evaluate(
        validation: Validation?,
        accountId: Long?,
        monotonicNow: Long,
        bootSession: Long,
    ): Access {
        val policy = validation?.policy ?: return Access.LEGACY
        if (!policy.supported || !policy.enforced) return Access.LEGACY
        if (policy.blocked) return Access.BLOCKED
        if (policy.expiresAtMs?.let { it <= policy.serverTimeMs } == true && !policy.permanent) return Access.EXPIRED
        if (accountId != policy.accountId || bootSession != validation.bootSession ||
            monotonicNow < validation.monotonicAtValidation) return Access.VALIDATION_REQUIRED
        val age = monotonicNow - validation.monotonicAtValidation
        val expiry = policy.expiresAtMs
        if (!policy.permanent && expiry != null && age >= expiry - policy.serverTimeMs) return Access.EXPIRED
        if (!policy.permanent && expiry == null) return Access.VALIDATION_REQUIRED
        if (policy.offlineGraceMs?.let { age > it } == true) return Access.VALIDATION_REQUIRED
        return Access.ACTIVE
    }

    /** A failed or missing validation leaves the last explicitly accepted policy in force. */
    fun update(previous: Validation?, verified: Validation?): Validation? {
        if (verified == null || !verified.policy.supported) return previous
        if (previous != null && previous.policy.accountId == verified.policy.accountId &&
            previous.policy.serverTimeMs >= verified.policy.serverTimeMs) return previous
        return verified
    }
}
