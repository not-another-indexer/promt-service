@file:OptIn(ExperimentalUuidApi::class)

package nsu.nai.usecase.gallery

import io.github.oshai.kotlinlogging.KotlinLogging.logger
import nsu.client.CloudberryStorageClient
import nsu.nai.core.table.gallery.Galleries
import nsu.nai.core.table.gallery.Gallery
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

class CreateGallery(
    private val userIdentifier: Long,
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
                it[name] = galleryName
                it[userId] = userIdentifier
            } get Galleries.id
        }

        val response = cloudberry.initBucket(newGalleryId.toKotlinUuid())
        if (!response.success) {
            logger.error { "bucket init failed with message ${response.statusMessage}" }
            throw IllegalStateException()
        }

        return Gallery(newGalleryId.toKotlinUuid(), galleryName)
    }
}