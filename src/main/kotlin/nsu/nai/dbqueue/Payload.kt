package nsu.nai.dbqueue

import kotlinx.serialization.Serializable

@Serializable
data class PutEntryPayload(
    val imageUUID: String
)

@Serializable
data class RemoveEntryPayload(
    val imageUUID: String
)

@Serializable
data class InitIndexPayload(
    val galleryUUID: String
)

@Serializable
data class DestroyIndexPayload(
    val galleryUUID: String
)