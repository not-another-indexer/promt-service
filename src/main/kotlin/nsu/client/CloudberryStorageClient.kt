package nsu.client

import cloudberry.*
import cloudberry.CloudberryStorageOuterClass.Empty
import cloudberry.CloudberryStorageOuterClass.FindResponse
import com.google.protobuf.ByteString
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.ManagedChannel
import nsu.nai.core.Parameter
import java.util.*
import cloudberry.CloudberryStorageOuterClass.Parameter as CoefficientType

/**
 * Клиент для взаимодействия с CloudberryStorage через gRPC
 * @param channel gRPC канал для подключения
 */
class CloudberryStorageClient(channel: ManagedChannel) {
    private val logger = KotlinLogging.logger {}
    private val stub = CloudberryStorageGrpcKt.CloudberryStorageCoroutineStub(channel)

    /**
     * Инициализирует новую корзинку в хранилище
     * @param bucketUUID UUID корзинки
     * @return ответ на запрос инициализации корзинки
     */
    suspend fun initBucket(bucketUUID: UUID): Empty {
        logger.info { "initBucket[bucketUUID=$bucketUUID]" }
        return stub.initBucket(
            initBucketRequest {
                this.pBucketUUID = bucketUUID.toString()
            }
        )
    }

    /**
     * Удаляет корзинку по ее UUID
     * @param bucketUUID UUID корзинки
     * @return ответ на запрос удаления корзинки
     */
    suspend fun destroyBucket(bucketUUID: UUID): Empty {
        logger.info { "destroyBucket[bucketUUID=$bucketUUID]" }
        val response = stub.destroyBucket(
            destroyBucketRequest {
                this.pBucketUUID = bucketUUID.toString()
            }
        )
        logger.info { "destroyBucket response: $response" }
        return response
    }

    /**
     * Ищет контент в корзинке по текстовому запросу
     * @param query строка с описанием контента
     * @param bucketUUID UUID корзинки, в которой выполняется поиск
     * @param parameters параметры поиска с весовыми коэффициентами
     * @param count количество результатов, которое необходимо получить
     * @return ответ на запрос поиска
     */
    suspend fun find(
        query: String,
        bucketUUID: UUID,
        parameters: Map<Parameter, Double>,
        count: Long
    ): FindResponse {
        logger.info { "find[query=$query, bucketUUID=$bucketUUID, parameters=$parameters, count=$count]" }
        val response = stub.find(
            findRequest {
                this.pQuery = query
                this.pBucketUUID = bucketUUID.toString()
                this.pParameters.addAll(
                    parameters.map { (key, value) ->
                        coefficient {
                            this.pParameter = CoefficientType.valueOf(key.name)
                            this.pValue = value
                        }
                    }
                )
                this.pCount = count
            }
        )
        logger.info { "find response: $response" }
        return response
    }

    suspend fun putEntry(
        contentUUID: UUID,
        bucketUUID: UUID,
        extension: String,
        description: String,
        content: ByteArray
    ): Empty {
        logger.info { "putEntry[contentUUID=$contentUUID, bucketUUID=$bucketUUID, extension=$extension, description=$description]" }
        val response = stub.putEntry(
            putEntryRequest {
                pMetadata = contentMetadata {
                    this.pContentUUID = contentUUID.toString()
                    this.pBucketUUID = bucketUUID.toString()
                    this.pExtension = extension
                    this.pDescription = description
                }
                pData = ByteString.copyFrom(content)
            }
        )
        logger.info { "putEntry response: $response" }
        return response
    }

    /**
     * Удаляет запись по UUID контента и корзинки
     * @param contentUUID UUID контента
     * @param bucketUUID UUID корзинки
     * @return ответ на запрос удаления записи
     */
    suspend fun removeEntry(contentUUID: UUID, bucketUUID: UUID): Empty {
        logger.info { "removeEntry[contentUUID=$contentUUID, bucketUUID=$bucketUUID]" }
        val response = stub.removeEntry(
            removeEntryRequest {
                this.pContentUUID = contentUUID.toString()
                this.pBucketUUID = bucketUUID.toString()
            }
        )
        logger.info { "removeEntry response: $response" }
        return response
    }
}