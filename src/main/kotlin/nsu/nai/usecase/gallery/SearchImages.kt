@file:OptIn(ExperimentalUuidApi::class)

package nsu.nai.usecase.gallery

import nsu.client.CloudberryStorageClient
import nsu.nai.core.Parameter
import nsu.nai.core.table.image.Image
import nsu.nai.core.table.image.Images
import nsu.nai.core.table.image.Images.id
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import java.util.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

class SearchImages(
    private val query: String,
    private val galleryUuid: Uuid,
    private val parameters: Map<Parameter, Double>,
    private val count: Long,
    //
    private val getNewConnection: () -> Connection,
    private val cloudberry: CloudberryStorageClient
) {
    @OptIn(ExperimentalUuidApi::class)
    suspend fun execute(): List<Image> {
        val response = cloudberry.find(
            query = query,
            bucketUuid = galleryUuid,
            parameters = parameters,
            count = count
        )

        val imagesUuids = transaction {
            addLogger(StdOutSqlLogger)
            response.entriesList.map { UUID.fromString(it.contentUuid) }
        }

        Database.connect(getNewConnection)
        return Images.selectAll()
            .where { id inList imagesUuids }
            .map { image ->
                Image(
                    id = image[id].toKotlinUuid(),
                    galleryId = image[Images.galleryUuid].toKotlinUuid(),
                    description = image[Images.description]
                )
            }
    }
}