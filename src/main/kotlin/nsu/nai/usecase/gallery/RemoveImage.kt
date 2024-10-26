@file:OptIn(ExperimentalUuidApi::class)

package nsu.nai.usecase.gallery

import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.grpc.StatusRuntimeException
import nsu.client.CloudberryStorageClient
import nsu.nai.core.table.gallery.Galleries
import nsu.nai.core.table.image.Images
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@OptIn(ExperimentalUuidApi::class)
class RemoveImage(
    private val userId: Long,
    private val imageIdentifier: Uuid,
    //
    private val getNewConnection: () -> Connection,
    private val cloudberry: CloudberryStorageClient
) {
    private val logger = logger {}

    suspend fun execute() {
        Database.connect(getNewConnection)

        transaction {
            addLogger(StdOutSqlLogger)

            Images.innerJoin(Galleries).delete(Images) {
                (Galleries.userId eq userId) and (Images.id eq imageIdentifier.toJavaUuid())
            }
        }

        //TODO Коннект с Михой
        try {
//            val response = cloudberry.removeEntry(
//                contentUuid = imageIdentifier,
//                bucketUuid = galleryUuid.toKotlinUuid()
//            )
        } catch (e: StatusRuntimeException) {
            logger.error { "image removal failed with status ${e.status}, with message ${e.message}" }
            "image removal failed with status ${e.status}, with message ${e.message}" to false
        }
    }
}