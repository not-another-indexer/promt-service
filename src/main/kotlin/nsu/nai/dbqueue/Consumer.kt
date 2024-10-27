package nsu.nai.dbqueue

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import nsu.Config
import nsu.nai.core.table.gallery.Galleries
import nsu.nai.core.table.image.Image
import nsu.nai.core.table.image.ImageEntity.Companion.toImageEntity
import nsu.nai.core.table.image.Images
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import ru.yoomoney.tech.dbqueue.api.QueueConsumer
import ru.yoomoney.tech.dbqueue.api.Task
import ru.yoomoney.tech.dbqueue.api.TaskExecutionResult
import ru.yoomoney.tech.dbqueue.api.TaskPayloadTransformer
import ru.yoomoney.tech.dbqueue.settings.QueueConfig
import java.util.*

class PutEntryConsumer(private val config: QueueConfig) : QueueConsumer<PutEntryPayload> {
    private val logger = KotlinLogging.logger { }

    override fun execute(task: Task<PutEntryPayload>): TaskExecutionResult {
        val uuid = UUID.fromString(task.payload.get().imageUUID)
        Database.connect(Config.connectionProvider)
        val (entity, blob) = transaction {
            val row = Images.selectAll().where { Images.id eq uuid }.single()
            row.toImageEntity() to row[Images.content]
        }
        try {
            runBlocking {
                Config.cloudberry.putEntry(
                    contentUUID = entity.id,
                    bucketUUID = entity.galleryUUID,
                    extension = entity.extension,
                    description = entity.description,
                    content = blob.bytes
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "PutEntryConsumer error" }
            return TaskExecutionResult.fail()
        }

        Database.connect(Config.connectionProvider)
        transaction {
            Images.update(
                where = { Images.id eq uuid }
            ) { it[status] = Image.Status.ACTIVE }
        }

        return TaskExecutionResult.finish()
    }

    override fun getQueueConfig(): QueueConfig = config

    override fun getPayloadTransformer(): TaskPayloadTransformer<PutEntryPayload> = PutEntryPayloadTransformer
}

class RemoveEntryConsumer(private val config: QueueConfig) : QueueConsumer<RemoveEntryPayload> {
    private val logger = KotlinLogging.logger { }

    override fun execute(task: Task<RemoveEntryPayload>): TaskExecutionResult {
        val uuid = UUID.fromString(task.payload.get().imageUUID)
        Database.connect(Config.connectionProvider)
        val bucketUuid = transaction { Images.selectAll().where { Images.id eq uuid }.single()[Images.galleryUUID] }
        try {
            runBlocking {
                Config.cloudberry.removeEntry(
                    contentUUID = uuid,
                    bucketUUID = bucketUuid,
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "RemoveEntryConsumer error" }
            return TaskExecutionResult.fail()
        }

        Database.connect(Config.connectionProvider)
        transaction { Images.deleteWhere { id eq uuid } }
        return TaskExecutionResult.finish()
    }

    override fun getQueueConfig(): QueueConfig = config

    override fun getPayloadTransformer(): TaskPayloadTransformer<RemoveEntryPayload> = RemoveEntryPayloadTransformer
}

class InitIndexConsumer(private val config: QueueConfig) : QueueConsumer<InitIndexPayload> {
    private val logger = KotlinLogging.logger { }
    override fun execute(task: Task<InitIndexPayload>): TaskExecutionResult {
        try {
            runBlocking {
                Config.cloudberry.initBucket(UUID.fromString(task.payload.get().galleryUUID))
            }
        } catch (e: Exception) {
            logger.error(e) { "InitIndexConsumer error" }
            return TaskExecutionResult.fail()
        }
        return TaskExecutionResult.finish()
    }

    override fun getQueueConfig(): QueueConfig = config

    override fun getPayloadTransformer(): TaskPayloadTransformer<InitIndexPayload> = InitIndexPayloadTransformer
}

class DestroyIndexConsumer(private val config: QueueConfig) : QueueConsumer<DestroyIndexPayload> {
    private val logger = KotlinLogging.logger { }
    override fun execute(task: Task<DestroyIndexPayload>): TaskExecutionResult {
        try {
            val uuid = UUID.fromString(task.payload.get().galleryUUID)
            runBlocking {
                Config.cloudberry.destroyBucket(uuid)
            }
            Database.connect(Config.connectionProvider)
            transaction {
                Images.deleteWhere { galleryUUID eq uuid }
                Galleries.deleteWhere { id eq uuid }
            }
        } catch (e: Exception) {
            logger.error(e) { "DestroyIndexConsumer error" }
            return TaskExecutionResult.fail()
        }
        return TaskExecutionResult.finish()
    }

    override fun getQueueConfig(): QueueConfig = config

    override fun getPayloadTransformer(): TaskPayloadTransformer<DestroyIndexPayload> = DestroyIndexPayloadTransformer
}