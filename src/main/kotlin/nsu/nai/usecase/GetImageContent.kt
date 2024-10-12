@file:OptIn(ExperimentalUuidApi::class)

package nsu.nai.usecase

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
    private val imageIdentifier: Uuid,
    //
    private val getNewConnection: () -> Connection,
) {
    suspend fun execute(): InputStream {
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