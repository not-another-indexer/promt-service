package nsu.nai.port

import com.google.protobuf.ByteString
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.Context
import io.grpc.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import nai.*
import nai.Nai.*
import nsu.Config
import nsu.nai.core.Parameter
import nsu.nai.core.table.image.Image
import nsu.nai.core.table.image.ImageExtension
import nsu.nai.dbqueue.Producers
import nsu.nai.exception.*
import nsu.nai.usecase.gallery.*
import nsu.platform.userId
import java.io.ByteArrayOutputStream
import java.util.*

class GalleryServiceImpl(private val producers: Producers) : GalleryServiceGrpcKt.GalleryServiceCoroutineImplBase() {
    private val logger = KotlinLogging.logger {}

    override suspend fun getGalleryImages(request: GetGalleryImagesRequest): GetGalleryImagesResponse {
        val userId = Context.current().userId

        return handleRequest {
            val (images: List<Image>, totalSize: Long) = GetGalleryImages(
                userId,
                UUID.fromString(request.pGalleryUuid),
                request.pSize,
                request.pOffset.toLong(),
                Config.connectionProvider
            ).execute()

            val imageResponses = images.map { image ->
                image {
                    pImageId = image.id.toString()
                    pGalleryUuid = image.galleryId.toString()
                    pDescription = image.description
                }
            }
            getGalleryImagesResponse {
                pContent.addAll(imageResponses)
                pTotal = totalSize
            }
        }
    }

    override suspend fun addImage(request: AddImageRequest): AddImageResponse {
        return handleRequest {
            val userId = Context.current().userId
            val metadata: ContentMetadata? = request.pMetadata
            val imageChunks = ByteArrayOutputStream()
            request.pChunkData.writeTo(imageChunks)

            metadata ?: throw MetadataNotFoundException()
            val imageExtension = ImageExtension.parse(metadata.pExtension)
                ?: throw InvalidExtensionException(metadata.pExtension)

            val image = AddImage(
                userId,
                galleryUuid = UUID.fromString(metadata.pGalleryUuid),
                imageDescription = metadata.pDescription,
                imageExtension = imageExtension,
                imageContent = imageChunks.toByteArray(),
                getNewConnection = Config.connectionProvider,
                putEntryProducer = producers.putEntry
            ).execute()

            addImageResponse {
                pImageUuid = image.id.toString()
            }
        }
    }

    override suspend fun deleteImage(request: DeleteImageRequest): Empty {
        return handleRequest {
            val userId = Context.current().userId
            RemoveImage(
                userId,
                UUID.fromString(request.pImageUuid),
                Config.connectionProvider,
                producers.removeEntry
            ).execute()

            empty { }
        }
    }

    override suspend fun getImageContent(request: GetImageContentRequest): GetImageContentResponse {
        return handleRequest {
            val imageId = request.pImageUuid
            val userId = Context.current().userId

            val imageIdentifier = UUID.fromString(imageId)

            runBlocking {
                val inputStream = GetImageContent(userId, imageIdentifier, Config.connectionProvider).execute()

                getImageContentResponse {
                    pChunkData = ByteString.readFrom(inputStream)
                }
            }
        }
    }

    override suspend fun searchImages(request: SearchImagesRequest): SearchImagesResponse {
        return handleRequest {
            val userId = Context.current().userId
            val query = request.pQuery
            val galleryId = request.pGalleryUuid
            val parameters = request.pParametersMap.mapKeys { Parameter.valueOf(it.key) }
            val count = request.pCount

            require(count > 0) { "count must be greater than 0" }

            runBlocking {
                withContext(Dispatchers.IO) {
                    val images = SearchImages(
                        userId,
                        query,
                        UUID.fromString(galleryId),
                        parameters,
                        count,
                        Config.connectionProvider,
                        Config.cloudberry
                    ).execute()

                    val imagesResponse = images.map {
                        metricImage {
                            pImage = image {
                                pImageId = it.image.id.toString()
                                pGalleryUuid = it.image.galleryId.toString()
                                pDescription = it.image.description
                            }
                            pMetrics.putAll(it.metrics)
                        }
                    }
                    searchImagesResponse {
                        pContent.addAll(imagesResponse)
                    }
                }
            }
        }
    }


    private fun <T> handleRequest(action: () -> T): T {
        return try {
            action()
        } catch (e: IllegalArgumentException) {
            logger.error(e) { "${e.message}" }
            throw Status.INVALID_ARGUMENT.withDescription(e.message).asRuntimeException()
        } catch (e: ValidationException) {
            logger.warn(e) { "${e.message}" }
            throw Status.INVALID_ARGUMENT.withDescription(e.message).asRuntimeException()
        } catch (e: MetadataNotFoundException) {
            logger.warn(e) { "${e.message}" }
            throw Status.INVALID_ARGUMENT.withDescription(e.message).asRuntimeException()
        } catch (e: EntityNotFoundException) {
            logger.warn(e) { "${e.message}" }
            throw Status.NOT_FOUND.withDescription(e.message).asRuntimeException()
        } catch (e: InvalidExtensionException) {
            logger.warn(e) { "${e.message}" }
            throw Status.INVALID_ARGUMENT.withDescription(e.message).asRuntimeException()
        } catch (e: EntityAlreadyExistsException) {
            logger.warn { "${e.message}" }
            throw Status.ALREADY_EXISTS.withDescription(e.message).asRuntimeException()
        } catch (exception: Throwable) {
            logger.error(exception) { "Unhandled exception" }
            throw Status.INTERNAL.withDescription("Internal server error").asRuntimeException()
        }
    }
}
