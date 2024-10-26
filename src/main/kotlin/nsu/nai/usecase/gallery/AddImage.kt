@file:OptIn(ExperimentalUuidApi::class)

package nsu.nai.usecase.gallery

import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.grpc.Status
import io.grpc.StatusRuntimeException
import nsu.client.CloudberryStorageClient
import nsu.nai.core.table.gallery.Galleries
import nsu.nai.core.table.image.Image
import nsu.nai.core.table.image.ImageExtension
import nsu.nai.core.table.image.Images
import nsu.nai.exception.EntityNotFoundException
import nsu.nai.exception.ImageUploadException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

class AddImage(
    private val userId: Long,
    private val galleryIdentifier: Uuid,
    private val imageDescription: String,
    private val imageExtension: ImageExtension,
    private val imageContent: ByteArray,
    //
    private val getNewConnection: () -> Connection,
    private val cloudberry: CloudberryStorageClient
) {
    private val logger = logger {}

    fun execute(): Image {
        Database.connect(getNewConnection)

        val newImageId = transaction {
            addLogger(StdOutSqlLogger)

            requireGalleryExist(userId, galleryIdentifier)

            Images.insert {
                it[galleryUuid] = galleryIdentifier.toJavaUuid()
                it[description] = imageDescription
                it[content] = ExposedBlob(imageContent)
            } get Images.id
        }

        //TODO Надо тренироваться
        try {
//            val response = cloudberry.putEntry(
//                contentUuid = newImageId.toKotlinUuid(),
//                bucketUuid = galleryIdentifier,
//                extension = imageExtension,
//                description = imageDescription,
//                content = imageContent,
//            )
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

    private fun requireGalleryExist(userId: Long, galleryIdentifier: Uuid) {
        val exist = Galleries.selectAll()
            .where { (Galleries.userId eq userId) and (Galleries.id eq galleryIdentifier.toJavaUuid()) }
            .any()
        if (!exist) {
            throw EntityNotFoundException("gallery")
        }
    }
}
