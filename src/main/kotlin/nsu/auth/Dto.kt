package nsu.auth

import kotlinx.serialization.Serializable

@Serializable
data class RegisterUserRequest(
    val username: String,
    val displayName: String,
    val rawPassword: String
)

@Serializable
data class SignInUserRequest(
    val username: String,
    val rawPassword: String
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

@Serializable
data class SignInUserResponse(
    val username: String,
    val displayName: String,
    val accessToken: String,
    val refreshToken: String
)

@Serializable
data class RefreshTokenResponse(
    val accessToken: String
)

@Serializable
data class ValidationErrorResponse(
    val usernameError: String
)

@Serializable
data class AuthorizationErrorResponse(
    val errorMessage: String,
    val username: String
)