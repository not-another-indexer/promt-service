package nsu.nai.port

import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.Context
import io.grpc.Status
import nai.*
import nai.Nai.*
import nsu.Config
import nsu.nai.dbqueue.Producers
import nsu.nai.exception.EntityAlreadyExistsException
import nsu.nai.exception.ValidationException
import nsu.nai.usecase.main.CreateGallery
import nsu.nai.usecase.main.Gallery
import nsu.nai.usecase.main.GetGalleries
import nsu.nai.usecase.main.RemoveGallery
import nsu.platform.userId
import java.util.*

class MainServiceImpl(private val producers: Producers) : MainServiceGrpcKt.MainServiceCoroutineImplBase() {

    private val logger = KotlinLogging.logger {}

    override suspend fun createGallery(request: CreateGalleryRequest): CreateGalleryResponse {
        val userId = Context.current().userId

        return handleRequest {
            val gallery = CreateGallery(
                userId,
                request.pGalleryName,
                Config.connectionProvider,
                producers.initIndex
            ).execute()

            createGalleryResponse { pGalleryUuid = gallery.id.toString() }
        }
    }

    override suspend fun getGalleries(request: GetGalleriesRequest): GetGalleriesResponse {
        val userId = Context.current().userId

        return handleRequest {
            val galleriesPreview: Map<Gallery, List<UUID>> = GetGalleries(
                userId,
                getNewConnection = Config.connectionProvider,
            ).execute()

            val galleryPreviewResponse: List<GalleryPreview> = galleriesPreview.map {
                galleryPreview {
                    pGalleryUuid = it.key.id.toString()
                    pGalleryName = it.key.name
                    pPreview.addAll(it.value.map { it.toString() })
                }
            }

            getGalleriesResponse {
                pContent.addAll(galleryPreviewResponse)
            }
        }
    }

    override suspend fun deleteGallery(request: DeleteGalleryRequest): Empty {
        return handleRequest {
            val userId = Context.current().userId

            RemoveGallery(
                userId,
                UUID.fromString(request.pGalleryUuid),
                getNewConnection = Config.connectionProvider,
                producers.destroyIndex
            ).execute()

            empty { }
        }
    }

    private fun <T> handleRequest(action: () -> T): T {
        return try {
            action()
        } catch (e: EntityAlreadyExistsException) {
            logger.warn { e.message }
            throw Status.ALREADY_EXISTS.withDescription(e.message).asRuntimeException()
        } catch (e: IllegalArgumentException) {
            logger.error { e.message }
            throw Status.INVALID_ARGUMENT.withDescription(e.message).asRuntimeException()
        } catch (e: ValidationException) {
            logger.warn { e.message }
            throw Status.INVALID_ARGUMENT.withDescription(e.message).asRuntimeException()
        } catch (exception: Throwable) {
            logger.error(exception) { "Unhandled exception" }
            throw Status.INTERNAL.withDescription("Internal server error").asRuntimeException()
        }
    }
}
