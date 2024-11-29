package nsu

import io.grpc.*
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
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager

fun main() {
    // DB-QUEUE
    val producers = initDbQueue()

    // GRPC
    val authInterceptor = AuthInterceptor()

    val server: Server = ServerBuilder.forPort(Config.grpcPort)
        .addService(AuthServiceImpl(Config.connectionProvider))
        .addService(ServerInterceptors.intercept(GalleryServiceImpl(producers), authInterceptor))
        .addService(ServerInterceptors.intercept(MainServiceImpl(producers), authInterceptor))
        .build()
        .start()

    Database.connect(Config.connectionProvider)
    transaction {
        SchemaUtils.create(Users, Galleries, Images)
    }

    LoggerFactory.getLogger("Server").info("Server started on port ${server.port}")

    server.awaitTermination()
}

object Config {
    val dbUrl = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/nai_db"
    val dbUser = System.getenv("DB_USER") ?: "nai_user"
    val dbPassword = System.getenv("DB_PASSWORD") ?: "nai_password"
    val grpcPort = System.getenv("GRPC_PORT")?.toInt() ?: 8080
    private val cloudberryHost = System.getenv("CLOUDBERRY_HOST") ?: "176.123.160.174"
    private val cloudberryPort = System.getenv("CLOUDBERRY_PORT")?.toInt() ?: 8002

    val connectionProvider: () -> Connection = {
        DriverManager.getConnection(dbUrl, dbUser, dbPassword)
    }

    private val managedChannel: ManagedChannel = ManagedChannelBuilder
        .forAddress(cloudberryHost, cloudberryPort)
        .usePlaintext() // unsecured communication
        .build()

    val cloudberry = CloudberryStorageClient(managedChannel)
}