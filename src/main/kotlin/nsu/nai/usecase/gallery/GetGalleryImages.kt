package nsu.nai.usecase.gallery

import nsu.nai.core.table.gallery.Galleries
import nsu.nai.core.table.image.Image
import nsu.nai.core.table.image.ImageEntity
import nsu.nai.core.table.image.Images
import nsu.nai.exception.EntityNotFoundException
import nsu.nai.exception.ValidationException
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
    private val iUserId: Long,
    private val iGalleryUuid: UUID,
    private val iSize: Int,
    private val iOffset: Long,
    // infrastructure
    private val getNewConnection: () -> Connection,
) {
    private fun validateSelection(size: Int, offset: Long): List<String> {
        val errors = mutableListOf<String>()
        if (size !in 1..999) errors.add("size must be greater than 0 and less than 1000")
        if (offset < 0) errors.add("offset must be greater than or equal to 0")
        return errors
    }

    fun execute(): ImagesWithTotal {
        val errors = validateSelection(iSize, iOffset)
        if (errors.isNotEmpty()) throw ValidationException(errors)

        Database.connect(getNewConnection)

        return transaction {
            val galleryExists = Galleries
                .selectAll()
                .where { (Galleries.userId eq iUserId) and (Galleries.id eq iGalleryUuid) }
                .any()

            if (!galleryExists) throw EntityNotFoundException(iGalleryUuid)

            val images = Images.innerJoin(Galleries).selectAll()
                .where { (Images.galleryUUID eq iGalleryUuid) and (Galleries.userId eq iUserId) and (Images.status eq ImageEntity.Status.ACTIVE) }
                .orderBy(Images.id)
                .limit(iSize)
                .offset(iOffset)
                .map { row ->
                    Image(
                        id = row[Images.id],
                        galleryId = row[Images.galleryUUID],
                        description = row[Images.description]
                    )
                }

            val total = Images.selectAll().where { Images.galleryUUID eq iGalleryUuid }.count()

            ImagesWithTotal(images, total)
        }
    }
}