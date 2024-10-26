package nsu.nai.dbqueue

import ru.yoomoney.tech.dbqueue.api.QueueConsumer
import ru.yoomoney.tech.dbqueue.settings.QueueConfig
import java.util.concurrent.atomic.AtomicInteger

class ConsumerFactory(
    private val queueConfig: QueueConfig,
    private val taskConsumedCounter: AtomicInteger
) {
    fun createStringQueueConsumer(): QueueConsumer<String> {
        return StringQueueConsumer(queueConfig, taskConsumedCounter)
    }
}