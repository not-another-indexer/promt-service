package nsu.nai.dbqueue

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import nsu.Config
import ru.yoomoney.tech.dbqueue.api.QueueConsumer
import ru.yoomoney.tech.dbqueue.api.Task
import ru.yoomoney.tech.dbqueue.api.TaskExecutionResult
import ru.yoomoney.tech.dbqueue.api.TaskPayloadTransformer
import ru.yoomoney.tech.dbqueue.settings.QueueConfig
import java.util.*

class PutEntryConsumer(private val config: QueueConfig) : QueueConsumer<PutEntryPayload> {
    override fun execute(task: Task<PutEntryPayload>): TaskExecutionResult {
        TODO("Not yet implemented")
    }

    override fun getQueueConfig(): QueueConfig = config

    override fun getPayloadTransformer(): TaskPayloadTransformer<PutEntryPayload> = PutEntryPayloadTransformer

    //  //TODO Надо тренироваться
    //        try {
    ////            val response = cloudberry.putEntry(
    ////                contentUUID = newImageId.toKotlinUUID(),
    ////                bucketUUID = galleryIdentifier,
    ////                extension = imageExtension,
    ////                description = imageDescription,
    ////                content = imageContent,
    ////            )
    //            // Обработка успешного ответа
    //            logger.info { "successfully uploaded image with id $newImageId" }
    //        } catch (e: StatusRuntimeException) {
    //            logger.error { "gRPC call failed: ${e.status}, message: ${e.message}" }
    //
    //            // Обрабатываем различные статусы
    //            when (e.status.code) {
    //                Status.Code.NOT_FOUND -> {
    //                    logger.error { "Bucket or content not found" }
    //                    throw RuntimeException("Bucket or content not found")
    //                }
    //
    //                Status.Code.PERMISSION_DENIED -> {
    //                    logger.error { "Permission denied" }
    //                    throw ImageUploadException("Permission denied")
    //                }
    //
    //                Status.Code.UNAVAILABLE -> {
    //                    logger.error { "Service unavailable" }
    //                    throw ImageUploadException("Service is temporarily unavailable, please try again later")
    //                }
    //
    //                else -> {
    //                    logger.error { "Unknown error occurred: ${e.status}" }
    //                    throw ImageUploadException("An unknown error occurred")
    //                }
    //            }
    //        }
}

class RemoveEntryConsumer(private val config: QueueConfig) : QueueConsumer<RemoveEntryPayload> {
    override fun execute(task: Task<RemoveEntryPayload>): TaskExecutionResult {
        TODO("Not yet implemented")
        // Images.innerJoin(Galleries).delete(Images) {
        //                (Galleries.userId eq userId) and (Images.id eq imageIdentifier.toJavaUUID())
        //            }

        //         //TODO Коннект с Михой
        //        try {
        ////            val response = cloudberry.removeEntry(
        ////                contentUUID = imageIdentifier,
        ////                bucketUUID = galleryUUID.toKotlinUUID()
        ////            )
        //        } catch (e: StatusRuntimeException) {
        //            logger.error { "image removal failed with status ${e.status}, with message ${e.message}" }
        //            "image removal failed with status ${e.status}, with message ${e.message}" to false
        //        }
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
    override fun execute(task: Task<DestroyIndexPayload>): TaskExecutionResult {
//        Images.deleteWhere { galleryUUID eq galleryIdentifier.toJavaUUID() }
//        Galleries.deleteWhere { id eq galleryIdentifier.toJavaUUID() }
        TODO()
    }

    override fun getQueueConfig(): QueueConfig = config

    override fun getPayloadTransformer(): TaskPayloadTransformer<DestroyIndexPayload> = DestroyIndexPayloadTransformer
}