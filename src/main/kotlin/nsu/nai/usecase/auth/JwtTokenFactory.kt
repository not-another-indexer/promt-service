package nsu.nai.usecase.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

object JwtTokenFactory {
    private const val SECRET = "your-secret-key"
    private val algorithm = Algorithm.HMAC256(SECRET)
    private val logger = KotlinLogging.logger {}

    fun createAccessToken(userId: Long, username: String): String {
        return JWT.create()
            .withIssuer("auth-service")
            .withSubject(userId.toString())
            .withClaim("username", username)
            .withExpiresAt(Date(System.currentTimeMillis() + 1000 * 60 * 15)) // 15 min expiration
            .sign(algorithm)
    }

    fun createRefreshToken(userId: Long): String {
        return JWT.create()
            .withIssuer("auth-service")
            .withSubject(userId.toString())
            .withExpiresAt(Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 7)) // 7 days expiration
            .sign(algorithm)
    }

    fun validateToken(token: String): Boolean {
        return try {
            JWT.require(algorithm)
                .withIssuer("auth-service")
                .build()
                .verify(token)
            true
        } catch (e: Exception) {
            logger.warn { e }
            false
        }
    }

    fun getUserIdFromToken(token: String): Long? {
        return try {
            val decodedJWT = JWT.require(algorithm)
                .withIssuer("auth-service")
                .build()
                .verify(token)
            decodedJWT.subject.toLongOrNull()
        } catch (e: Exception) {
            logger.warn { e }
            null
        }
    }
}