package nsu.nai.usecase.main

import nsu.nai.core.table.gallery.Galleries
import nsu.nai.core.table.gallery.Gallery
import nsu.nai.core.table.gallery.Gallery.Status
import nsu.nai.dbqueue.InitIndexPayload
import nsu.nai.exception.EntityAlreadyExistsException
import nsu.platform.enqueue
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import ru.yoomoney.tech.dbqueue.api.QueueProducer
import java.sql.Connection

class CreateGallery(
    private val userId: Long,
    private val galleryName: String,
    private val getNewConnection: () -> Connection,
    private val initIndexProducer: QueueProducer<InitIndexPayload>
) {
    fun execute(): Gallery {
        Database.connect(getNewConnection)

        val newGalleryId = transaction {
            requireNotExist(userId, galleryName)

            val uuid = Galleries.insert {
                it[name] = galleryName
                it[userId] = this@CreateGallery.userId
                it[status] = Status.IN_PROCESS
            } get Galleries.id

            initIndexProducer.enqueue(InitIndexPayload(galleryUUID = uuid.toString()))
            return@transaction uuid
        }

        return Gallery(newGalleryId, galleryName)
    }

    private fun requireNotExist(userId: Long, galleryName: String) {
        val exist = Galleries.selectAll()
            .where { (Galleries.userId eq userId) and (Galleries.name eq galleryName) }
            .any()

        if (exist) {
            throw EntityAlreadyExistsException(galleryName)
        }
    }
}
