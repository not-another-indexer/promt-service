package nsu.nai.dbqueue

import ru.yoomoney.tech.dbqueue.api.QueueProducer
import ru.yoomoney.tech.dbqueue.api.impl.MonitoringQueueProducer
import ru.yoomoney.tech.dbqueue.api.impl.NoopPayloadTransformer
import ru.yoomoney.tech.dbqueue.api.impl.ShardingQueueProducer
import ru.yoomoney.tech.dbqueue.api.impl.SingleQueueShardRouter
import ru.yoomoney.tech.dbqueue.config.QueueShard
import ru.yoomoney.tech.dbqueue.settings.QueueConfig

class QueueProducerFactory {
    fun createQueueProducer(
        config: QueueConfig,
        shard: QueueShard<JdbcDatabaseAccessLayer>
    ): QueueProducer<String> {
        val shardingQueueProducer = ShardingQueueProducer(
            config,
            NoopPayloadTransformer.getInstance(),
            SingleQueueShardRouter(shard)
        )
        return MonitoringQueueProducer(shardingQueueProducer, config.location.queueId)
    }
}