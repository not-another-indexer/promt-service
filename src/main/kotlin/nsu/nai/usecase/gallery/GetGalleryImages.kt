package nsu.nai.usecase.gallery

import nsu.nai.core.table.gallery.Galleries
import nsu.nai.core.table.image.Image
import nsu.nai.core.table.image.Images
import nsu.nai.exception.EntityNotFoundException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import java.util.*

data class ImagesWithTotal(
    val images: List<Image>,
    val total: Long
)

class GetGalleryImages(
    private val userId: Long,
    private val galleryUuid: UUID,
    private val size: Int,
    private val offset: Long,
    // infrastructure
    private val getNewConnection: () -> Connection,
) {
    fun execute(): ImagesWithTotal {
        Database.connect(getNewConnection)

        return transaction {
            val galleryExists = Galleries
                .selectAll()
                .where { (Galleries.userId eq userId) and (Galleries.id eq galleryUuid) }
                .any()

            if (!galleryExists) throw EntityNotFoundException(galleryUuid)

            val images = Images.innerJoin(Galleries).selectAll()
                .where { (Images.galleryUUID eq galleryUuid) and (Galleries.userId eq userId) }
                .orderBy(Images.id)
                .limit(size)
                .offset(offset)
                .map { row ->
                    Image(
                        id = row[Images.id],
                        galleryId = row[Images.galleryUUID],
                        description = row[Images.description]
                    )
                }

            val total = Images.selectAll().where { Images.galleryUUID eq galleryUuid }.count()

            ImagesWithTotal(images, total)
        }
    }
}