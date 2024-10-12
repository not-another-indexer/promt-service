@file:OptIn(ExperimentalUuidApi::class)

package nsu.nai.usecase

import nsu.nai.core.table.image.Image
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

class GetGalleryImages(
    private val galleryIdentifier: Uuid,
    private val size: Int,
    private val offset: Long,
    //
    private val getNewConnection: () -> Connection,
) {
    fun execute(): Pair<List<Image>, Long> {
        Database.connect(getNewConnection)

        return transaction {
            addLogger(StdOutSqlLogger)

            val images = Images.selectAll().where { Images.galleryUuid eq galleryIdentifier.toJavaUuid() }
                .offset(offset)
                .fetchSize(size)
                .map { image ->
                    Image(
                        id = image[Images.id].toKotlinUuid(),
                        galleryId = image[Images.galleryUuid].toKotlinUuid(),
                        description = image[Images.description]
                    )
                }
            val total = Images.selectAll().count()

            images to total
        }
    }
}