@file:OptIn(ExperimentalUuidApi::class, ExperimentalUuidApi::class)

package nsu.nai.usecase.gallery

import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.grpc.Context
import io.grpc.StatusRuntimeException
import nsu.client.CloudberryStorageClient
import nsu.nai.core.table.gallery.Galleries
import nsu.nai.core.table.gallery.Gallery
import nsu.nai.interceptor.AuthInterceptor
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

class CreateGallery(
    private val userId: Long,
    private val galleryName: String,
    //
    private val getNewConnection: () -> Connection,
    private val cloudberry: CloudberryStorageClient
) {
    private val logger = logger {}

    suspend fun execute(): Gallery {
        Database.connect(getNewConnection)

        val newGalleryId = transaction {
            addLogger(StdOutSqlLogger)

            Galleries.insert {
                it[Galleries.name] = galleryName
                it[Galleries.userId] = userId
            } get Galleries.id
        }

        try {
            val response = cloudberry.initBucket(newGalleryId.toKotlinUuid())
        } catch (e: StatusRuntimeException) {
            logger.error { "gallery creation failed with status ${e.status}, with message ${e.message}" }
            throw e
        }

        return Gallery(newGalleryId.toKotlinUuid(), galleryName)
    }
}
