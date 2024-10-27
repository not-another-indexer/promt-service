package nsu.nai.core.table.gallery

import nsu.nai.core.table.gallery.GalleryEntity.Status
import nsu.nai.core.table.user.Users
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import java.util.*

data class GalleryEntity(
    val id: UUID,
    val name: String,
    val userId: Long,
    val status: Status
) {
    enum class Status {
        IN_PROCESS,
        ACTIVE,
        FOR_REMOVAL
    }

    companion object {
        fun ResultRow.toGalleryEntity(): GalleryEntity {
            return GalleryEntity(
                id = this[Galleries.id],
                name = this[Galleries.name],
                userId = this[Galleries.userId],
                status = this[Galleries.status],
            )
        }
    }
}

object Galleries : Table("galleries") {
    val id: Column<UUID> = uuid("id").autoGenerate()
    val name: Column<String> = varchar("name", length = 128)
    val userId: Column<Long> = (long("user_id") references Users.id)
    val status = enumeration<Status>("status")

    override val primaryKey = PrimaryKey(id, name = "PK_Gallery_ID")
}
