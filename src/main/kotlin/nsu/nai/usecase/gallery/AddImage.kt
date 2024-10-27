package nsu.nai.usecase.gallery

import nsu.nai.core.table.gallery.Galleries
import nsu.nai.core.table.image.Image
import nsu.nai.core.table.image.ImageEntity
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
    private val galleryUuid: UUID,
    private val imageDescription: String,
    private val imageExtension: ImageExtension,
    private val imageContent: ByteArray,
    // infrastructure
    private val getNewConnection: () -> Connection,
    private val putEntryProducer: QueueProducer<PutEntryPayload>
) {
    fun execute(): Image {
        Database.connect(getNewConnection)

        val newImageId = transaction {
            val galleryExists = Galleries
                .selectAll()
                .where { (Galleries.userId eq userId) and (Galleries.id eq galleryUuid) }
                .any()

            if (!galleryExists) throw EntityNotFoundException(galleryUuid)

            val imageId = Images.insert {
                it[galleryUUID] = galleryUuid
                it[description] = imageDescription
                it[content] = ExposedBlob(imageContent)
                it[extension] = imageExtension.extension
                it[status] = ImageEntity.Status.IN_PROCESS
            } get Images.id

            imageId.also {
                putEntryProducer.enqueue(PutEntryPayload(it))
            }
        }

        return Image(
            id = newImageId,
            galleryId = galleryUuid,
            description = imageDescription
        )
    }
}