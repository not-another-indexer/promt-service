package nsu.gallery
import kotlinx.serialization.Serializable

@Serializable
data class GalleryDto(val galleryId: Long, val galleryName: String)

@Serializable
data class CreateGalleryRequest(val galleryName: String)

@Serializable
data class CreateGalleryResponse(val galleryDto : GalleryDto)

@Serializable
data class ImageResponse(val imageUrl: String)

@Serializable
data class ErrorResponse(val message: String, val details: List<String>)