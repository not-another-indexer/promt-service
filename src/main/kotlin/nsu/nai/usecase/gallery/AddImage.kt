@file:OptIn(ExperimentalUuidApi::class)

package nsu.nai.usecase.gallery

import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.grpc.Context
import io.grpc.Status
import io.grpc.StatusRuntimeException
import nsu.client.CloudberryStorageClient
import nsu.nai.core.table.image.Image
import nsu.nai.core.table.image.Images
import nsu.nai.interceptor.AuthInterceptor
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.InputStream
import java.sql.Connection
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

class AddImage(
    private val userId: Long,
    private val galleryIdentifier: Uuid,
    private val imageDescription: String,
    private val imageExtension: String,
    private val imageContent: InputStream,
    //
    private val getNewConnection: () -> Connection,
    private val cloudberry: CloudberryStorageClient
) {
    private val logger = logger {}

    suspend fun execute(): Image {
        println(userId)
        val user = AuthInterceptor.USER_CONTEXT_KEY.get(Context.current())

        Database.connect(getNewConnection)

        val newImageId = transaction {
            addLogger(StdOutSqlLogger)

            Images.insert {
                it[galleryUuid] = galleryIdentifier.toJavaUuid()
                it[description] = imageDescription
                it[content] = ExposedBlob(imageContent)
            } get Images.id
        }

        try {
            val response = cloudberry.putEntry(
                contentUuid = newImageId.toKotlinUuid(),
                bucketUuid = galleryIdentifier,
                extension = imageExtension,
                description = imageDescription,
                content = imageContent,
            )
            // Обработка успешного ответа
            logger.info { "successfully uploaded image with id $newImageId" }
        } catch (e: StatusRuntimeException) {
            logger.error { "gRPC call failed: ${e.status}, message: ${e.message}" }

            // Обрабатываем различные статусы
            when (e.status.code) {
                Status.Code.NOT_FOUND -> {
                    logger.error { "Bucket or content not found" }
                    throw RuntimeException("Bucket or content not found")
                }
                Status.Code.PERMISSION_DENIED -> {
                    logger.error { "Permission denied" }
                    throw ImageUploadException("Permission denied")
                }
                Status.Code.UNAVAILABLE -> {
                    logger.error { "Service unavailable" }
                    throw ImageUploadException("Service is temporarily unavailable, please try again later")
                }
                else -> {
                    logger.error { "Unknown error occurred: ${e.status}" }
                    throw ImageUploadException("An unknown error occurred")
                }
            }
        }

        return Image(
            id = newImageId.toKotlinUuid(),
            galleryId = galleryIdentifier,
            description = imageDescription
        )
    }

    public class ImageUploadException(message: String) : Exception(message)
}