package nsu.nai.usecase.auth

import io.github.oshai.kotlinlogging.KotlinLogging
import nsu.nai.core.table.user.User
import nsu.nai.core.table.user.Users
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.security.crypto.bcrypt.BCrypt
import java.sql.Connection

class LoginUser(
    private val usernameData: String,
    private val rawPassword: String,
    //
    private val getNewConnection: () -> Connection
) {
    private val logger = KotlinLogging.logger { }

    fun execute(): Pair<User, Pair<String, String>>? {
        Database.connect(getNewConnection)

        return try {
            val user = transaction {
                val userRecord = Users.selectAll().where { Users.username eq usernameData }.single()

                User(
                    userRecord[Users.id],
                    userRecord[Users.username],
                    userRecord[Users.displayName],
                    userRecord[Users.passwordHash]
                )
            }

            if (!BCrypt.checkpw(rawPassword, user.passwordHash)) {
                logger.warn { "Invalid password for user: $usernameData" }
                return null
            }

            val accessToken = JwtTokenFactory.createAccessToken(user.id, user.username)
            val refreshToken = JwtTokenFactory.createRefreshToken(user.id)

            return user to (accessToken to refreshToken)
        } catch (e: Exception) {
            logger.error { "Login failed: $e" }
            null
        }
    }
}
