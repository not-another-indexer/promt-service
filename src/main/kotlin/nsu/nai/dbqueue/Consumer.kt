package nsu.nai.dbqueue

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import nsu.Config
import nsu.nai.core.table.gallery.Galleries
import nsu.nai.core.table.gallery.GalleryEntity
import nsu.nai.core.table.image.ImageEntity
import nsu.nai.core.table.image.ImageEntity.Companion.toImageEntity
import nsu.nai.core.table.image.Images
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import ru.yoomoney.tech.dbqueue.api.QueueConsumer
import ru.yoomoney.tech.dbqueue.api.Task
import ru.yoomoney.tech.dbqueue.api.TaskExecutionResult
import ru.yoomoney.tech.dbqueue.settings.QueueConfig
import java.util.*

abstract class BaseConsumer<T>(private val config: QueueConfig) : QueueConsumer<T> {
    protected val logger = KotlinLogging.logger { }
    override fun getQueueConfig() = config
}

class PutEntryConsumer(config: QueueConfig) : BaseConsumer<PutEntryPayload>(config) {
    override fun execute(task: Task<PutEntryPayload>): TaskExecutionResult {
        val uuid = UUID.fromString(task.payload.get().imageUUID)
        return runTransaction(uuid) { entity, blob ->
            Config.cloudberry.putEntry(
                contentUUID = entity.id,
                bucketUUID = entity.galleryUUID,
                extension = entity.extension,
                description = entity.description,
                content = blob.bytes
            )
            Images.update({ Images.id eq uuid }) { it[status] = ImageEntity.Status.ACTIVE }
        }
    }

    private fun runTransaction(uuid: UUID, action: suspend (ImageEntity, ExposedBlob) -> Unit): TaskExecutionResult {
        Database.connect(Config.connectionProvider)
        return transaction {
            try {
                val (entity, blob) = Images.selectAll()
                    .where { Images.id eq uuid }
                    .single().let { it.toImageEntity() to it[Images.content] }
                runBlocking { action(entity, blob) }
                TaskExecutionResult.finish()
            } catch (e: Exception) {
                logger.error(e) { "PutEntryConsumer error" }
                TaskExecutionResult.fail()
            }
        }
    }

    override fun getPayloadTransformer() = PutEntryPayloadTransformer
}

class RemoveEntryConsumer(config: QueueConfig) : BaseConsumer<RemoveEntryPayload>(config) {
    override fun execute(task: Task<RemoveEntryPayload>): TaskExecutionResult {
        val uuid = UUID.fromString(task.payload.get().imageUUID)
        return runTransaction(uuid) { bucketUuid ->
            Config.cloudberry.removeEntry(uuid, bucketUuid)
            Images.deleteWhere { Images.id eq uuid }
        }
    }

    private fun runTransaction(uuid: UUID, action: suspend (UUID) -> Unit): TaskExecutionResult {
        Database.connect(Config.connectionProvider)
        return transaction {
            try {
                val bucketUuid = Images.selectAll()
                    .where { Images.id eq uuid }
                    .single()[Images.galleryUUID]
                runBlocking { action(bucketUuid) }
                TaskExecutionResult.finish()
            } catch (e: Exception) {
                logger.error(e) { "RemoveEntryConsumer error" }
                TaskExecutionResult.fail()
            }
        }
    }

    override fun getPayloadTransformer() = RemoveEntryPayloadTransformer
}

class InitIndexConsumer(config: QueueConfig) : BaseConsumer<InitIndexPayload>(config) {
    override fun execute(task: Task<InitIndexPayload>): TaskExecutionResult {
        val bucketUUID = UUID.fromString(task.payload.get().galleryUUID)
        return runTransaction {
            Config.cloudberry.initBucket(bucketUUID)
            Galleries.update({ Galleries.id eq bucketUUID }) { it[status] = GalleryEntity.Status.ACTIVE }
        }
    }

    private fun runTransaction(action: suspend () -> Unit): TaskExecutionResult {
        Database.connect(Config.connectionProvider)
        return transaction {
            try {
                runBlocking { action() }
                TaskExecutionResult.finish()
            } catch (e: Exception) {
                logger.error(e) { "InitIndexConsumer error" }
                TaskExecutionResult.fail()
            }
        }
    }

    override fun getPayloadTransformer() = InitIndexPayloadTransformer
}

class DestroyIndexConsumer(config: QueueConfig) : BaseConsumer<DestroyIndexPayload>(config) {
    override fun execute(task: Task<DestroyIndexPayload>): TaskExecutionResult {
        val bucketUUID = UUID.fromString(task.payload.get().galleryUUID)
        return runTransaction {
            Config.cloudberry.destroyBucket(bucketUUID)
            Images.deleteWhere { galleryUUID eq bucketUUID }
            Galleries.deleteWhere { id eq bucketUUID }
        }
    }

    private fun runTransaction(action: suspend () -> Unit): TaskExecutionResult {
        Database.connect(Config.connectionProvider)
        return transaction {
            try {
                runBlocking { action() }
                TaskExecutionResult.finish()
            } catch (e: Exception) {
                logger.error(e) { "DestroyIndexConsumer error" }
                TaskExecutionResult.fail()
            }
        }
    }

    override fun getPayloadTransformer() = DestroyIndexPayloadTransformer
}