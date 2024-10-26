package nsu.nai.usecase.gallery

import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.runBlocking
import nsu.client.CloudberryStorageClient
import nsu.nai.core.table.gallery.Galleries
import nsu.nai.core.table.image.Images
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@OptIn(ExperimentalUuidApi::class)
class RemoveGallery(
    private val userId: Long,
    private val galleryIdentifier: Uuid,
    //
    private val getNewConnection: () -> Connection,
    private val cloudberry: CloudberryStorageClient
) {
    private val logger = logger {}

    fun execute() {
        Database.connect(getNewConnection)

        transaction {
            addLogger(StdOutSqlLogger)

            require(isGalleryExist(userId, galleryIdentifier)) {
                "No gallery results found"
            }

            Images.deleteWhere { galleryUuid eq galleryIdentifier.toJavaUuid() }
            Galleries.deleteWhere { id eq galleryIdentifier.toJavaUuid() }
        }

        try {
            runBlocking {
                //TODO Миха обязательно справится
//            val response = cloudberry.destroyBucket(galleryIdentifier)
            }
        } catch (e: StatusRuntimeException) {
            logger.error { "gallery removal failed with status ${e.status}, with message ${e.message}" }
            throw e
        }

        // TODO(e.shelbogashev): разобраться, как в grpc котлин обрабатывать ошибки (по-идее, putEntry должен выбросить throwable, но я хз)
//        if (!response.success) {
//            logger.error { "bucket destroy failed with message ${response.statusMessage}" }
//        }
    }

    private fun isGalleryExist(userId: Long, galleryIdentifier: Uuid): Boolean {
        return try {
            Galleries.selectAll()
                .where { (Galleries.userId eq userId) and (Galleries.id eq galleryIdentifier.toJavaUuid()) }
                .single()
            true
        } catch (e: Exception) {
            false
        }
    }
}