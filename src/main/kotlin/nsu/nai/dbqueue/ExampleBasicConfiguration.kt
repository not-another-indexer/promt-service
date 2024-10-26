package nsu.nai.dbqueue

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import ru.yoomoney.tech.dbqueue.api.EnqueueParams
import ru.yoomoney.tech.dbqueue.api.QueueProducer
import ru.yoomoney.tech.dbqueue.api.impl.MonitoringQueueProducer
import ru.yoomoney.tech.dbqueue.api.impl.NoopPayloadTransformer
import ru.yoomoney.tech.dbqueue.api.impl.ShardingQueueProducer
import ru.yoomoney.tech.dbqueue.api.impl.SingleQueueShardRouter
import ru.yoomoney.tech.dbqueue.config.*
import ru.yoomoney.tech.dbqueue.config.impl.LoggingTaskLifecycleListener
import ru.yoomoney.tech.dbqueue.config.impl.LoggingThreadLifecycleListener
import ru.yoomoney.tech.dbqueue.settings.*
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

private const val CB_QUEUE = "cb_queue"
private const val DATABASE_URL = "jdbc:postgresql://localhost:5432/dbqueue"
private const val DATABASE_USER = "nai_user"
private const val DATABASE_PASSWORD = "nai_password"

fun main() {
    val dataSource = createDataSource()
    val jdbcTemplate = JdbcTemplate(dataSource)
    val transactionTemplate = createTransactionTemplate(dataSource)

    initializeDatabase(jdbcTemplate)

    val taskConsumedCount = AtomicInteger(0)

    val databaseAccessLayer = JdbcDatabaseAccessLayer(
        DatabaseDialect.POSTGRESQL,
        QueueTableSchema.builder().build(),
        jdbcTemplate,
        transactionTemplate
    )
    val shard = QueueShard(QueueShardId("main"), databaseAccessLayer)

    val queueService = QueueService(
        listOf(shard),
        LoggingThreadLifecycleListener(),
        LoggingTaskLifecycleListener()
    )

    val consumerFactory = QueueConsumerFactory(taskConsumedCount)
    val producerFactory = QueueProducerFactory()

    val numConsumers = 5
    for (i in 1..numConsumers) {
        val queueConfig = createQueueConfig("example_queue_$i")
        val producer = producerFactory.createQueueProducer(queueConfig, shard)
        val consumer = consumerFactory.createStringQueueConsumer(queueConfig)

        queueService.registerQueue(consumer)
        enqueueTasks(producer)
    }

    queueService.start()
}

private fun createDataSource(): HikariDataSource {
    return HikariDataSource(HikariConfig().apply {
        jdbcUrl = DATABASE_URL
        username = DATABASE_USER
        password = DATABASE_PASSWORD
        maximumPoolSize = 10
        minimumIdle = 5
        idleTimeout = 300
        connectionTimeout = 30000
    })
}

private fun createTransactionTemplate(dataSource: HikariDataSource): TransactionTemplate {
    val transactionManager = DataSourceTransactionManager(dataSource)
    return TransactionTemplate(transactionManager)
}

private fun initializeDatabase(jdbcTemplate: JdbcTemplate) {
    jdbcTemplate.execute(String.format(PG_DEFAULT_TABLE_DDL, CB_QUEUE, CB_QUEUE, CB_QUEUE))
}

private fun createQueueConfig(queueId: String): QueueConfig {
    val queueSettings = QueueSettings.builder()
        .withProcessingSettings(
            ProcessingSettings.builder()
                .withProcessingMode(ProcessingMode.SEPARATE_TRANSACTIONS)
                .withThreadCount(1)
                .build()
        )
        .withPollSettings(
            PollSettings.builder()
                .withBetweenTaskTimeout(Duration.ofMillis(100))
                .withNoTaskTimeout(Duration.ofMillis(100))
                .withFatalCrashTimeout(Duration.ofSeconds(1))
                .build()
        )
        .withFailureSettings(
            FailureSettings.builder()
                .withRetryType(FailRetryType.GEOMETRIC_BACKOFF)
                .withRetryInterval(Duration.ofMinutes(1))
                .build()
        )
        .withReenqueueSettings(
            ReenqueueSettings.builder()
                .withRetryType(ReenqueueRetryType.MANUAL)
                .build()
        )
        .withExtSettings(ExtSettings.builder().withSettings(LinkedHashMap()).build())
        .build()

    return QueueConfig(
        QueueLocation.builder()
            .withTableName(CB_QUEUE)
            .withQueueId(QueueId(queueId))
            .build(),
        queueSettings
    )
}

private fun createQueueProducer(
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

private fun enqueueTasks(producer: QueueProducer<String>) {
    producer.enqueue(EnqueueParams.create("example task"))
}

const val PG_DEFAULT_TABLE_DDL = "CREATE TABLE IF NOT EXISTS %s (\n" +
        "  id                BIGSERIAL PRIMARY KEY,\n" +
        "  queue_name        TEXT NOT NULL,\n" +
        "  payload           TEXT,\n" +
        "  created_at        TIMESTAMP WITH TIME ZONE DEFAULT now(),\n" +
        "  next_process_at   TIMESTAMP WITH TIME ZONE DEFAULT now(),\n" +
        "  attempt           INTEGER                  DEFAULT 0,\n" +
        "  reenqueue_attempt INTEGER                  DEFAULT 0,\n" +
        "  total_attempt     INTEGER                  DEFAULT 0\n" +
        ");" +
        "CREATE INDEX IF NOT EXISTS %s_name_time_desc_idx\n" +
        "  ON %s (queue_name, next_process_at, id DESC);\n" +
        "\n"