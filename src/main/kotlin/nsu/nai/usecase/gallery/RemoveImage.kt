package nsu.nai.usecase.gallery

import nsu.nai.core.table.gallery.Galleries
import nsu.nai.core.table.image.ImageEntity
import nsu.nai.core.table.image.Images
import nsu.nai.dbqueue.RemoveEntryPayload
import nsu.nai.exception.EntityNotFoundException
import nsu.nai.exception.ImageInProcessException
import nsu.platform.enqueue
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import ru.yoomoney.tech.dbqueue.api.QueueProducer
import java.sql.Connection
import java.util.*

class RemoveImage(
    private val userId: Long,
    private val imageUuid: UUID,
    // infrastructure
    private val getNewConnection: () -> Connection,
    private val removeEntryProducer: QueueProducer<RemoveEntryPayload>
) {
    fun execute() {
        Database.connect(getNewConnection)

        transaction {
            val condition = (Galleries.userId eq userId) and (Images.id eq imageUuid)

            val imageRow = Images.innerJoin(Galleries).selectAll().where { condition }.singleOrNull()
                ?: throw EntityNotFoundException(imageUuid)

            if (imageRow[Images.status] == ImageEntity.Status.IN_PROCESS) {
                throw ImageInProcessException(imageRow[Images.id])
            }

            Images.innerJoin(Galleries).update({ condition }) {
                it[Images.status] = ImageEntity.Status.FOR_REMOVAL
            }

            removeEntryProducer.enqueue(RemoveEntryPayload(imageUuid.toString()))
        }
    }
}