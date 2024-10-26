package nsu.nai.dbqueue.impl

import org.springframework.jdbc.core.JdbcOperations
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import ru.yoomoney.tech.dbqueue.api.TaskRecord
import ru.yoomoney.tech.dbqueue.config.QueueTableSchema
import ru.yoomoney.tech.dbqueue.dao.QueuePickTaskDao
import ru.yoomoney.tech.dbqueue.settings.FailRetryType
import ru.yoomoney.tech.dbqueue.settings.FailureSettings
import ru.yoomoney.tech.dbqueue.settings.QueueLocation
import java.sql.ResultSet
import java.time.ZoneId
import java.time.ZonedDateTime

class PostgresQueuePickTaskDao(
    jdbcTemplate: JdbcOperations,
    private val queueTableSchema: QueueTableSchema,
    queueLocation: QueueLocation,
    failureSettings: FailureSettings
) : QueuePickTaskDao {
    private val jdbcTemplate = NamedParameterJdbcTemplate(jdbcTemplate)

    private var pickTaskSql: String = createPickTaskSql(queueLocation, failureSettings)
    private var pickTaskSqlPlaceholders: MapSqlParameterSource =
        createSqlParameterSource(queueLocation, failureSettings)

    init {
        failureSettings.registerObserver { _, newValue ->
            pickTaskSql = createPickTaskSql(queueLocation, newValue)
            pickTaskSqlPlaceholders = createSqlParameterSource(queueLocation, newValue)
        }
    }

    override fun pickTask(): TaskRecord? {
        return jdbcTemplate.execute(pickTaskSql, pickTaskSqlPlaceholders) { ps ->
            ps.executeQuery().use { rs ->
                if (!rs.next()) return@use null

                val additionalData = queueTableSchema.extFields.associateWith { key ->
                    runCatching { rs.getString(key) }.getOrElse { throw RuntimeException(it) }
                }.toMutableMap()

                TaskRecord.builder().apply {
                    withId(rs.getLong(queueTableSchema.idField))
                    withCreatedAt(getZonedDateTime(rs, queueTableSchema.createdAtField))
                    withNextProcessAt(getZonedDateTime(rs, queueTableSchema.nextProcessAtField))
                    withPayload(rs.getString(queueTableSchema.payloadField))
                    withAttemptsCount(rs.getLong(queueTableSchema.attemptField))
                    withReenqueueAttemptsCount(rs.getLong(queueTableSchema.reenqueueAttemptField))
                    withTotalAttemptsCount(rs.getLong(queueTableSchema.totalAttemptField))
                    withExtData(additionalData)
                }.build()
            }
        }
    }

    private fun createPickTaskSql(location: QueueLocation, failureSettings: FailureSettings): String {
        val extFieldsSql = if (queueTableSchema.extFields.isNotEmpty()) {
            ", ${queueTableSchema.extFields.joinToString(", ") { "q.$it" }}"
        } else ""

        return """
            WITH cte AS (
                SELECT ${queueTableSchema.idField}
                FROM ${location.tableName}
                WHERE ${queueTableSchema.queueNameField} = :queueName
                  AND ${queueTableSchema.nextProcessAtField} <= now()
                ORDER BY ${queueTableSchema.nextProcessAtField} ASC
                LIMIT 1
                FOR UPDATE SKIP LOCKED
            )
            UPDATE ${location.tableName} q
            SET ${queueTableSchema.nextProcessAtField} = ${
            getNextProcessTimeSql(
                failureSettings.retryType,
                queueTableSchema
            )
        },
                ${queueTableSchema.attemptField} = ${queueTableSchema.attemptField} + 1,
                ${queueTableSchema.totalAttemptField} = ${queueTableSchema.totalAttemptField} + 1
            FROM cte
            WHERE q.${queueTableSchema.idField} = cte.${queueTableSchema.idField}
            RETURNING q.${queueTableSchema.idField},
                      q.${queueTableSchema.payloadField},
                      q.${queueTableSchema.attemptField},
                      q.${queueTableSchema.reenqueueAttemptField},
                      q.${queueTableSchema.totalAttemptField},
                      q.${queueTableSchema.createdAtField},
                      q.${queueTableSchema.nextProcessAtField}$extFieldsSql
        """.trimIndent()
    }

    private fun createSqlParameterSource(
        location: QueueLocation,
        failureSettings: FailureSettings
    ): MapSqlParameterSource {
        return MapSqlParameterSource().apply {
            addValue("queueName", location.queueId.asString())
            addValue("retryInterval", failureSettings.retryInterval.seconds)
        }
    }

    private fun getZonedDateTime(rs: ResultSet, time: String): ZonedDateTime {
        return ZonedDateTime.ofInstant(rs.getTimestamp(time).toInstant(), ZoneId.systemDefault())
    }

    private fun getNextProcessTimeSql(failRetryType: FailRetryType, queueTableSchema: QueueTableSchema): String {
        return when (failRetryType) {
            FailRetryType.GEOMETRIC_BACKOFF -> "now() + power(2, ${queueTableSchema.attemptField}) * :retryInterval * INTERVAL '1 SECOND'"
            FailRetryType.ARITHMETIC_BACKOFF -> "now() + (1 + (${queueTableSchema.attemptField} * 2)) * :retryInterval * INTERVAL '1 SECOND'"
            FailRetryType.LINEAR_BACKOFF -> "now() + :retryInterval * INTERVAL '1 SECOND'"
            else -> throw IllegalStateException("unknown retry type: $failRetryType")
        }
    }
}