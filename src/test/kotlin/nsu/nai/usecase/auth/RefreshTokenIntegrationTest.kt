package nsu.nai.usecase.auth

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkObject
import nsu.nai.core.table.user.Users
import nsu.nai.exception.InvalidToken
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.security.crypto.bcrypt.BCrypt
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.sql.DriverManager

class RefreshTokenIntegrationTest : FunSpec({

    // Define the PostgreSQL container
    val postgresContainer = PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:latest")).apply {
        withDatabaseName("test_db")
        withUsername("test")
        withPassword("test")
        start()
    }

    lateinit var database: Database
    lateinit var connectionGetter: () -> Connection

    beforeSpec {
        // Set up database using Testcontainers
        database = Database.connect(
            url = postgresContainer.jdbcUrl,
            user = postgresContainer.username,
            password = postgresContainer.password,
            driver = "org.postgresql.Driver"
        )

        // Create a stable connection getter
        connectionGetter = {
            DriverManager.getConnection(
                postgresContainer.jdbcUrl,
                postgresContainer.username,
                postgresContainer.password
            )
        }
    }

    beforeTest {
        // Create tables and insert test user
        transaction(database) {
            SchemaUtils.create(Users)

            Users.insert {
                it[id] = 1
                it[username] = "testuser"
                it[displayName] = "Test User"
                it[passwordHash] = BCrypt.hashpw("password", BCrypt.gensalt())
            }
        }

        // Mock JwtTokenFactory
        mockkObject(JwtTokenFactory)
        every { JwtTokenFactory.isRefreshToken(any()) } returns true
        every { JwtTokenFactory.getUserIdFromToken(any()) } returns 1
        every { JwtTokenFactory.createAccessToken(any(), any()) } returns "new_access_token"
    }

    afterTest {
        // Clean up database
        transaction(database) {
            SchemaUtils.drop(Users)
        }
    }

    afterSpec {
        // Close the database connection after all tests
        postgresContainer.stop()
    }

    test("should successfully refresh token") {
        val refreshToken = RefreshToken("valid_refresh_token", connectionGetter)
        val result = refreshToken.execute()

        println(result)
        result shouldBe "new_access_token"
    }

    test("should throw InvalidToken for invalid refresh token") {
        every { JwtTokenFactory.isRefreshToken(any()) } returns false // Mocking invalid refresh token

        val refreshToken = RefreshToken("invalid_refresh_token", connectionGetter)

        // Expecting an InvalidToken exception
        shouldThrow<InvalidToken> {
            refreshToken.execute()
        }
    }

    test("should throw InvalidToken for non-existent user ID") {
        every { JwtTokenFactory.getUserIdFromToken(any()) } returns null // Mocking non-existent user ID

        val refreshToken = RefreshToken("valid_refresh_token", connectionGetter)

        // Expecting an InvalidToken exception
        shouldThrow<InvalidToken> {
            refreshToken.execute()
        }
    }
})