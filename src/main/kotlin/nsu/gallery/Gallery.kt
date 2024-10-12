@file:OptIn(ExperimentalUuidApi::class)

package nsu.gallery

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import nsu.Config
import nsu.nai.core.table.gallery.Gallery
import nsu.nai.usecase.CreateGallery
import nsu.nai.usecase.GetGalleries
import nsu.nai.usecase.RemoveGallery
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun Application.configureGallery() {
    routing {
        swaggerUI(path = "api/galleries", swaggerFile = "api/v1/gallery.yaml")

        post("/galleries") {
            val receive = call.receive<CreateGalleryRequest>()
            val name = receive.galleryName
            val createdGallery: Gallery = CreateGallery(
                userIdentifier = 0,
                name,
                Config.connectionProvider,
                Config.cloudberry
            ).execute()
            call.respond(
                HttpStatusCode.OK,
                CreateGalleryResponse(
                    createdGallery.id.toString(),
                    createdGallery.name,
                )
            )
        }

        get("/galleries") {
            call.respond(
                HttpStatusCode.OK,
                GetGalleriesResponse(
                    GetGalleries(0, Config.connectionProvider).execute().map { (gallery, uuids) ->
                        GalleryDto(
                            gallery.id.toString(), gallery.name, uuids.map { it.toString() }
                        )
                    }
                )
            )
        }

        get("/galleries/{galleryId}/images") {
            call.respond(HttpStatusCode.NotImplemented, "Getting images form gallery not implemented")
        }

        delete("/galleries/{galleryId}") {
            val receive = Uuid.parse(call.pathParameters["galleryId"]!!)
            RemoveGallery(
                receive,
                Config.connectionProvider,
                Config.cloudberry
            )
            call.respond(HttpStatusCode.NotImplemented, "Deleting gallery not implemented")
        }

    }
}