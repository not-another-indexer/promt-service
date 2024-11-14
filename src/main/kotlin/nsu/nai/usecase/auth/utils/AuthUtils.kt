package nsu.nai.usecase.auth.utils

import java.util.regex.Pattern

val SPECIAL_CHARS: Pattern = Pattern.compile("[$#%&]")

fun validateCredentials(rawPassword: String, username: String): List<String> {
    val errors = mutableListOf<String>()

    val allowedSpecialChars = Pattern.compile("[^a-zA-Z0-9_-]")

    if (username.length < 3) errors.add("Username must be at least 3 characters long")
    if (username.length > 64) errors.add("Username must not exceed 64 characters")
    if (username.isBlank()) errors.add("Username must not be empty")
    if (allowedSpecialChars.matcher(username).find()) {
        errors.add("Username can only contain letters, numbers, '-', or '_'")
    }
    if (rawPassword.length < 8) errors.add("Password must be at least 8 characters long")
    if (rawPassword.length > 128) errors.add("Password must not exceed 128 characters")
    if (rawPassword.isBlank()) errors.add("Password must not be empty")
    if (rawPassword.contains(" ")) errors.add("Password must not contain spaces")
    if (!rawPassword.any { it.isDigit() }) errors.add("Password must contain at least 1 digit")
    if (!rawPassword.any { it.isUpperCase() }) errors.add("Password must contain at least 1 uppercase letter")
    if (!rawPassword.any { it.isLowerCase() }) errors.add("Password must contain at least 1 lowercase letter")
    if (!SPECIAL_CHARS.matcher(rawPassword).find()) errors.add("Password must contain at least 1 special character from $ # % &")

    return errors
}