@file:OptIn(ExperimentalUuidApi::class)

package nsu.nai.usecase

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

class GetGalleries(
    private val userIdentifier: Long,
    //
    private val getNewConnection: () -> Connection,
) {
    fun execute(): Map<Gallery, List<Uuid>> {
        Database.connect(getNewConnection)

        val galleries: List<Gallery> = transaction {
            addLogger(StdOutSqlLogger)

            Galleries.selectAll()
                .where { Galleries.userId eq userIdentifier }
                .map { gallery ->
                    Gallery(
                        id = gallery[Galleries.id].toKotlinUuid(),
                        name = gallery[Galleries.name]
                    )
                }
        }

        return galleries.map { gallery ->
            gallery to Images.selectAll()
                .where { Images.galleryUuid eq gallery.id.toJavaUuid() }
                .limit(4)
                .map { image -> image[Images.id].toKotlinUuid() }
        }.toMap()
    }
}