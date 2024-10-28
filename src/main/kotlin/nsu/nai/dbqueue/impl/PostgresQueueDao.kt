package nsu.nai.dbqueue.impl

import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import ru.yoomoney.tech.dbqueue.api.EnqueueParams
import ru.yoomoney.tech.dbqueue.config.QueueTableSchema
import ru.yoomoney.tech.dbqueue.dao.QueueDao
import ru.yoomoney.tech.dbqueue.settings.QueueLocation
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors


class PostgresQueueDao(
    jdbcTemplate: JdbcOperations,
    private val queueTableSchema: QueueTableSchema
) : QueueDao {
    private val jdbcTemplate = NamedParameterJdbcTemplate(jdbcTemplate)
    private val enqueueSqlCache = ConcurrentHashMap<QueueLocation, String>()
    private val deleteSqlCache = ConcurrentHashMap<QueueLocation, String>()
    private val requeueSqlCache = ConcurrentHashMap<QueueLocation, String>()

    override fun enqueue(location: QueueLocation, enqueueParams: EnqueueParams<String>): Long {
        val params = createEnqueueParams(location, enqueueParams)
        val enqueueSql = enqueueSqlCache.computeIfAbsent(location) { createEnqueueSql(location) }
        return transaction {
            val stmt = getGeneratedSql(enqueueSql, params.values)
            exec(stmt, explicitStatementType = StatementType.SELECT) {
                it.next()
                it.getLong(1)
            }!!
        }
    }

    private fun getGeneratedSql(sql: String, params: Map<String, Any?>): String {
        var generatedSql = sql
        params.forEach { (key, value) ->
            generatedSql = generatedSql.replace(":$key", value.toString())
        }
        return generatedSql
    }

    override fun deleteTask(location: QueueLocation, taskId: Long): Boolean {
        val deleteSql = deleteSqlCache.computeIfAbsent(location) { createDeleteSql(location) }
        val updatedRows = jdbcTemplate.update(
            deleteSql,
            MapSqlParameterSource().apply {
                addValue("id", taskId)
                addValue("queueName", location.queueId.asString())
            }
        )
        return updatedRows > 0
    }

    override fun reenqueue(location: QueueLocation, taskId: Long, executionDelay: Duration): Boolean {
        val updatedRows = jdbcTemplate.update(
            requeueSqlCache.computeIfAbsent(location, this::createReenqueueSql),
            MapSqlParameterSource()
                .addValue("id", taskId)
                .addValue("queueName", location.queueId.asString())
                .addValue("executionDelay", executionDelay.seconds)
        )
        return updatedRows != 0
    }

    private fun createEnqueueParams(
        location: QueueLocation,
        enqueueParams: EnqueueParams<String>
    ): MapSqlParameterSource {
        return MapSqlParameterSource().apply {
            addValue("queueName", location.queueId.asString())
            addValue("payload", enqueueParams.payload)
            addValue("executionDelay", enqueueParams.executionDelay.seconds)

            queueTableSchema.extFields.forEach { addValue(it, null) }
            enqueueParams.extData.forEach { (paramName, value) -> addValue(paramName, value) }
        }
    }

    private fun createEnqueueSql(location: QueueLocation): String {
        return """
            INSERT INTO 
            ${location.tableName}(queue_name,payload,next_process_at,reenqueue_attempt,total_attempt) 
            VALUES (':queueName', ':payload', now() +  0 * INTERVAL '1 SECOND', 0, 0) RETURNING id
        """.trimIndent()
    }

    private fun createDeleteSql(location: QueueLocation): String {
        return "DELETE FROM " + location.tableName + " WHERE " + queueTableSchema.queueNameField +
                " = :queueName AND " + queueTableSchema.idField + " = :id"
    }

    private fun createReenqueueSql(location: QueueLocation): String {
        return "UPDATE " + location.tableName + " SET " + queueTableSchema.nextProcessAtField +
                " = now() + :executionDelay * INTERVAL '1 SECOND', " +
                queueTableSchema.attemptField + " = 0, " +
                queueTableSchema.reenqueueAttemptField +
                " = " + queueTableSchema.reenqueueAttemptField + " + 1 " +
                "WHERE " + queueTableSchema.idField + " = :id AND " +
                queueTableSchema.queueNameField + " = :queueName"
    }
}