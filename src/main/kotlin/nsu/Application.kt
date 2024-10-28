package nsu

import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.*
import kotlinx.coroutines.runBlocking
import nsu.client.CloudberryStorageClient
import nsu.nai.core.table.gallery.Galleries
import nsu.nai.core.table.image.Images
import nsu.nai.core.table.user.Users
import nsu.nai.dbqueue.initDbQueue
import nsu.nai.interceptor.AuthInterceptor
import nsu.nai.port.AuthServiceImpl
import nsu.nai.port.GalleryServiceImpl
import nsu.nai.port.MainServiceImpl
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import java.sql.DriverManager

fun main() = runBlocking {
    // DB-QUEUE
    val producers = initDbQueue()

    // GRPC
    val authInterceptor = AuthInterceptor()

    val server: Server = ServerBuilder.forPort(8080)
        .addService(AuthServiceImpl(Config.connectionProvider))
        .addService(ServerInterceptors.intercept(GalleryServiceImpl(producers), authInterceptor))
        .addService(ServerInterceptors.intercept(MainServiceImpl(producers), authInterceptor))
        .build()
        .start()

    Database.connect(Config.connectionProvider)
    transaction {
        SchemaUtils.create(Users, Galleries, Images)
    }

    KotlinLogging.logger { }.info { "Server started on port ${server.port}" }

    server.awaitTermination()

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