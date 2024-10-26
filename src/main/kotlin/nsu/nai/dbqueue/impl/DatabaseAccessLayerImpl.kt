package nsu.nai.dbqueue.impl

import org.springframework.jdbc.core.JdbcOperations
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionOperations
import ru.yoomoney.tech.dbqueue.config.DatabaseAccessLayer
import ru.yoomoney.tech.dbqueue.config.DatabaseDialect
import ru.yoomoney.tech.dbqueue.config.QueueTableSchema
import ru.yoomoney.tech.dbqueue.dao.QueueDao
import ru.yoomoney.tech.dbqueue.dao.QueuePickTaskDao
import ru.yoomoney.tech.dbqueue.settings.FailureSettings
import ru.yoomoney.tech.dbqueue.settings.QueueLocation
import java.util.*
import java.util.function.Supplier


class JdbcDatabaseAccessLayer(
    private val databaseDialect: DatabaseDialect,
    private val queueTableSchema: QueueTableSchema,
    jdbcOperations: JdbcOperations,
    transactionOperations: TransactionOperations
) : DatabaseAccessLayer {

    private val jdbcOperations: JdbcOperations = Objects.requireNonNull(jdbcOperations)
    private val transactionOperations: TransactionOperations = Objects.requireNonNull(transactionOperations)
    private val queueDao: QueueDao = createQueueDao(databaseDialect, queueTableSchema, jdbcOperations)

    override fun getQueueDao(): QueueDao {
        return queueDao
    }

    private fun createQueueDao(
        databaseDialect: DatabaseDialect,
        queueTableSchema: QueueTableSchema,
        jdbcOperations: JdbcOperations
    ): QueueDao {
        return when (databaseDialect) {
            DatabaseDialect.POSTGRESQL -> PostgresQueueDao(jdbcOperations, queueTableSchema)
            else -> throw IllegalArgumentException("unsupported database kind: $databaseDialect")
        }
    }

    override fun createQueuePickTaskDao(
        queueLocation: QueueLocation,
        failureSettings: FailureSettings
    ): QueuePickTaskDao {
        return when (databaseDialect) {
            DatabaseDialect.POSTGRESQL -> PostgresQueuePickTaskDao(
                jdbcOperations,
                queueTableSchema,
                queueLocation,
                failureSettings
            )

            else -> throw IllegalArgumentException("unsupported database kind: $databaseDialect")
        }
    }

    override fun getDatabaseDialect(): DatabaseDialect {
        return databaseDialect
    }

    override fun getQueueTableSchema(): QueueTableSchema {
        return queueTableSchema
    }

    override fun <T> transact(supplier: Supplier<T>): T? {
        return transactionOperations.execute { _: TransactionStatus? -> supplier.get() }
    }

    override fun transact(runnable: Runnable) {
        transact {
            Supplier {
                runnable.run()
                null
            }
        }
    }
}