package nsu.gallery

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.swagger.*
import io.ktor.http.*

fun Application.configureGallery() {
    routing {
        swaggerUI(path = "api/galleries", swaggerFile = "api/v1/gallery.yaml")

        post("/galleries") {
            call.respond(HttpStatusCode.NotImplemented, "Galleries not implemented")
        }

        get("/galleries/{galleryId}/images") {
            call.respond(HttpStatusCode.NotImplemented,"Getting images form gallery not implemented")
        }

        delete("/galleries/{galleryId}") {
            call.respond(HttpStatusCode.NotImplemented, "Deleting gallery not implemented")
        }

    }
}