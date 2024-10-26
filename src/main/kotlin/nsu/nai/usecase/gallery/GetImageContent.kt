@file:OptIn(ExperimentalUuidApi::class)

package nsu.nai.usecase.gallery

import nsu.nai.core.table.gallery.Galleries
import nsu.nai.core.table.image.Images
import nsu.nai.exception.EntityNotFoundException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.InputStream
import java.sql.Connection
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@OptIn(ExperimentalUuidApi::class)
class GetImageContent(
    private val userId: Long,
    private val imageId: Uuid,
    //
    private val getNewConnection: () -> Connection,
) {
    suspend fun execute(): InputStream {
        Database.connect(getNewConnection)

        return transaction {
            addLogger(StdOutSqlLogger)

            Images.innerJoin(Galleries) { Galleries.userId eq userId }.selectAll()
                .where { Images.id eq imageId.toJavaUuid() }
                .singleOrNull()
                ?.let { it[Images.content].inputStream }
                ?: throw EntityNotFoundException(imageId.toString())
        }
    }
}