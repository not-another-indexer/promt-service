@file:OptIn(ExperimentalUuidApi::class)

package nsu.nai.port

import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.Context
import io.grpc.Status
import nai.*
import nai.Nai.*
import nsu.Config
import nsu.nai.core.table.gallery.Gallery
import nsu.nai.exception.EntityAlreadyExistsException
import nsu.nai.usecase.gallery.CreateGallery
import nsu.nai.usecase.gallery.GetGalleries
import nsu.nai.usecase.gallery.RemoveGallery
import nsu.platform.userId
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class MainServiceImpl : MainServiceGrpcKt.MainServiceCoroutineImplBase() {

    private val logger = KotlinLogging.logger {}

    override suspend fun createGallery(request: CreateGalleryRequest): CreateGalleryResponse {
        val userId = Context.current().userId

        return handleRequest {
            val gallery = CreateGallery(
                userId,
                request.pGalleryName,
                getNewConnection = Config.connectionProvider,
                cloudberry = Config.cloudberry
            ).execute()

            createGalleryResponse {
                pGalleryId = gallery.id.toString()
            }
        }
    }

    override suspend fun getGalleries(request: GetGalleriesRequest): GetGalleriesResponse {
        val userId = Context.current().userId

        return handleRequest {
            val galleriesPreview: Map<Gallery, List<Uuid>> = GetGalleries(
                userId,
                getNewConnection = Config.connectionProvider,
            ).execute()

            val galleryPreviewResponse: List<GalleryPreview> = galleriesPreview.map {
                galleryPreview {
                    pGalleryId = it.key.id.toString()
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
                Uuid.parse(request.pGalleryId),
                getNewConnection = Config.connectionProvider,
                cloudberry = Config.cloudberry
            ).execute()

            empty {  }
        }
    }

    private fun <T> handleRequest(action: () -> T): T {
        return try {
            action()
        } catch (e: EntityAlreadyExistsException) {
            logger.error { e.message }
            throw Status.ALREADY_EXISTS.withDescription(e.message).asRuntimeException()
        } catch (e: IllegalArgumentException) {
            logger.error { e.message }
            throw Status.INVALID_ARGUMENT.withDescription(e.message).asRuntimeException()
        } catch (exception: Throwable) {
            logger.error(exception) { "Unhandled exception" }
            throw Status.INTERNAL.withDescription("Internal server error").asRuntimeException()
        }
    }
}
