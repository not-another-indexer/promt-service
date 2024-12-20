package nsu.nai.core.table.image

import nsu.nai.core.table.gallery.Galleries
import nsu.nai.core.table.image.ImageEntity.Status
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import java.util.*

data class Image(
    val id: UUID,
    val galleryId: UUID,
    val description: String,
)

data class ImageEntity(
    val id: UUID,
    val galleryUUID: UUID,
    val description: String,
    val extension: String,
    val status: Status,
) {
    enum class Status {
        IN_PROCESS,
        ACTIVE,
        FOR_REMOVAL
    }

    companion object {
        fun ResultRow.toImageEntity(): ImageEntity {
            return ImageEntity(
                id = this[Images.id],
                galleryUUID = this[Images.galleryUUID],
                description = this[Images.description],
                extension = this[Images.extension],
                status = this[Images.status],
            )
        }
    }
}

object Images : Table("images") {
    val id: Column<UUID> = uuid("id").autoGenerate()
    val galleryUUID: Column<UUID> = (uuid("gallery_UUID") references Galleries.id)
    val description: Column<String> = varchar("description", length = 2048)
    val content: Column<ExposedBlob> = blob("content")
    val extension: Column<String> = varchar("extension", length = 10)
    val status = enumeration<Status>("status")

    override val primaryKey = PrimaryKey(id, name = "PK_Image_ID")
}
