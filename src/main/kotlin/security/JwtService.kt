package com.example.catering.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import java.util.Date

private const val SESSION_VERSION_CLAIM = "session_version"
private const val JWT_ISSUER = "catering-backend"
private const val JWT_AUDIENCE = "foodhub-android"

class JwtService {

    private val secret =
        System.getenv("CATERING_JWT_SECRET")
            ?.takeIf { it.isNotBlank() }
            ?: error("Не задан CATERING_JWT_SECRET")

    private val algorithm = Algorithm.HMAC256(secret)

    fun generateToken(
        userId: Int,
        sessionVersion: Long = 0L
    ): String {

        require(userId > 0)
        require(sessionVersion >= 0L)

        val now = System.currentTimeMillis()
        val expiresAt = now + 14L * 24 * 60 * 60 * 1000

        return JWT.create()
            .withIssuer(JWT_ISSUER)
            .withAudience(JWT_AUDIENCE)
            .withSubject(userId.toString())
            .withClaim(
                SESSION_VERSION_CLAIM,
                sessionVersion
            )
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(expiresAt))
            .sign(algorithm)
    }

    fun verifier() = JWT
        .require(algorithm)
        .withIssuer(JWT_ISSUER)
        .withAudience(JWT_AUDIENCE)
        .build()

    fun isSessionVersionCurrent(
        token: DecodedJWT,
        currentVersion: Long
    ): Boolean {

        val tokenVersion = token
            .getClaim(SESSION_VERSION_CLAIM)
            .asLong()

        return tokenVersion != null &&
                tokenVersion == currentVersion
    }
}