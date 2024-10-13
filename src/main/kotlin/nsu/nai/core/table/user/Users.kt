package nsu.nai.core.table.user

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

data class User(
    val id: Long,
    val username: String,
    val displayName: String,
    val passwordHash: String,
) {
    init {
        require(id > 0)
        require(username.isNotBlank())
        require(displayName.isNotBlank())
    }
}

object Users : Table("users") {
    val id: Column<Long> = long("id")
    val username: Column<String> = varchar("username", length = 64)
    val displayName: Column<String> = varchar("display_name", length = 128)
    val passwordHash: Column<String> = varchar("password_hash", length = 128)

    override val primaryKey = PrimaryKey(id, name = "PK_User_ID")
}
