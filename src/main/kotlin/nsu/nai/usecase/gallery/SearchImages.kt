package nsu.nai.usecase.gallery

import nsu.client.CloudberryStorageClient
import nsu.nai.core.Parameter
import nsu.nai.core.table.gallery.Galleries
import nsu.nai.core.table.image.Image
import nsu.nai.core.table.image.Images
import nsu.nai.exception.EntityNotFoundException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import java.util.*

data class ImageWithMetric(
    val image: Image,
    val metrics: Map<String, Double>
)

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
    suspend fun execute(): List<ImageWithMetric> {
        Database.connect(getNewConnection)
        transaction {
            val exists = Galleries.selectAll()
                .where { (Galleries.userId eq userId) and (Galleries.id eq galleryUuid) }
                .any()

            if (!exists) {
                throw EntityNotFoundException(galleryUuid)
            }
        }

        try {
            val response = cloudberry.find(
                query = query,
                bucketUUID = galleryUuid,
                parameters = parameters,
                count = count
            )

            val imagesWithMetric = response.pEntriesList.associate { entry ->
                UUID.fromString(entry.pContentUuid) to entry.pMetricsList.associate { metric ->
                    metric.pParameter.name to metric.pValue
                }
            }

            return transaction {
                Images.selectAll()
                    .where { Images.id inList imagesWithMetric.keys }
                    .map { image ->
                        ImageWithMetric(
                            image = Image(
                                id = image[Images.id],
                                galleryId = image[Images.galleryUUID],
                                description = image[Images.description]
                            ),
                            metrics = imagesWithMetric[image[Images.id]] ?: emptyMap()
                        )
                    }
            }
        } catch (e: Exception) {
            // If search fails, search by description
            val descriptionResults = transaction {
                Images.selectAll()
                    .where {
                        (Images.galleryUUID eq galleryUuid) and
                        (Images.description.lowerCase() like "%${query.lowercase()}%")
                    }
                    .limit(count.toInt())
                    .map { image ->
                        ImageWithMetric(
                            image = Image(
                                id = image[Images.id],
                                galleryId = image[Images.galleryUUID],
                                description = image[Images.description]
                            ),
                            metrics = mapOf("description" to 1.0)
                        )
                    }
            }

            // If no results found by description, return first N images from gallery
            if (descriptionResults.isEmpty()) {
                return transaction {
                    Images.selectAll()
                        .where { Images.galleryUUID eq galleryUuid }
                        .limit(count.toInt())
                        .map { image ->
                            ImageWithMetric(
                                image = Image(
                                    id = image[Images.id],
                                    galleryId = image[Images.galleryUUID],
                                    description = image[Images.description]
                                ),
                                metrics = mapOf("fallback" to 0.0)
                            )
                        }
                }
            }

            return descriptionResults
        }
    }
}
