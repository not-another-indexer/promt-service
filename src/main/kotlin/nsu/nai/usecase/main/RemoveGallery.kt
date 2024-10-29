package nsu.nai.usecase.main

import nsu.nai.core.table.gallery.Galleries
import nsu.nai.core.table.gallery.GalleryEntity
import nsu.nai.dbqueue.DestroyIndexPayload
import nsu.platform.enqueue
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import ru.yoomoney.tech.dbqueue.api.QueueProducer
import java.sql.Connection
import java.util.*

class RemoveGallery(
    private val userId: Long,
    private val galleryIdentifier: UUID,
    private val getNewConnection: () -> Connection,
    private val destroyIndexProducer: QueueProducer<DestroyIndexPayload>
) {
    fun execute() {
        Database.connect(getNewConnection)

        transaction {
            val galleryExists = Galleries
                .selectAll()
                .where { (Galleries.userId eq userId) and (Galleries.id eq galleryIdentifier) }
                .any()

            require(galleryExists) { "No gallery found for user $userId with ID $galleryIdentifier." }

            Galleries.update({ Galleries.id eq galleryIdentifier }) {
                it[status] = GalleryEntity.Status.FOR_REMOVAL
            }

            destroyIndexProducer.enqueue(DestroyIndexPayload(galleryIdentifier.toString()))
        }
    }
}