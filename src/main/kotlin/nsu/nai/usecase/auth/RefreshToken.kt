package nsu.nai.usecase.auth

import io.github.oshai.kotlinlogging.KotlinLogging
import nsu.nai.core.table.user.User
import nsu.nai.core.table.user.Users
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection

class RefreshToken(
    private val refreshToken: String,
    //
    private val getNewConnection: () -> Connection
) {
    private val logger = KotlinLogging.logger { }

    fun execute(): String? {
        Database.connect(getNewConnection)

        val userIdData: Long = JwtTokenFactory.getUserIdFromToken(refreshToken)
            ?: return "Invalid refresh token"

        return try {
            val user = transaction {
                val userRecord = Users.selectAll().where { Users.id eq userIdData }.single()

                User(
                    userRecord[Users.id],
                    userRecord[Users.username],
                    userRecord[Users.displayName],
                    userRecord[Users.passwordHash]
                )
            }

            val newAccessToken = JwtTokenFactory.createAccessToken(user.id, user.username)

            newAccessToken
        } catch (e: Exception) {
            logger.error { "Failed to refresh token: $e" }
            null
        }
    }
}