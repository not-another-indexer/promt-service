package nsu.nai.usecase.gallery

import nsu.nai.core.table.gallery.Galleries
import nsu.nai.core.table.image.Image
import nsu.nai.core.table.image.Image.Status
import nsu.nai.core.table.image.ImageExtension
import nsu.nai.core.table.image.Images
import nsu.nai.dbqueue.PutEntryPayload
import nsu.nai.exception.EntityNotFoundException
import nsu.platform.enqueue
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import ru.yoomoney.tech.dbqueue.api.QueueProducer
import java.sql.Connection
import java.util.*

class AddImage(
    private val userId: Long,
    private val galleryIdentifier: UUID,
    private val imageDescription: String,
    private val imageExtension: ImageExtension,
    private val imageContent: ByteArray,
    //
    private val getNewConnection: () -> Connection,
    private val putEntryProducer: QueueProducer<PutEntryPayload>
) {
    fun execute(): Image {
        Database.connect(getNewConnection)

        val newImageId = transaction {
            requireGalleryExist(userId, galleryIdentifier)

            val uuid = Images.insert {
                it[galleryUUID] = galleryIdentifier
                it[description] = imageDescription
                it[content] = ExposedBlob(imageContent)
                it[extension] = imageExtension.extension
                it[status] = Status.IN_PROCESS
            } get Images.id

            putEntryProducer.enqueue(PutEntryPayload(uuid.toString()))
            return@transaction uuid
        }

        return Image(
            id = newImageId,
            galleryId = galleryIdentifier,
            description = imageDescription
        )
    }

    private fun requireGalleryExist(userId: Long, galleryIdentifier: UUID) {
        val exist = Galleries.selectAll()
            .where { (Galleries.userId eq userId) and (Galleries.id eq galleryIdentifier) }
            .any()
        if (!exist) {
            throw EntityNotFoundException("gallery")
        }
    }
}
