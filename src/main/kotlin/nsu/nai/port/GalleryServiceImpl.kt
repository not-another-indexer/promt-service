@file:OptIn(ExperimentalUuidApi::class, DelicateCoroutinesApi::class)

package nsu.nai.port

import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.Context
import io.grpc.Status
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.*
import nai.GalleryServiceGrpc
import nai.Nai
import nai.Nai.ContentMetadata
import nsu.Config
import nsu.nai.core.Parameter
import nsu.nai.core.table.image.Image
import nsu.nai.usecase.gallery.*
import nsu.platform.userId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class GalleryServiceImpl : GalleryServiceGrpc.GalleryServiceImplBase() {
    override fun getGalleryImages(
        request: Nai.GetGalleryImagesRequest, responseObserver: StreamObserver<Nai.GetGalleryImagesResponse>
    ) {
        try {
            val userId = Context.current().userId
            val (images: List<Image>, total: Long) = GetGalleryImages(
                userId, Uuid.parse(request.galleryId), request.size, request.offset.toLong(), Config.connectionProvider
            ).execute()

            val imageResponses = images.map { image ->
                Nai.Image.newBuilder().setImageId(image.id.toString()).setGalleryId(image.galleryId.toString())
                    .setDescription(image.description).build()
            }

            val response =
                Nai.GetGalleryImagesResponse.newBuilder().addAllContent(imageResponses).setTotal(total).build()

            responseObserver.onNext(response)
            responseObserver.onCompleted()
        } catch (e: Exception) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.message).asRuntimeException())
        }
    }

    override fun addImage(responseObserver: StreamObserver<Nai.AddImageResponse>): StreamObserver<Nai.AddImageRequest> {
        return object : StreamObserver<Nai.AddImageRequest> {
            private var metadata: ContentMetadata? = null
            private val imageChunks = ByteArrayOutputStream()
            private val logger = KotlinLogging.logger {}

            override fun onNext(request: Nai.AddImageRequest) {
                when (request.payloadCase) {
                    Nai.AddImageRequest.PayloadCase.METADATA -> {
                        metadata = request.metadata
                    }

                    Nai.AddImageRequest.PayloadCase.CHUNK_DATA -> {
                        request.chunkData.writeTo(imageChunks)
                    }

                    else -> {
                        responseObserver.onError(
                            Status.INVALID_ARGUMENT.withDescription("Invalid payload").asRuntimeException()
                        )
                    }
                }
            }

            override fun onError(t: Throwable) {
                logger.error(t) { "Error during image upload: ${t.message}" }
                responseObserver.onError(t)
            }

            override fun onCompleted() {
                if (metadata == null) {
                    responseObserver.onError(
                        Status.INVALID_ARGUMENT.withDescription("Metadata is missing").asRuntimeException()
                    )
                    return
                }
                val userId = Context.current().userId
                val imageContent = ByteArrayInputStream(imageChunks.toByteArray())

                GlobalScope.launch {
                    try {
                        val image = AddImage(
                            userId,
                            galleryIdentifier = Uuid.parse(metadata!!.galleryId),
                            imageDescription = metadata!!.description,
                            imageExtension = metadata!!.extension,
                            imageContent = imageContent,
                            getNewConnection = Config.connectionProvider,
                            cloudberry = Config.cloudberry
                        ).execute()

                        val response = Nai.AddImageResponse.newBuilder().setImageId(image.id.toString()).build()

                        responseObserver.onNext(response)
                        responseObserver.onCompleted()

                    } catch (e: Exception) {
                        logger.error(e) { "Error while processing image: ${e.message}" }
                        responseObserver.onError(Status.INTERNAL.withDescription(e.message).asRuntimeException())
                    }
                }
            }
        }
    }

    override fun deleteImage(
        request: Nai.DeleteImageRequest, responseObserver: StreamObserver<Nai.DeleteImageResponse>
    ) {
        GlobalScope.launch {
            try {
                val userId = Context.current().userId

                val (message, success) = RemoveImage(
                    userId, Uuid.parse(request.imageId), Config.connectionProvider,  // DB connection provider
                    Config.cloudberry           // CloudberryStorageClient
                ).execute()

                val response =
                    Nai.DeleteImageResponse.newBuilder().setStatusMessage(message).setSuccess(success).build()

                responseObserver.onNext(response)
                responseObserver.onCompleted()

            } catch (e: Exception) {
                val errorMessage = "Failed to delete image: ${e.message}"
                KotlinLogging.logger {}.error(e) { errorMessage }

                val errorResponse =
                    Nai.DeleteImageResponse.newBuilder().setStatusMessage(errorMessage).setSuccess(false).build()

                responseObserver.onNext(errorResponse)
                responseObserver.onCompleted()
            }
        }
    }

    override fun getImageContent(
        request: Nai.GetImageContentRequest?, responseObserver: StreamObserver<Nai.GetImageContentResponse?>?
    ) {
        if (request == null) {
            responseObserver?.onError(IllegalArgumentException("Request cannot be null"))
            return
        }

        val imageId = request.imageId
        val userId = Context.current().userId

        if (imageId.isEmpty()) {
            responseObserver?.onError(IllegalArgumentException("Image ID cannot be empty"))
            return
        }

        val imageIdentifier = Uuid.parse(imageId)

        GlobalScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val getImageContent = GetImageContent(userId, imageIdentifier, Config.connectionProvider)
                    val inputStream = getImageContent.execute()

                    val response = Nai.GetImageContentResponse.newBuilder()
                        .setChunkData(com.google.protobuf.ByteString.readFrom(inputStream)).build()

                    responseObserver?.onNext(response)
                    responseObserver?.onCompleted()
                } catch (e: Exception) {
                    responseObserver?.onError(e)
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun searchImage(
        request: Nai.SearchImageRequest?, responseObserver: StreamObserver<Nai.SearchImageResponse?>?
    ) {
        if (request == null) {
            responseObserver?.onError(IllegalArgumentException("Request cannot be null"))
            return
        }

        val userId = Context.current().userId
        val query = request.query
        val galleryId = request.galleryId
        val parameters = request.parametersMap.mapKeys { Parameter.valueOf(it.key) }
        val count = request.count

        GlobalScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val searchImages = SearchImages(
                        userId,
                        query,
                        Uuid.parse(galleryId),
                        parameters,
                        count,
                        Config.connectionProvider,
                        Config.cloudberry
                    )
                    val images = searchImages.execute()

                    val responseBuilder = Nai.SearchImageResponse.newBuilder()
                    images.forEach { image ->
                        val imageResponse = Nai.Image.newBuilder().setImageId(image.id.toString())
                            .setGalleryId(image.galleryId.toString()).setDescription(image.description).build()
                        responseBuilder.addContent(imageResponse)
                    }

                    responseObserver?.onNext(responseBuilder.build())
                    responseObserver?.onCompleted()
                } catch (e: Exception) {
                    responseObserver?.onError(e)
                }
            }
        }
    }
}