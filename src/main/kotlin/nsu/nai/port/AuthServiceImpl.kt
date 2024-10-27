package nsu.nai.port

import AuthServiceGrpcKt
import NaiAuth.*
import empty
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.Status
import nsu.nai.exception.BadCredentials
import nsu.nai.exception.InvalidToken
import nsu.nai.exception.UserAlreadyExistException
import nsu.nai.exception.ValidationException
import nsu.nai.usecase.auth.LoginUser
import nsu.nai.usecase.auth.RefreshToken
import nsu.nai.usecase.auth.RegisterUser
import refreshTokenResponse
import signInResponse
import java.sql.Connection

/**
 * Реализация сервиса аутентификации.
 *
 * Этот класс предоставляет методы для регистрации пользователей, входа в систему и обновления токенов.
 *
 * @param connectionProvider Функция для получения соединения с базой данных.
 */
class AuthServiceImpl(
    private val connectionProvider: () -> Connection
) : AuthServiceGrpcKt.AuthServiceCoroutineImplBase() {
    private val logger = KotlinLogging.logger {}

    /**
     * Регистрация нового пользователя.
     *
     * @param request Запрос на регистрацию, содержащий имя пользователя, отображаемое имя и пароль.
     * @return Ничего, если регистрация прошла успешно.
     * @throws UserAlreadyExistException Если пользователь с таким именем уже существует.
     */
    override suspend fun register(request: RegisterRequest): Empty {
        return handleRequest {
            RegisterUser(
                request.pUsername,
                request.pDisplayName,
                request.pRawPassword,
                connectionProvider
            ).execute()

            empty {}
        }
    }

    /**
     * Вход пользователя в систему.
     *
     * @param request Запрос на вход, содержащий имя пользователя и пароль.
     * @return Ответ с данными о пользователе и токенами доступа.
     * @throws BadCredentials Если предоставлены неверные учетные данные (как логин, так и пароль).
     */
    override suspend fun signIn(request: SignInRequest): SignInResponse {
        return handleRequest {
            val (user, access, refresh) = LoginUser(
                request.pUsername,
                request.pRawPassword,
                connectionProvider
            ).execute()

            signInResponse {
                pUsername = request.pUsername
                pDisplayName = user.displayName
                pAccessToken = access
                pRefreshToken = refresh
            }
        }
    }

    /**
     * Обновление токена доступа.
     *
     * @param request Запрос на обновление токена, содержащий токен обновления.
     * @return Ответ с новым токеном доступа.
     * @throws InvalidToken Если предоставленный токен обновления недействителен.
     */
    override suspend fun refreshToken(request: RefreshTokenRequest): RefreshTokenResponse {
        return handleRequest {
            val generatedAccessToken = RefreshToken(
                request.pRefreshToken,
                connectionProvider
            ).execute()

            refreshTokenResponse {
                pAccessToken = generatedAccessToken
            }
        }
    }

    /**
     * Обработка запросов с учетом возможных исключений.
     *
     * @param action Действие, в котором выполняется use-case.
     * @throws Status.ALREADY_EXISTS Если пользователь с таким именем уже существует.
     * @throws Status.UNAUTHENTICATED Если предоставлены неверные учетные данные или токен.
     * @throws Status.INVALID_ARGUMENT Если входные данные некорректны.
     * @throws Status.INTERNAL В случае непредвиденной ошибки.
     */
    private suspend fun <T> handleRequest(action: suspend () -> T): T {
        return try {
            action()
        } catch (exception: UserAlreadyExistException) {
            throw Status.ALREADY_EXISTS.withDescription(exception.message).asRuntimeException()
        } catch (exception: BadCredentials) {
            throw Status.UNAUTHENTICATED.withDescription(exception.message).asRuntimeException()
        } catch (exception: InvalidToken) {
            throw Status.UNAUTHENTICATED.withDescription(exception.message).asRuntimeException()
        } catch (exception: ValidationException) {
            // TODO: Добавить проверки валидации и вернуть список нарушенных ограничений.
            throw Status.INVALID_ARGUMENT.withDescription(exception.message).asRuntimeException()
        } catch (exception: Throwable) {
            logger.error(exception) { "Unhandled exception" }
            throw Status.INTERNAL.withDescription("Internal server error").asRuntimeException()
        }
    }
}