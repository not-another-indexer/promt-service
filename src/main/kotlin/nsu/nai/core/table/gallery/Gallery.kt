package nsu.nai.core.table.gallery

import nsu.nai.core.table.gallery.Gallery.Status
import nsu.nai.core.table.user.Users
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import java.util.*

data class Gallery(
    val id: UUID,
    val name: String,
) {
    enum class Status {
        IN_PROCESS,
        ACTIVE,
        FOR_REMOVAL
    }
}

object Galleries : Table("galleries") {
    val id: Column<UUID> = uuid("id").autoGenerate()
    val name: Column<String> = varchar("name", length = 128)
    val userId: Column<Long> = (long("user_id") references Users.id)
    val status = enumeration<Status>("status")

    override val primaryKey = PrimaryKey(id, name = "PK_Gallery_ID")
}
