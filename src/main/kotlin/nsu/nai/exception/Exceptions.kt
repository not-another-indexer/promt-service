package nsu.nai.exception

import nsu.nai.core.table.image.ImageExtension

class BadCredentials : Exception("bad credentials")

class InvalidToken : Exception("invalid or expired token")

class UserAlreadyExistException(username: String) : Exception("user [$username] already exists")

class ValidationException(message: String) : Exception(message)

class InvalidExtensionException(message: String) : Exception("extension [$message] is not allowed, , allowed extension " + ImageExtension.entries)

class EntityNotFoundException(entityName: String) : Exception("entity [$entityName] not found")

class EntityAlreadyExistsException(entityName: String) : Exception("entity [$entityName] already exists")

class InProcessException : Exception()

class MetadataNotFoundException() : Exception("metadata not found")