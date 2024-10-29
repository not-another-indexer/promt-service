package nsu.nai.exception

import nsu.nai.core.table.image.ImageExtension
import java.util.*

class BadCredentials : Exception("bad credentials")

class InvalidToken : Exception("invalid or expired token")

class UserAlreadyExistException(username: String) : Exception("user [$username] already exists")

class ValidationException(message: String) : Exception(message)

class InvalidExtensionException(message: String) :
    Exception("extension [$message] is not allowed, , allowed extension " + ImageExtension.entries)

class EntityNotFoundException(uuid: UUID) : Exception("entity with id [$uuid] not found")

class EntityAlreadyExistsException(entityName: String) : Exception("entity [$entityName] already exists")

class ImageInProcessException(imageUuid: UUID) :
    Exception("Image $imageUuid is still in-process and cannot be removed.")

class MetadataNotFoundException() : Exception("metadata not found")