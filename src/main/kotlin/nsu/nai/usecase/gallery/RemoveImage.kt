package nsu.nai.usecase.gallery

import nsu.nai.core.table.gallery.Galleries
import nsu.nai.core.table.image.Image.Status
import nsu.nai.core.table.image.Images
import nsu.nai.dbqueue.RemoveEntryPayload
import nsu.nai.exception.EntityNotFoundException
import nsu.nai.exception.InProcessException
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
    private val imageIdentifier: UUID,
    private val getNewConnection: () -> Connection,
    private val removeEntryProducer: QueueProducer<RemoveEntryPayload>
) {
    fun execute() {
        Database.connect(getNewConnection)

        transaction {
            val op = (Galleries.userId eq userId) and (Images.id eq imageIdentifier)
            val result = Images.innerJoin(Galleries).selectAll().where(op).singleOrNull()
                ?: throw EntityNotFoundException(imageIdentifier.toString())

            val status: Status = result[Images.status]
            if (status == Status.IN_PROCESS) {
                throw InProcessException()
            }
            Images.innerJoin(Galleries).update({ op }) {
                it[Images.status] = Status.FOR_REMOVAL
            }

            removeEntryProducer.enqueue(RemoveEntryPayload(imageIdentifier.toString()))
        }
    }
}