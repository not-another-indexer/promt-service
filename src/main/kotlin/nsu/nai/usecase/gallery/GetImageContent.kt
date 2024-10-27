package nsu.nai.usecase.gallery

import nsu.nai.core.table.gallery.Galleries
import nsu.nai.core.table.image.Images
import nsu.nai.exception.EntityNotFoundException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.InputStream
import java.sql.Connection
import java.util.*

class GetImageContent(
    private val userId: Long,
    private val imageUuid: UUID,
    // infrastructure
    private val getNewConnection: () -> Connection,
) {
    fun execute(): InputStream {
        Database.connect(getNewConnection)

        return transaction {
            addLogger(StdOutSqlLogger)

            Images.innerJoin(Galleries)
                .selectAll()
                .where { (Images.id eq imageUuid) and (Galleries.userId eq userId) }
                .singleOrNull()
                ?.get(Images.content)?.inputStream
                ?: throw EntityNotFoundException(imageUuid)
        }
    }
}