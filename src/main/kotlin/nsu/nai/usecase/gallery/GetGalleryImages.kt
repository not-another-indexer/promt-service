package nsu.nai.usecase.gallery

import nsu.nai.core.table.gallery.Galleries
import nsu.nai.core.table.image.Image
import nsu.nai.core.table.image.Images
import nsu.nai.exception.EntityNotFoundException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import java.util.*

class GetGalleryImages(
    private val userId: Long,
    private val galleryIdentifier: UUID,
    private val size: Int,
    private val offset: Long,
    //
    private val getNewConnection: () -> Connection,
) {
    fun execute(): Pair<List<Image>, Long> {
        Database.connect(getNewConnection)

        return transaction {
            addLogger(StdOutSqlLogger)

            requireGalleryExist(userId, galleryIdentifier)

            val images = Images.innerJoin(Galleries).selectAll()
                .where { (Images.galleryUUID eq galleryIdentifier) and (Galleries.userId eq userId) }
                .orderBy(Images.id)
                .offset(offset)
                .limit(size)
                .map { image ->
                    Image(
                        id = image[Images.id],
                        galleryId = image[Images.galleryUUID],
                        description = image[Images.description]
                    )
                }
            val total = Images.selectAll().count()

            images to total
        }
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
