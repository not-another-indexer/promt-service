package nsu.nai.usecase.auth.utils

import java.util.regex.Pattern

val SPECIAL_CHARS: Pattern = Pattern.compile("[$#%&]")

fun validateCredentials(rawPassword: String, username: String): List<String> {
    val errors = mutableListOf<String>()

    val allowedSpecialChars = Pattern.compile("[^a-zA-Z0-9_-]")

    if (username.length < 3) errors.add("Имя пользователя должно содержать не менее 3 символов")
    if (username.length > 64) errors.add("Имя пользователя не должно превышать 64 символов")
    if (username.isBlank()) errors.add("Имя пользователя не должно быть пустым")
    if (allowedSpecialChars.matcher(username).find()) {
        errors.add("Имя пользователя может содержать только буквы, цифры, '-', или '_'")
    }
    if (rawPassword.length < 8) errors.add("Пароль должен содержать не менее 8 символов")
    if (rawPassword.length > 128) errors.add("Пароль не должен превышать 128 символов")
    if (rawPassword.isBlank()) errors.add("Пароль не должен быть пустым")
    if (rawPassword.contains(" ")) errors.add("Пароль не должен содержать пробелы")
    if (!rawPassword.any { it.isDigit() }) errors.add("Пароль должен содержать хотя бы 1 цифру")
    if (!rawPassword.any { it.isUpperCase() }) errors.add("Пароль должен содержать хотя бы 1 заглавную букву")
    if (!rawPassword.any { it.isLowerCase() }) errors.add("Пароль должен содержать хотя бы 1 строчную букву")
    if (!SPECIAL_CHARS.matcher(rawPassword).find()) errors.add("Пароль должен содержать хотя бы 1 специальный символ $ # % &")

    return errors
}