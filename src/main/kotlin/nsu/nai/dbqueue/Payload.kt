package nsu.nai.dbqueue

import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class PutEntryPayload(
    val imageUUID: String
) {
    constructor(imageUUID: UUID) : this(imageUUID.toString())
}

@Serializable
data class RemoveEntryPayload(
    val imageUUID: String
) {
    constructor(imageUUID: UUID) : this(imageUUID.toString())
}

@Serializable
data class InitIndexPayload(
    val galleryUUID: String
) {
    constructor(galleryUUID: UUID) : this(galleryUUID.toString())
}

@Serializable
data class DestroyIndexPayload(
    val galleryUUID: String
) {
    constructor(galleryUUID: UUID) : this(galleryUUID.toString())
}