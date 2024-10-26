@file:OptIn(ExperimentalUuidApi::class)

package nsu.nai.usecase.gallery

import nsu.nai.core.table.gallery.Galleries
import nsu.nai.core.table.gallery.Gallery
import nsu.nai.core.table.image.Images
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@OptIn(ExperimentalUuidApi::class)
class GetGalleries(
    private val userId: Long,
    //
    private val getNewConnection: () -> Connection,
) {
    fun execute(): Map<Gallery, List<Uuid>> {

        Database.connect(getNewConnection)

        return transaction {
            addLogger(StdOutSqlLogger)

            Galleries.selectAll()
                .where { Galleries.userId eq userId }
                .map { gallery ->
                    Gallery(
                        id = gallery[Galleries.id].toKotlinUuid(),
                        name = gallery[Galleries.name]
                    )
                }.associateWith { gallery ->
                    Images.selectAll()
                        .where { Images.galleryUuid eq gallery.id.toJavaUuid() }
                        .limit(4)
                        .map { image -> image[Images.id].toKotlinUuid() }
                }
        }
    }
}