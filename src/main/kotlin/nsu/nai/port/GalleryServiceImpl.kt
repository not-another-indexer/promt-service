@file:OptIn(ExperimentalUuidApi::class, DelicateCoroutinesApi::class)

package nsu.nai.port

import com.google.protobuf.ByteString
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.Context
import io.grpc.Status
import kotlinx.coroutines.*
import nai.*
import nai.Nai.*
import nai.Nai.GetGalleryImagesRequest
import nsu.Config
import nsu.nai.core.Parameter
import nsu.nai.core.table.image.Image
import nsu.nai.core.table.image.ImageExtension
import nsu.nai.exception.EntityAlreadyExistsException
import nsu.nai.exception.EntityNotFoundException
import nsu.nai.exception.InvalidExtensionException
import nsu.nai.exception.MetadataNotFoundException
import nsu.nai.usecase.gallery.*
import nsu.platform.userId
import java.io.ByteArrayOutputStream
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class GalleryServiceImpl : GalleryServiceGrpcKt.GalleryServiceCoroutineImplBase() {

    val logger = KotlinLogging.logger {}

    override suspend fun getGalleryImages(request: GetGalleryImagesRequest): GetGalleryImagesResponse {
        return handleRequest {
            require((request.pSize > 0) and (request.pSize < 1000)) { "size must be greater than 0 and less than 1000" }
            require(request.pOffset > 0) { "offset must be greater than 0" }

            val userId = Context.current().userId
            val (images: List<Image>, totalSize: Long) = GetGalleryImages(
                userId,
                Uuid.parse(request.pGalleryId),
                request.pSize,
                request.pOffset.toLong(),
                Config.connectionProvider
            ).execute()

            val imageResponses = images.map { image ->
                image {
                    pImageId = image.id.toString()
                    pGalleryId = image.galleryId.toString()
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
                galleryIdentifier = Uuid.parse(metadata.pGalleryId),
                imageDescription = metadata.pDescription,
                imageExtension = imageExtension,
                imageContent = imageChunks.toByteArray(),
                getNewConnection = Config.connectionProvider,
                cloudberry = Config.cloudberry
            ).execute()

            addImageResponse {
                pImageId = image.id.toString()
            }
        }
    }

    override suspend fun deleteImage(request: DeleteImageRequest): Empty {
        return handleRequest {
            val userId = Context.current().userId
            runBlocking {
                RemoveImage(
                    userId, Uuid.parse(request.pImageId), Config.connectionProvider,
                    Config.cloudberry
                ).execute()

                empty { }
            }
        }
    }

    override suspend fun getImageContent(request: GetImageContentRequest): GetImageContentResponse {
        return handleRequest {
            val imageId = request.pImageId
            val userId = Context.current().userId

            val imageIdentifier = Uuid.parse(imageId)

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
            val galleryId = request.pGalleryId
            val parameters = request.pParametersMap.mapKeys { Parameter.valueOf(it.key) }
            val count = request.pCount

            require(count > 0) { "count must be greater than 0" }

            runBlocking {
                withContext(Dispatchers.IO) {
                    val images = SearchImages(
                        userId,
                        query,
                        Uuid.parse(galleryId),
                        parameters,
                        count,
                        Config.connectionProvider,
                        Config.cloudberry
                    ).execute()

                    val imagesResponse = images.map {
                        image {
                            pImageId = it.id.toString()
                            pGalleryId = it.galleryId.toString()
                            pDescription = it.description
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
        } catch (e: MetadataNotFoundException) {
            logger.error(e) { "${e.message}" }
            throw Status.INVALID_ARGUMENT.withDescription(e.message).asRuntimeException()
        } catch (e: EntityNotFoundException) {
            logger.error(e) { "${e.message}" }
            throw Status.NOT_FOUND.withDescription(e.message).asRuntimeException()
        } catch (e: InvalidExtensionException) {
            logger.error(e) { "${e.message}" }
            throw Status.INVALID_ARGUMENT.withDescription(e.message).asRuntimeException()
        } catch (e: EntityAlreadyExistsException) {
            logger.error { "${e.message}" }
            throw Status.ALREADY_EXISTS.withDescription(e.message).asRuntimeException()
        } catch (e: IllegalArgumentException) {
            logger.error { "${e.message}" }
            throw Status.INVALID_ARGUMENT.withDescription(e.message).asRuntimeException()
        } catch (exception: Throwable) {
            logger.error(exception) { "Unhandled exception" }
            throw Status.INTERNAL.withDescription("Internal server error").asRuntimeException()
        }
    }
}
