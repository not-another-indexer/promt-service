package nsu.gallery

import kotlinx.serialization.Serializable

@Serializable
data class GalleryDto(val galleryId: String, val galleryName: String, val preview: List<String>)

@Serializable
data class CreateGalleryRequest(val galleryName: String)

@Serializable
data class CreateGalleryResponse(val galleryId: String, val galleryName: String)

@Serializable
data class ImageResponse(val imageUrl: String)

@Serializable
data class ErrorResponse(val message: String, val details: List<String>)

@Serializable
data class GetGalleriesResponse(val content: List<GalleryDto>)