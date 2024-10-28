package nsu.nai.usecase.main

import nsu.nai.core.table.gallery.Galleries
import nsu.nai.core.table.gallery.Gallery
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
            require(isGalleryExist(userId, galleryIdentifier)) { "No gallery results found" }

            Galleries.update(
                where = { Galleries.id eq galleryIdentifier },
                limit = 1,
                body = { it[status] = Gallery.Status.FOR_REMOVAL }
            )

            destroyIndexProducer.enqueue(DestroyIndexPayload(galleryUUID = galleryIdentifier.toString()))
        }
    }

    private fun isGalleryExist(userId: Long, galleryIdentifier: UUID): Boolean {
        return try {
            Galleries.selectAll()
                .where { (Galleries.userId eq userId) and (Galleries.id eq galleryIdentifier) }
                .single()
            true
        } catch (e: Exception) {
            false
        }
    }
}