package nsu.auth

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureAuth() {
    install(ContentNegotiation) {
        json()
    }

    routing {
        swaggerUI(path = "swagger-ui", swaggerFile = "api/v1/auth.yaml")

        post("/register") {
            val request = call.receive<RegisterUserRequest>()
            call.respondText("Registration not implemented", status = HttpStatusCode.NotImplemented)
        }

        post("/signin") {
            val request = call.receive<SignInUserRequest>()
            call.respondText("Sign-in not implemented", status = HttpStatusCode.NotImplemented)
        }

        post("/refresh-token") {
            val request = call.receive<RefreshTokenRequest>()
            call.respondText("Refresh token not implemented", status = HttpStatusCode.NotImplemented)
        }
    }
}