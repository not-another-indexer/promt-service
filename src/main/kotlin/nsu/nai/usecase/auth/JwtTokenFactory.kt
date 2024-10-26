package nsu.nai.usecase.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

object JwtTokenFactory {
    private const val SECRET = "your-secret-key"
    private val algorithm = Algorithm.HMAC256(SECRET)
    private val logger = KotlinLogging.logger {}
    private const val ISSUER = "auth-service"
    //TODO не забыть убрать
    private const val REFRESH_TOKEN_EXPIRATION: Long = 1000 * 60 * 15*1000 // 15 min expiration
    private const val ACCESS_TOKEN_EXPIRATION: Long = 1000 * 60 * 60 * 24 * 7 // 7 days expiration

    /**
     * Создает JWT токен с указанными параметрами.
     *
     * @param userId Идентификатор пользователя.
     * @param username Имя пользователя (необязательный параметр).
     * @param expiration Время действия токена в миллисекундах.
     * @return Созданный JWT токен.
     */
    private fun createToken(userId: Long, username: String? = null, expiration: Long): String {
        val builder = JWT.create()
            .withIssuer(ISSUER)
            .withSubject(userId.toString())
            .withExpiresAt(Date(System.currentTimeMillis() + expiration))

        username?.let { builder.withClaim("username", it) }

        return builder.sign(algorithm)
    }

    /**
     * Создает токен доступа с 15-минутным сроком действия.
     *
     * @param userId Идентификатор пользователя.
     * @param username Имя пользователя.
     * @return Созданный токен доступа.
     */
    fun createAccessToken(userId: Long, username: String): String {
        return createToken(userId, username, ACCESS_TOKEN_EXPIRATION)
    }

    /**
     * Создает токен обновления с 7-дневным сроком действия.
     *
     * @param userId Идентификатор пользователя.
     * @return Созданный токен обновления.
     */
    fun createRefreshToken(userId: Long): String {
        return createToken(userId, expiration = REFRESH_TOKEN_EXPIRATION)
    }

    /**
     * Проверяет и декодирует JWT токен.
     *
     * @param token JWT токен для проверки.
     * @return Декодированный JWT, если проверка прошла успешно; иначе null.
     */
    private fun verifyToken(token: String): DecodedJWT? {
        return try {
            JWT.require(algorithm)
                .withIssuer(ISSUER)
                .build()
                .verify(token)
        } catch (e: Exception) {
            logger.warn { "Token verification failed: $e" }
            null
        }
    }

    /**
     * Проверяет, является ли токен токеном обновления.
     *
     * @param token JWT токен для проверки.
     * @return true, если это токен обновления; иначе false.
     */
    fun isRefreshToken(token: String): Boolean {
        val decodedJWT = verifyToken(token) ?: return true
        return decodedJWT.getClaim("username").isMissing
    }

    /**
     * Извлекает идентификатор пользователя из токена.
     *
     * @param token JWT токен для извлечения идентификатора.
     * @return Идентификатор пользователя, если он найден; иначе null.
     */
    fun getUserIdFromToken(token: String): Long? {
        val decodedJWT = verifyToken(token) ?: return null
        return decodedJWT.subject.toLongOrNull()
    }
}