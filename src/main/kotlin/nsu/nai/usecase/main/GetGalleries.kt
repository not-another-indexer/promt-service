package nsu.nai.usecase.main

import nsu.nai.core.table.gallery.Galleries
import nsu.nai.core.table.gallery.GalleryEntity.Companion.toGalleryEntity
import nsu.nai.core.table.image.Images
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import java.util.*

data class Gallery(
    val id: UUID,
    val name: String,
)

class GetGalleries(
    private val userId: Long,
    // infrastructure
    private val getNewConnection: () -> Connection,
) {
    fun execute(): Map<Gallery, List<UUID>> {
        Database.connect(getNewConnection)

        return transaction {
            Galleries.selectAll()
                .where { Galleries.userId eq userId }
                .map { row -> row.toGalleryEntity() }
                .map { Gallery(it.id, it.name) }
                .associateWith { gallery ->
                    Images
                        .selectAll()
                        .where { Images.galleryUUID eq gallery.id }
                        .limit(4)
                        .map { it[Images.id] }
                }
        }
    }
}