package nsu.nai.exception

class BadCredentials : Exception("bad credentials")

class InvalidToken : Exception("invalid or expired token")

class UserAlreadyExistException(username: String) : Exception("user [$username] already exists")

class ValidationException(message: String) : Exception(message)