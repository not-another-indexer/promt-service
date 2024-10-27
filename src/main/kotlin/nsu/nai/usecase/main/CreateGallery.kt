package nsu.nai.usecase.main

import nsu.nai.core.table.gallery.Galleries
import nsu.nai.core.table.gallery.GalleryEntity
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
    private val iUserId: Long,
    private val iGalleryName: String,
    // infrastructure
    private val getNewConnection: () -> Connection,
    private val initIndexProducer: QueueProducer<InitIndexPayload>
) {
    fun execute(): Gallery {
        Database.connect(getNewConnection)

        return transaction {
            val galleryExists = Galleries
                .selectAll()
                .where { (Galleries.userId eq iUserId) and (Galleries.name eq iGalleryName) }
                .any()

            if (galleryExists) {
                throw EntityAlreadyExistsException("Gallery '$getNewConnection' already exists for user $iUserId.")
            }

            val galleryId = Galleries.insert {
                it[name] = iGalleryName
                it[userId] = iUserId
                it[status] = GalleryEntity.Status.IN_PROCESS
            } get Galleries.id

            initIndexProducer.enqueue(InitIndexPayload(galleryUUID = galleryId.toString()))

            Gallery(id = galleryId, name = iGalleryName)
        }
    }
}