package nsu.nai.usecase.gallery

import cloudberry.findResponse
import cloudberry.findResponseEntry
import nsu.client.CloudberryStorageClient
import nsu.nai.core.Parameter
import nsu.nai.core.table.gallery.Galleries
import nsu.nai.core.table.image.Image
import nsu.nai.core.table.image.Images
import nsu.nai.exception.EntityNotFoundException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import java.util.*

class SearchImages(
    private val userId: Long,
    private val query: String,
    private val galleryUuid: UUID,
    private val parameters: Map<Parameter, Double>,
    private val count: Long,
    // infrastructure
    private val getNewConnection: () -> Connection,
    private val cloudberry: CloudberryStorageClient
) {
    suspend fun execute(): List<Image> {
        Database.connect(getNewConnection)
        transaction {
            requireGalleryExist(userId, galleryUuid)
        }

//        val response = cloudberry.find(
//            query = query,
//            bucketUUID = galleryUUID,
//            parameters = parameters,
//            count = count
//        )

        //Test stub
        val v1 = findResponseEntry {
            pContentUUID = "518c3079-6fbd-423c-b3c5-62b3e7282b5c"
        }
        val v2 = findResponseEntry {
            pContentUUID = "6131d3c0-c3c7-483a-ae6f-9e12a68fb362"
        }
        val v3 = findResponseEntry {
            pContentUUID = "7c4c7899-47f0-404d-bfa6-948abab182e4"
        }

        val response = findResponse {
            pEntries.add(v1)
            pEntries.add(v2)
            pEntries.add(v3)
        }

        val imagesUUIDs = transaction {
            addLogger(StdOutSqlLogger)
            response.pEntriesList.map { UUID.fromString(it.pContentUUID) }
        }

        return transaction {
            Images.selectAll()
                .where { Images.id inList imagesUUIDs }
                .map { image ->
                    Image(
                        id = image[Images.id],
                        galleryId = image[Images.galleryUUID],
                        description = image[Images.description]
                    )
                }
        }
    }

    private fun requireGalleryExist(userId: Long, galleryIdentifier: UUID) {
        val exist = Galleries.selectAll()
            .where { (Galleries.userId eq userId) and (Galleries.id eq galleryIdentifier) }
            .any()
        if (!exist) {
            throw EntityNotFoundException(galleryUuid)
        }
    }
}