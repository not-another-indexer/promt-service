@file:OptIn(ExperimentalUuidApi::class, DelicateCoroutinesApi::class)

package nsu

import AuthServiceGrpcKt
import NaiAuth
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.*
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.*
import nai.GalleryServiceGrpc
import nai.MainServiceGrpc
import nai.Nai
import nai.Nai.ContentMetadata
import nsu.client.CloudberryStorageClient
import nsu.nai.core.Parameter
import nsu.nai.core.table.gallery.Galleries
import nsu.nai.core.table.image.Image
import nsu.nai.core.table.image.Images
import nsu.nai.core.table.user.Users
import nsu.nai.interceptor.AuthInterceptor
import nsu.nai.usecase.auth.*
import nsu.nai.usecase.gallery.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.sql.Connection
import java.sql.DriverManager
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun main() = runBlocking {
    val authInterceptor = AuthInterceptor();

    val server: Server = ServerBuilder.forPort(8080)
        .addService(AuthServiceImpl())
        .addService(ServerInterceptors.intercept(GalleryServiceImpl(), authInterceptor))
        .addService(ServerInterceptors.intercept(MainServiceImpl(), authInterceptor))
        .build()
        .start()

    Database.connect(Config.connectionProvider)
    transaction {
        SchemaUtils.create(Users, Galleries, Images)
    }

    println("Server started on port ${server.port}")

    server.awaitTermination()
}

class AuthServiceImpl : AuthServiceGrpcKt.AuthServiceCoroutineImplBase() {
    override suspend fun register(request: NaiAuth.RegisterRequest): NaiAuth.RegisterResponse {
        val (message, success) = RegisterUser(
            request.username,
            request.displayName,
            request.rawPassword,
            Config.connectionProvider
        ).execute()

        return NaiAuth.RegisterResponse.newBuilder()
            .setStatusMessage(message)
            .setSuccess(success)
            .build()
    }

    override suspend fun signIn(request: NaiAuth.SignInRequest): NaiAuth.SignInResponse {
        try {
            val (user, tokens) = LoginUser(
                request.username,
                request.rawPassword,
                Config.connectionProvider
            ).execute()

            return NaiAuth.SignInResponse.newBuilder()
                .setUsername(request.username)
                .setDisplayName(user.displayName)
                .setAccessToken(tokens.first)
                .setRefreshToken(tokens.second)
                .build()
        } catch (exception: UserNotFoundException) {
            throw Status.NOT_FOUND.withDescription("user not found").asRuntimeException()
        } catch (exception: BadCredentials) {
            throw Status.UNAUTHENTICATED.withDescription("bad credentials").asRuntimeException()
        } catch (exception: Throwable) {
            throw Status.INTERNAL.withDescription("internal server error").asRuntimeException()
        }
    }

    override suspend fun refreshToken(request: NaiAuth.RefreshTokenRequest): NaiAuth.RefreshTokenResponse {
        val refreshToken = RefreshToken(
            request.refreshToken,
            Config.connectionProvider
        ).execute() ?: throw Status.UNAUTHENTICATED.withDescription("Invalid or expired token").asRuntimeException()

        return NaiAuth.RefreshTokenResponse.newBuilder()
            .setAccessToken(refreshToken)
            .build()
    }
}

