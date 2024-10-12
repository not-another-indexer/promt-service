@file:OptIn(ExperimentalUuidApi::class)

package nsu.nai.usecase

import io.github.oshai.kotlinlogging.KotlinLogging.logger
import nsu.client.CloudberryStorageClient
import nsu.nai.core.table.image.Images
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.deleteReturning
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

class RemoveImage(
    private val imageIdentifier: Uuid,
    //
    private val getNewConnection: () -> Connection,
    private val cloudberry: CloudberryStorageClient
) {
    private val logger = logger {}

    suspend fun execute() {
        Database.connect(getNewConnection)

        val galleryUuid = transaction {
            addLogger(StdOutSqlLogger)

            Images.deleteReturning { Images.id eq imageIdentifier.toJavaUuid() }.single()[Images.galleryUuid]
        }

        val response = cloudberry.removeEntry(
            contentUuid = imageIdentifier,
            bucketUuid = galleryUuid.toKotlinUuid()
        )

        if (!response.success) {
            logger.error { "image removal failed with message ${response.statusMessage}" }
        }
    }
}