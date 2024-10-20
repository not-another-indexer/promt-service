@file:OptIn(ExperimentalUuidApi::class)

package nsu.nai.usecase.gallery

import nsu.nai.core.table.image.Images
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.InputStream
import java.sql.Connection
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

class GetImageContent(
    private val userId: Long,
    private val imageIdentifier: Uuid,
    //
    private val getNewConnection: () -> Connection,
) {
    @ExperimentalUuidApi
    suspend fun execute(): InputStream {
        println(userId)
        Database.connect(getNewConnection)

        return transaction {
            addLogger(StdOutSqlLogger)

            Images.selectAll()
                .where { Images.id eq imageIdentifier.toJavaUuid() }
                .single()[Images.content]
                .inputStream
        }
    }
}