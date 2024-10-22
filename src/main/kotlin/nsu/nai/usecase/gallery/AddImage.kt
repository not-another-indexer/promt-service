@file:OptIn(ExperimentalUuidApi::class)

package nsu.nai.usecase.gallery

import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.grpc.Context
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
        } catch (e: StatusRuntimeException) {
            logger.error { "image adding failed with status ${e.status}, with message ${e.message}" }
            throw e
        }

        // TODO(e.shelbogashev): разобраться, как в grpc котлин обрабатывать ошибки (по-идее, putEntry должен выбросить throwable, но я хз)
//        if (!response.success) {
//            logger.error { "image addition failed with message ${response.statusMessage}" }
//        }

        return Image(
            id = newImageId.toKotlinUuid(),
            galleryId = galleryIdentifier,
            description = imageDescription
        )
    }
}