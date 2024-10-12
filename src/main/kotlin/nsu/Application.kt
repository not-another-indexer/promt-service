package nsu

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import nsu.auth.configureAuth
import nsu.client.CloudberryStorageClient
import nsu.gallery.configureGallery
import nsu.plugins.*
import java.sql.Connection
import java.sql.DriverManager

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureRouting()
    configureAuth()
    configureGallery()
}

object Config {
    val connectionProvider: () -> Connection = {
        DriverManager.getConnection(
            "jdbc:postgresql://localhost:5432/nai_db",
            "nai_user",
            "nai_password"
        )
    }

    private val managedChannel: ManagedChannel = ManagedChannelBuilder
        .forAddress("localhost", 50051)
        .usePlaintext() // unsecured communication
        .build()

    val cloudberry = CloudberryStorageClient(managedChannel)
}