class GalleryServiceImpl : GalleryServiceGrpc.GalleryServiceImplBase() {
    override fun getGalleryImages(
        request: Nai.GetGalleryImagesRequest,
        responseObserver: StreamObserver<Nai.GetGalleryImagesResponse>
    ) {
        try {
            val (images: List<Image>, total: Long) = GetGalleryImages(
                Uuid.parse(request.galleryId),
                request.size,
                request.offset.toLong(),
                Config.connectionProvider
            ).execute()

            val imageResponses = images.map { image ->
                Nai.Image.newBuilder()
                    .setImageId(image.id.toString())
                    .setGalleryId(image.galleryId.toString())
                    .setDescription(image.description)
                    .build()
            }

            val response = Nai.GetGalleryImagesResponse.newBuilder()
                .addAllContent(imageResponses)
                .setTotal(total)
                .build()

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

                val imageContent = ByteArrayInputStream(imageChunks.toByteArray())

                GlobalScope.launch {
                    try {
                        val image = AddImage(
                            galleryIdentifier = Uuid.parse(metadata!!.galleryId),
                            imageDescription = metadata!!.description,
                            imageExtension = metadata!!.extension,
                            imageContent = imageContent,
                            getNewConnection = Config.connectionProvider,
                            cloudberry = Config.cloudberry
                        ).execute()

                        val response = Nai.AddImageResponse.newBuilder()
                            .setImageId(image.id.toString())
                            .build()

                        responseObserver.onNext(response)
                        responseObserver.onCompleted()

                    } catch (e: Exception) {
                        logger.error(e) { "Error while processing image" }
                        responseObserver.onError(Status.INTERNAL.withDescription(e.message).asRuntimeException())
                    }
                }
            }
        }
    }

    override fun deleteImage(
        request: Nai.DeleteImageRequest,
        responseObserver: StreamObserver<Nai.DeleteImageResponse>
    ) {
        GlobalScope.launch {
            try {
                val (message, success) = RemoveImage(
                    Uuid.parse(request.imageId),
                    Config.connectionProvider,  // DB connection provider
                    Config.cloudberry           // CloudberryStorageClient
                ).execute()

                val response = Nai.DeleteImageResponse.newBuilder()
                    .setStatusMessage(message)
                    .setSuccess(success)
                    .build()

                responseObserver.onNext(response)
                responseObserver.onCompleted()

            } catch (e: Exception) {
                val errorMessage = "Failed to delete image: ${e.message}"
                KotlinLogging.logger {}.error(e) { errorMessage }

                val errorResponse = Nai.DeleteImageResponse.newBuilder()
                    .setStatusMessage(errorMessage)
                    .setSuccess(false)
                    .build()

                responseObserver.onNext(errorResponse)
                responseObserver.onCompleted()
            }
        }
    }

    override fun getImageContent(
        request: Nai.GetImageContentRequest?,
        responseObserver: StreamObserver<Nai.GetImageContentResponse?>?
    ) {
        if (request == null) {
            responseObserver?.onError(IllegalArgumentException("Request cannot be null"))
            return
        }

        val imageId = request.imageId
        if (imageId.isEmpty()) {
            responseObserver?.onError(IllegalArgumentException("Image ID cannot be empty"))
            return
        }

        val imageIdentifier = Uuid.parse(imageId)

        GlobalScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val getImageContent = GetImageContent(imageIdentifier, Config.connectionProvider)
                    val inputStream = getImageContent.execute()

                    val response = Nai.GetImageContentResponse.newBuilder()
                        .setChunkData(com.google.protobuf.ByteString.readFrom(inputStream))
                        .build()

                    responseObserver?.onNext(response)
                    responseObserver?.onCompleted()
                } catch (e: Exception) {
                    responseObserver?.onError(e)
                }
            }
        }
    }

    override fun searchImage(
        request: Nai.SearchImageRequest?,
        responseObserver: StreamObserver<Nai.SearchImageResponse?>?
    ) {
        if (request == null) {
            responseObserver?.onError(IllegalArgumentException("Request cannot be null"))
            return
        }

        val query = request.query
        val galleryId = request.galleryId
        val parameters = request.parametersMap.mapKeys { Parameter.valueOf(it.key) }
        val count = request.count

        GlobalScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val searchImages = SearchImages(
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
                        val imageResponse = Nai.Image.newBuilder()
                            .setImageId(image.id.toString())
                            .setGalleryId(image.galleryId.toString())
                            .setDescription(image.description)
                            .build()
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

class MainServiceImpl : MainServiceGrpc.MainServiceImplBase() {
    override fun createGallery(
        request: Nai.CreateGalleryRequest?,
        responseObserver: StreamObserver<Nai.CreateGalleryResponse?>?
    ) {
        super.createGallery(request, responseObserver)
    }

    override fun getGalleries(
        request: Nai.GetGalleriesRequest?,
        responseObserver: StreamObserver<Nai.GetGalleriesResponse?>?
    ) {
        super.getGalleries(request, responseObserver)
    }

    override fun deleteGallery(
        request: Nai.DeleteGalleryRequest?,
        responseObserver: StreamObserver<Nai.DeleteGalleryResponse?>?
    ) {
        super.deleteGallery(request, responseObserver)
    }
}

object Config {
    val connectionProvider: () -> Connection = {
        DriverManager.getConnection(
            "jdbc:postgresql://localhost:5432/nai_db",
            "nai_user",
            "nai_password"
        )
    }

    private val managedChannel: ManagedChannel = ManagedChannelBuilder
        .forAddress("localhost", 50051)
        .usePlaintext() // unsecured communication
        .build()

    val cloudberry = CloudberryStorageClient(managedChannel)
}