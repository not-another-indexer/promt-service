package nsu.nai.usecase.auth.utils

import java.util.regex.Pattern

val SPECIAL_CHARS: Pattern = Pattern.compile("[^a-zA-Z0-9]")

fun validateCredentials(rawPassword: String, username: String): List<String> {
    val errors = mutableListOf<String>()

    if (username.length > 64) errors.add("username must not exceed 64 characters")
    if (username.isBlank()) errors.add("username must not be blank")

    if (!SPECIAL_CHARS.matcher(rawPassword).find()) errors.add("passwords must have at least 1 special character")
    if (rawPassword.length < 8) errors.add("password must be at least 8 characters")
    if (!rawPassword.any { it.isDigit() }) errors.add("password must contain at least 1 digit")
    if (!rawPassword.any { it.isUpperCase() }) errors.add("password must contain at least 1 upper case character")

    return errors
}
