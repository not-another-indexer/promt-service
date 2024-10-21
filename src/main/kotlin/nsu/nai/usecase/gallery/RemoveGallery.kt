@file:OptIn(ExperimentalUuidApi::class)

package nsu.nai.usecase.gallery

import io.github.oshai.kotlinlogging.KotlinLogging.logger
import nsu.client.CloudberryStorageClient
import nsu.nai.core.table.gallery.Galleries
import nsu.nai.core.table.image.Images
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

class RemoveGallery(
    private val userId: Long,
    private val galleryIdentifier: Uuid,
    //
    private val getNewConnection: () -> Connection,
    private val cloudberry: CloudberryStorageClient
) {
    private val logger = logger {}

    suspend fun execute() {
        println(userId)
        Database.connect(getNewConnection)

        transaction {
            addLogger(StdOutSqlLogger)

            Images.deleteWhere { galleryUuid eq galleryIdentifier.toJavaUuid() }
            Galleries.deleteWhere { id eq galleryIdentifier.toJavaUuid() }
        }

        val response = cloudberry.destroyBucket(galleryIdentifier)
        // TODO(e.shelbogashev): разобраться, как в grpc котлин обрабатывать ошибки (по-идее, putEntry должен выбросить throwable, но я хз)
//        if (!response.success) {
//            logger.error { "bucket destroy failed with message ${response.statusMessage}" }
//        }
    }
}