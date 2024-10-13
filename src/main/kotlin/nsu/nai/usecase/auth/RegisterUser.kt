package nsu.nai.usecase.auth

import io.github.oshai.kotlinlogging.KotlinLogging
import nsu.nai.core.table.user.Users
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.security.crypto.bcrypt.BCrypt
import java.sql.Connection


class RegisterUser(
    private val usernameData: String,
    private val displayNameData: String,
    private val rawPassword: String,
    private val getNewConnection: () -> Connection
) {
    private val logger = KotlinLogging.logger { }

    fun execute(): Pair<String, Boolean> {
        // Hash the raw password using BCrypt
        val passwordHashData = BCrypt.hashpw(rawPassword, BCrypt.gensalt())

        Database.connect(getNewConnection)

        return try {
            transaction {
                // Insert the new user into the Users table
                Users.insert {
                    it[username] = usernameData
                    it[displayName] = displayNameData
                    it[passwordHash] = passwordHashData
                }
            }
            "User registered successfully" to true
        } catch (e: Exception) {
            logger.error { "Registration failed: $e" }
            "Registration failed: ${e.message}" to false
        }
    }
}