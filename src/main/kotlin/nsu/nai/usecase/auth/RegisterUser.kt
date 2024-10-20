package nsu.nai.usecase.auth

import nsu.nai.core.table.user.Users
import nsu.nai.exception.UserAlreadyExistException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.security.crypto.bcrypt.BCrypt
import java.sql.Connection

/**
 * Регистрация нового пользователя.
 *
 * @property username Имя пользователя.
 * @property displayName Отображаемое имя пользователя.
 * @property rawPassword Пароль пользователя в сыром виде.
 * @property getNewConnection Функция для получения нового соединения с базой данных.
 */
class RegisterUser(
    private val username: String,
    private val displayName: String,
    private val rawPassword: String,
    private val getNewConnection: () -> Connection
) {
    /**
     * Выполняет регистрацию пользователя.
     *
     * @throws UserAlreadyExistException Если пользователь с таким именем уже существует.
     */
    fun execute() {
        Database.connect(getNewConnection)

        transaction {
            ensureUserDoesNotExist(username)
            val passwordHashData = hashPassword(rawPassword)
            insertUser(username, displayName, passwordHashData)
        }
    }

    private fun ensureUserDoesNotExist(username: String) {
        val userExists = Users.selectAll().where { Users.username eq username }.singleOrNull() != null
        if (userExists) {
            throw UserAlreadyExistException(username)
        }
    }

    private fun hashPassword(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt())
    }

    private fun insertUser(username: String, displayName: String, passwordHash: String) {
        Users.insert {
            it[Users.username] = username
            it[Users.displayName] = displayName
            it[Users.passwordHash] = passwordHash
        }
    }
}