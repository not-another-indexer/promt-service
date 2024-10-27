package nsu.nai.dbqueue

import io.github.oshai.kotlinlogging.KotlinLogging
import ru.yoomoney.tech.dbqueue.api.QueueConsumer
import ru.yoomoney.tech.dbqueue.api.Task
import ru.yoomoney.tech.dbqueue.api.TaskExecutionResult
import ru.yoomoney.tech.dbqueue.api.TaskPayloadTransformer
import ru.yoomoney.tech.dbqueue.api.impl.NoopPayloadTransformer
import ru.yoomoney.tech.dbqueue.settings.QueueConfig
import java.lang.Thread.sleep
import java.util.concurrent.atomic.AtomicInteger

class StringQueueConsumer(
    private val queueConfig: QueueConfig,
    private val taskConsumedCount: AtomicInteger
) : QueueConsumer<String> {

    override fun execute(task: Task<String>): TaskExecutionResult {
        log.info { "payload=${task.payloadOrThrow}" }
        taskConsumedCount.incrementAndGet()
        return TaskExecutionResult.finish()
    }

    override fun getQueueConfig(): QueueConfig {
        return queueConfig
    }

    override fun getPayloadTransformer(): TaskPayloadTransformer<String> {
        return NoopPayloadTransformer.getInstance()
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}

