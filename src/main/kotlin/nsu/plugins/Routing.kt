package nsu.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello World!")
        }

        swaggerUI(path = "api/gallery", swaggerFile = "api/v1/gallery.yaml")

        post("/search-images") {
            call.respond(HttpStatusCode.NotImplemented, "Not Implemented")
        }

        post("/delete-image") {
            call.respond(HttpStatusCode.NotImplemented, "Not Implemented")
        }

        post("/add-image") {
            call.respond(HttpStatusCode.NotImplemented, "Not Implemented")
        }

        get("/get-images") {
            call.respond(HttpStatusCode.NotImplemented, "Not Implemented")
        }
    }
}
