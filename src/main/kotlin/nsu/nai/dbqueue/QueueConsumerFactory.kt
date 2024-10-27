package nsu.nai.dbqueue

import ru.yoomoney.tech.dbqueue.api.QueueConsumer
import ru.yoomoney.tech.dbqueue.settings.QueueConfig
import java.util.concurrent.atomic.AtomicInteger

class QueueConsumerFactory(
    private val taskConsumedCounter: AtomicInteger
) {
    /**
     * Creates a new StringQueueConsumer with the given queue configuration.
     * Each consumer will be associated with a specific QueueConfig,
     * allowing for multiple unique consumers and queues.
     *
     * @param queueConfig The QueueConfig for the consumer's queue.
     * @return A new instance of StringQueueConsumer.
     */
    fun createStringQueueConsumer(queueConfig: QueueConfig): QueueConsumer<String> {
        return StringQueueConsumer(queueConfig, taskConsumedCounter)
    }
}