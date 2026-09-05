package uz.abumme.harfgame.backend.auth.oauth

/**
 * Revokes a user's Sign in with Apple tokens on account deletion (Apple TN3194). The real
 * implementation exchanges a client secret signed with the Apple `.p8` key; until that key is
 * configured, [NoOpAppleTokenRevoker] is wired so deletion still works and the call site is in place.
 */
interface AppleTokenRevoker {
    /** Revoke Apple tokens for the given Apple provider subject. */
    suspend fun revoke(providerSubject: String)
}

/** Placeholder used until the Apple private key is configured. Does nothing, never fails. */
object NoOpAppleTokenRevoker : AppleTokenRevoker {
    override suspend fun revoke(providerSubject: String) { /* no-op until the Apple key exists */ }
}
