@file:OptIn(ExperimentalUuidApi::class)

package nsu.nai.usecase.gallery

import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.grpc.StatusRuntimeException
import nsu.client.CloudberryStorageClient
import nsu.nai.core.table.gallery.Galleries
import nsu.nai.core.table.gallery.Gallery
import nsu.nai.exception.EntityAlreadyExistsException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

@OptIn(ExperimentalUuidApi::class)
class CreateGallery(
    private val userId: Long,
    private val galleryName: String,
    //
    private val getNewConnection: () -> Connection,
    private val cloudberry: CloudberryStorageClient
) {
    private val logger = logger {}

    fun execute(): Gallery {
        Database.connect(getNewConnection)

        val newGalleryId = transaction {
            requireNotExist(userId, galleryName)

            Galleries.insert {
                it[name] = galleryName
                it[userId] = this@CreateGallery.userId
            } get Galleries.id
        }

        try {
//            val response = cloudberry.initBucket(newGalleryId.toKotlinUuid())
        } catch (e: StatusRuntimeException) {
            logger.error { "gallery creation failed with status ${e.status}, with message ${e.message}" }
            throw e
        }

        return Gallery(newGalleryId.toKotlinUuid(), galleryName)
    }

    private fun requireNotExist(userId: Long, galleryName: String) {
        val exist = Galleries.selectAll()
            .where { (Galleries.userId eq userId) and (Galleries.name eq galleryName) }
            .any()

        if (exist) {
            throw EntityAlreadyExistsException(galleryName)
        }
    }
}
