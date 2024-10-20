@file:OptIn(ExperimentalUuidApi::class)

package nsu.nai.core.table.image

import nsu.nai.core.table.gallery.Galleries
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class Image(
    val id: Uuid,
    val galleryId: Uuid,
    val description: String,
)

object Images : Table("images") {
    val id: Column<UUID> = uuid("id").autoGenerate()
    val galleryUuid: Column<UUID> = (uuid("gallery_uuid") references Galleries.id)
    val description: Column<String> = varchar("description", length = 2048)
    val content: Column<ExposedBlob> = blob("content")

    override val primaryKey = PrimaryKey(Images.id, name = "PK_Image_ID")
}
