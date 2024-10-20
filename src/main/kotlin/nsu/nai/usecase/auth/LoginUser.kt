package nsu.nai.usecase.auth

import nsu.nai.core.table.user.User
import nsu.nai.core.table.user.Users
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.security.crypto.bcrypt.BCrypt
import java.sql.Connection

class UserNotFoundException(username: String) : Exception("user $username not found")
class BadCredentials : Exception("bad credentials")

class LoginUser(
    private val usernameData: String,
    private val rawPassword: String,
    //
    private val getNewConnection: () -> Connection
) {
    fun execute(): Pair<User, Pair<String, String>> {
        Database.connect(getNewConnection)

        val user = transaction {
            val userRecord = Users.selectAll().where { Users.username eq usernameData }.singleOrNull()
            if (userRecord == null) {
                throw UserNotFoundException(usernameData)
            }

            User(
                userRecord[Users.id],
                userRecord[Users.username],
                userRecord[Users.displayName],
                userRecord[Users.passwordHash]
            )
        }

        if (!BCrypt.checkpw(rawPassword, user.passwordHash)) {
            throw BadCredentials()
        }

        val accessToken = JwtTokenFactory.createAccessToken(user.id, user.username)
        val refreshToken = JwtTokenFactory.createRefreshToken(user.id)

        return user to (accessToken to refreshToken)
    }
}
