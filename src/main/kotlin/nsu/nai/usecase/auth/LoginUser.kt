package nsu.nai.usecase.auth

import nsu.nai.core.table.user.User
import nsu.nai.core.table.user.Users
import nsu.nai.exception.BadCredentials
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.security.crypto.bcrypt.BCrypt
import java.sql.Connection

class LoginUser(
    private val username: String,
    private val rawPassword: String,
    private val getConnection: () -> Connection
) {

    /**
     * Выполняет вход пользователя и возвращает пользователя с токенами доступа.
     *
     * @return Пара из пользователя и пары токенов (access и refresh).
     * @throws BadCredentials Если учетные данные неверны.
     */
    fun execute(): Pair<User, Pair<String, String>> {
        Database.connect(getConnection)

        val user = fetchUser() ?: throw BadCredentials()

        validatePassword(user.passwordHash)

        val accessToken = JwtTokenFactory.createAccessToken(user.id, user.username)
        val refreshToken = JwtTokenFactory.createRefreshToken(user.id)

        return user to (accessToken to refreshToken)
    }

    private fun fetchUser(): User? {
        return transaction {
            Users.selectAll().where { Users.username eq username }
                .mapNotNull { userRecord ->
                    User(
                        userRecord[Users.id],
                        userRecord[Users.username],
                        userRecord[Users.displayName],
                        userRecord[Users.passwordHash]
                    )
                }.singleOrNull()
        }
    }

    private fun validatePassword(storedPasswordHash: String) {
        if (!BCrypt.checkpw(rawPassword, storedPasswordHash)) {
            throw BadCredentials()
        }
    }
}