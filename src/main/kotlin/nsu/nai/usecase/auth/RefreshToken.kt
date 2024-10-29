package nsu.nai.usecase.auth

import io.github.oshai.kotlinlogging.KotlinLogging
import nsu.nai.core.table.user.User
import nsu.nai.core.table.user.Users
import nsu.nai.exception.InvalidToken
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection

/**
 * Обновляет токен доступа, используя токен обновления.
 *
 * @property refreshToken Токен обновления, который необходимо проверить.
 * @property getNewConnection Функция для получения нового соединения с базой данных.
 */

class RefreshToken(
    private val refreshToken: String,
    // infrastructure
    private val getNewConnection: () -> Connection
) {
    private val logger = KotlinLogging.logger {}

    fun execute(): String {
        logger.info { "Starting token refresh process" }
        try {
            val userId = getUserIdFromToken(refreshToken)
            val user = findUserById(userId)
            val newAccessToken = createNewAccessToken(user)
            logger.info { "Token refresh successful for user: ${user.username}" }
            return newAccessToken
        } catch (e: Exception) {
            logger.error(e) { "Error during token refresh process" }
            throw e
        }
    }

    private fun getUserIdFromToken(token: String): Long {
        logger.debug { "Extracting user ID from refresh token" }
        if (!JwtTokenFactory.isRefreshToken(token)) {
            logger.error { "Invalid refresh token: not a refresh token" }
            throw InvalidToken()
        }
        return JwtTokenFactory.getUserIdFromToken(token) ?: run {
            logger.error { "Invalid refresh token: unable to extract user ID" }
            throw InvalidToken()
        }
    }

    private fun findUserById(userId: Long): User {
        logger.debug { "Fetching user data for ID: $userId" }
        Database.connect(getNewConnection)
        return transaction {
            Users.selectAll().where { Users.id eq userId }.singleOrNull()?.let { userRecord ->
                User(
                    userRecord[Users.id],
                    userRecord[Users.username],
                    userRecord[Users.displayName],
                    userRecord[Users.passwordHash]
                )
            } ?: run {
                logger.error { "User not found for ID: $userId" }
                throw InvalidToken()
            }
        }
    }

    private fun createNewAccessToken(user: User): String {
        logger.debug { "Creating new access token for user: ${user.username}" }
        return try {
            JwtTokenFactory.createAccessToken(user.id, user.username)
        } catch (e: Exception) {
            logger.error(e) { "Error creating new access token for user: ${user.username}" }
            throw e
        }
    }
}