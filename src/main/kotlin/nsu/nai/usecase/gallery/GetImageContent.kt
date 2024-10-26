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
    private val imageId: UUID,
    //
    private val getNewConnection: () -> Connection,
) {
    fun execute(): InputStream {
        Database.connect(getNewConnection)

        return transaction {
            addLogger(StdOutSqlLogger)

            Images.innerJoin(Galleries) { Galleries.userId eq userId }.selectAll()
                .where { Images.id eq imageId }
                .singleOrNull()
                ?.let { it[Images.content].inputStream }
                ?: throw EntityNotFoundException(imageId.toString())
        }
    }
}