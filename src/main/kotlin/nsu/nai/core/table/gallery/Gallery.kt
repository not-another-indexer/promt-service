@file:OptIn(ExperimentalUuidApi::class)

package nsu.nai.core.table.gallery

import nsu.nai.core.table.user.Users
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import java.util.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class Gallery(
    val id: Uuid,
    val name: String,
)

object Galleries : Table("galleries") {
    val id: Column<UUID> = uuid("id").autoGenerate()
    val name: Column<String> = varchar("name", length = 128)
    val userId: Column<Long> = (long("user_id") references Users.id)

    override val primaryKey = PrimaryKey(id, name = "PK_Gallery_ID")
}
