package uz.abumme.harfgame.backend.service

import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import uz.abumme.harfgame.backend.auth.oauth.OAuthVerifier
import uz.abumme.harfgame.backend.db.DatabaseFactory
import uz.abumme.harfgame.backend.db.OAuthIdentitiesTable
import uz.abumme.harfgame.backend.db.RefreshTokensTable
import uz.abumme.harfgame.backend.db.UsersTable
import uz.abumme.harfgame.backend.security.JwtService
import uz.abumme.harfgame.backend.security.TokenUtils
import uz.abumme.harfgame.data.auth.*
import java.util.UUID

class AuthServerService(
    val jwtService: JwtService,
    val verifiers: Map<OAuthProvider, OAuthVerifier> = emptyMap(),
) {

    suspend fun createAnonymousAccount(): AnonymousAuthResponse {
        val userId = UUID.randomUUID().toString()
        val nowMillis = System.currentTimeMillis()
        val nowInstant = java.time.Instant.ofEpochMilli(nowMillis)
        val rawRefreshToken = TokenUtils.generateSecureToken()
        val tokenHash = TokenUtils.hashToken(rawRefreshToken)
        val expiresAt = java.time.Instant.ofEpochMilli(nowMillis + 30L * 24 * 3600 * 1000)
        val tokenId = UUID.randomUUID().toString()

        DatabaseFactory.dbQuery {
            UsersTable.insert {
                it[id] = userId
                it[createdAt] = nowInstant
            }

            RefreshTokensTable.insert {
                it[id] = tokenId
                it[RefreshTokensTable.userId] = userId
                it[RefreshTokensTable.tokenHash] = tokenHash
                it[createdAt] = nowInstant
                it[RefreshTokensTable.expiresAt] = expiresAt
            }
        }

        val accessToken = jwtService.generateAccessToken(userId)
        return AnonymousAuthResponse(
            userId = userId,
            tokens = TokenPairDto(
                accessToken = accessToken,
                refreshToken = rawRefreshToken
            )
        )
    }

    suspend fun refreshToken(rawToken: String): RefreshResponse? {
        val hash = TokenUtils.hashToken(rawToken)
        val nowMillis = System.currentTimeMillis()
        val nowInstant = java.time.Instant.ofEpochMilli(nowMillis)

        return DatabaseFactory.dbQuery {
            val existingRow = RefreshTokensTable
                .selectAll()
                .where { RefreshTokensTable.tokenHash eq hash }
                .singleOrNull() ?: return@dbQuery null

            val userId = existingRow[RefreshTokensTable.userId]
            val revokedAt = existingRow[RefreshTokensTable.revokedAt]
            val expiresAt = existingRow[RefreshTokensTable.expiresAt]
            val replacedBy = existingRow[RefreshTokensTable.replacedBy]
            val rotatedAt = existingRow[RefreshTokensTable.rotatedAt]

            // Check if expired or revoked
            if (expiresAt.toEpochMilli() < nowMillis || revokedAt != null) {
                RefreshTokensTable.update({ RefreshTokensTable.userId eq userId }) {
                    it[RefreshTokensTable.revokedAt] = nowInstant
                }
                return@dbQuery null
            }

            // Check if already rotated
            if (replacedBy != null && rotatedAt != null) {
                val elapsed = nowMillis - rotatedAt.toEpochMilli()
                if (elapsed in 0..60_000L) {
                    val graceToken = existingRow[RefreshTokensTable.graceReplacementToken]
                    if (graceToken != null) {
                        val newAccessToken = jwtService.generateAccessToken(userId)
                        return@dbQuery RefreshResponse(
                            tokens = TokenPairDto(
                                accessToken = newAccessToken,
                                refreshToken = graceToken
                            )
                        )
                    }
                }
                // Reuse outside grace window -> revoke all tokens for this account
                RefreshTokensTable.update({ RefreshTokensTable.userId eq userId }) {
                    it[RefreshTokensTable.revokedAt] = nowInstant
                }
                return@dbQuery null
            }

            // Normal rotation
            val newRawRefreshToken = TokenUtils.generateSecureToken()
            val newHash = TokenUtils.hashToken(newRawRefreshToken)
            val newExpiresAt = java.time.Instant.ofEpochMilli(nowMillis + 30L * 24 * 3600 * 1000)
            val newId = UUID.randomUUID().toString()

            RefreshTokensTable.insert {
                it[id] = newId
                it[RefreshTokensTable.userId] = userId
                it[tokenHash] = newHash
                it[createdAt] = nowInstant
                it[RefreshTokensTable.expiresAt] = newExpiresAt
            }

            RefreshTokensTable.update({ RefreshTokensTable.id eq existingRow[RefreshTokensTable.id] }) {
                it[RefreshTokensTable.replacedBy] = newId
                it[RefreshTokensTable.rotatedAt] = nowInstant
                it[graceReplacementToken] = newRawRefreshToken
            }

            val newAccessToken = jwtService.generateAccessToken(userId)
            RefreshResponse(
                tokens = TokenPairDto(
                    accessToken = newAccessToken,
                    refreshToken = newRawRefreshToken
                )
            )
        }
    }

    suspend fun linkAccount(currentUserId: String, request: LinkAccountRequest): LinkAccountResponse? {
        val verifier = verifiers[request.provider] ?: return null
        val oauthResult = verifier.verify(request.idToken) ?: return null
        val providerName = request.provider.name
        val subject = oauthResult.subject

        return DatabaseFactory.dbQuery {
            val existingIdentity = OAuthIdentitiesTable
                .selectAll()
                .where {
                    (OAuthIdentitiesTable.provider eq providerName) and
                    (OAuthIdentitiesTable.providerSubject eq subject)
                }
                .singleOrNull()

            val targetUserId: String
            if (existingIdentity == null) {
                OAuthIdentitiesTable.insert {
                    it[id] = UUID.randomUUID().toString()
                    it[userId] = currentUserId
                    it[provider] = providerName
                    it[providerSubject] = subject
                }
                targetUserId = currentUserId
            } else {
                val ownerUserId = existingIdentity[OAuthIdentitiesTable.userId]
                if (ownerUserId == currentUserId) {
                    targetUserId = currentUserId
                } else {
                    // Merge: Existing account wins, delete orphaned anonymous account and its data
                    UsersTable.deleteWhere { UsersTable.id eq currentUserId }
                    targetUserId = ownerUserId
                }
            }

            val newRawRefreshToken = TokenUtils.generateSecureToken()
            val newHash = TokenUtils.hashToken(newRawRefreshToken)
            val nowMillis = System.currentTimeMillis()
            val nowInstant = java.time.Instant.ofEpochMilli(nowMillis)
            val newExpiresAt = java.time.Instant.ofEpochMilli(nowMillis + 30L * 24 * 3600 * 1000)
            val tokenId = UUID.randomUUID().toString()

            RefreshTokensTable.insert {
                it[id] = tokenId
                it[userId] = targetUserId
                it[tokenHash] = newHash
                it[createdAt] = nowInstant
                it[expiresAt] = newExpiresAt
            }

            val newAccessToken = jwtService.generateAccessToken(targetUserId)
            LinkAccountResponse(
                userId = targetUserId,
                tokens = TokenPairDto(
                    accessToken = newAccessToken,
                    refreshToken = newRawRefreshToken
                )
            )
        }
    }

    suspend fun logout(userId: String) {
        val nowInstant = java.time.Instant.ofEpochMilli(System.currentTimeMillis())
        DatabaseFactory.dbQuery {
            RefreshTokensTable.update({ RefreshTokensTable.userId eq userId }) {
                it[revokedAt] = nowInstant
            }
        }
    }

    suspend fun deleteAccount(userId: String): Boolean {
        return DatabaseFactory.dbQuery {
            val deleted = UsersTable.deleteWhere { UsersTable.id eq userId }
            deleted > 0
        }
    }
}
