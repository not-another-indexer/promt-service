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
    private val getNewConnection: () -> Connection
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Выполняет процесс обновления токена доступа.
     *
     * @return Новый токен доступа.
     * @throws InvalidToken Если токен обновления недействителен или не найден.
     */
    fun execute(): String {
        validateRefreshToken(refreshToken)

        Database.connect(getNewConnection)

        val userId = getUserIdFromToken(refreshToken)

        val user = findUserById(userId)

        return JwtTokenFactory.createAccessToken(user.id, user.username)
    }

    private fun validateRefreshToken(token: String) {
        if (!JwtTokenFactory.isRefreshToken(token)) {
            logger.error { "An access token was received instead of an update token" }
            throw InvalidToken()
        }
    }

    private fun getUserIdFromToken(token: String): Long {
        return JwtTokenFactory.getUserIdFromToken(token)
            ?: throw InvalidToken()
    }

    private fun findUserById(userId: Long): User {
        return transaction {
            Users.selectAll().where { Users.id eq userId }.singleOrNull()?.let { userRecord ->
                User(
                    userRecord[Users.id],
                    userRecord[Users.username],
                    userRecord[Users.displayName],
                    userRecord[Users.passwordHash]
                )
            } ?: throw InvalidToken()
        }
    }
}