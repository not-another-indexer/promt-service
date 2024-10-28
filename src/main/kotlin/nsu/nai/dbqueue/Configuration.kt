package nsu.nai.dbqueue

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import nsu.nai.dbqueue.impl.JdbcDatabaseAccessLayer
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import ru.yoomoney.tech.dbqueue.api.QueueProducer
import ru.yoomoney.tech.dbqueue.api.TaskPayloadTransformer
import ru.yoomoney.tech.dbqueue.api.impl.MonitoringQueueProducer
import ru.yoomoney.tech.dbqueue.api.impl.ShardingQueueProducer
import ru.yoomoney.tech.dbqueue.api.impl.SingleQueueShardRouter
import ru.yoomoney.tech.dbqueue.config.*
import ru.yoomoney.tech.dbqueue.config.impl.LoggingTaskLifecycleListener
import ru.yoomoney.tech.dbqueue.config.impl.LoggingThreadLifecycleListener
import ru.yoomoney.tech.dbqueue.settings.*
import java.time.Duration

private const val CB_QUEUE = "cb_queue"
private const val DATABASE_URL = "jdbc:postgresql://localhost:5432/dbqueue"
private const val DATABASE_USER = "nai_user"
private const val DATABASE_PASSWORD = "nai_password"

fun initDbQueue(): Producers {
    val dataSource = HikariDataSource(HikariConfig().apply {
        jdbcUrl = DATABASE_URL
        username = DATABASE_USER
        password = DATABASE_PASSWORD
        maximumPoolSize = 10
        minimumIdle = 5
        idleTimeout = 300
        connectionTimeout = 30000
    })

    val transactionTemplate = TransactionTemplate(DataSourceTransactionManager(dataSource))
    val jdbcTemplate = JdbcTemplate(dataSource).also {
        // init database
        it.execute(String.format(PG_DEFAULT_TABLE_DDL, CB_QUEUE, CB_QUEUE, CB_QUEUE))
    }

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

    val (producer1, consumer1) = putEntry(shard)
    val (producer2, consumer2) = removeEntry(shard)
    val (producer3, consumer3) = initIndex(shard)
    val (producer4, consumer4) = destroyIndex(shard)

    queueService.registerQueue(consumer1)
    queueService.registerQueue(consumer2)
    queueService.registerQueue(consumer3)
    queueService.registerQueue(consumer4)

    queueService.start()

    return Producers(producer1, producer2, producer3, producer4)
}

data class Producers(
    val putEntry: QueueProducer<PutEntryPayload>,
    val removeEntry: QueueProducer<RemoveEntryPayload>,
    val initIndex: QueueProducer<InitIndexPayload>,
    val destroyIndex: QueueProducer<DestroyIndexPayload>,
)

fun putEntry(shard: QueueShard<JdbcDatabaseAccessLayer>): Pair<QueueProducer<PutEntryPayload>, PutEntryConsumer> {
    val config = createQueueConfig("PUT_ENTRY_QUEUE")
    val producer = producer(config, shard, PutEntryPayloadTransformer)
    val consumer = PutEntryConsumer(config)
    return producer to consumer
}

fun removeEntry(shard: QueueShard<JdbcDatabaseAccessLayer>): Pair<QueueProducer<RemoveEntryPayload>, RemoveEntryConsumer> {
    val config = createQueueConfig("REMOVE_ENTRY_QUEUE")
    val producer = producer(config, shard, RemoveEntryPayloadTransformer)
    val consumer = RemoveEntryConsumer(config)
    return producer to consumer
}

fun initIndex(shard: QueueShard<JdbcDatabaseAccessLayer>): Pair<QueueProducer<InitIndexPayload>, InitIndexConsumer> {
    val config = createQueueConfig("INIT_INDEX_QUEUE")
    val producer = producer(config, shard, InitIndexPayloadTransformer)
    val consumer = InitIndexConsumer(config)
    return producer to consumer
}

fun destroyIndex(shard: QueueShard<JdbcDatabaseAccessLayer>): Pair<QueueProducer<DestroyIndexPayload>, DestroyIndexConsumer> {
    val config = createQueueConfig("REMOVE_ENTRY_QUEUE")
    val producer = producer(config, shard, DestroyIndexPayloadTransformer)
    val consumer = DestroyIndexConsumer(config)
    return producer to consumer
}

private fun <P> producer(
    config: QueueConfig,
    shard: QueueShard<JdbcDatabaseAccessLayer>,
    transformer: TaskPayloadTransformer<P>
): MonitoringQueueProducer<P> {
    val router = SingleQueueShardRouter<P, JdbcDatabaseAccessLayer>(shard)
    val producer = ShardingQueueProducer(config, transformer, router)
    return MonitoringQueueProducer(producer, config.location.queueId)
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

private const val PG_DEFAULT_TABLE_DDL = "CREATE TABLE IF NOT EXISTS %s (\n" +
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