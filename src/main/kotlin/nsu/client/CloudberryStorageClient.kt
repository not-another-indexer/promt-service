@file:OptIn(ExperimentalUuidApi::class)

package nsu.client

import cloudberry.*
import cloudberry.CloudberryStorageOuterClass.Empty
import cloudberry.CloudberryStorageOuterClass.FindResponse
import com.google.protobuf.ByteString
import io.grpc.ManagedChannel
import nsu.nai.core.Parameter
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import cloudberry.CloudberryStorageOuterClass.Parameter as CoefficientType

/**
 * Клиент для взаимодействия с CloudberryStorage через gRPC
 * @param channel gRPC канал для подключения
 */
class CloudberryStorageClient(channel: ManagedChannel) {
    private val stub = CloudberryStorageGrpcKt.CloudberryStorageCoroutineStub(channel)

    /**
     * Инициализирует новую корзинку в хранилище
     * @param bucketUuid UUID корзинки
     * @return ответ на запрос инициализации корзинки
     */
    suspend fun initBucket(bucketUuid: Uuid): Empty = stub.initBucket(
        initBucketRequest {
            this.pBucketUuid = bucketUuid.toString()
        }
    )

    /**
     * Удаляет корзинку по ее UUID
     * @param bucketUuid UUID корзинки
     * @return ответ на запрос удаления корзинки
     */
    suspend fun destroyBucket(bucketUuid: Uuid): Empty = stub.destroyBucket(
        destroyBucketRequest {
            this.pBucketUuid = bucketUuid.toString()
        }
    )

    /**
     * Ищет контент в корзинке по текстовому запросу
     * @param query строка с описанием контента
     * @param bucketUuid UUID корзинки, в которой выполняется поиск
     * @param parameters параметры поиска с весовыми коэффициентами
     * @param count количество результатов, которое необходимо получить
     * @return ответ на запрос поиска
     */
    suspend fun find(
        query: String,
        bucketUuid: Uuid,
        parameters: Map<Parameter, Double>,
        count: Long
    ): FindResponse = stub.find(
        findRequest {
            this.pQuery = query
            this.pBucketUuid = bucketUuid.toString()
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

    suspend fun putEntry(
        contentUuid: Uuid,
        bucketUuid: Uuid,
        extension: String,
        description: String,
        content: ByteArray
    ): Empty = stub.putEntry(
        putEntryRequest {
            pMetadata = contentMetadata {
                this.pContentUuid = contentUuid.toString()
                this.pBucketUuid = bucketUuid.toString()
                this.pExtension = extension
                this.pDescription = description
            }
            pData = ByteString.copyFrom(content)
        }
    )

    /**
     * Удаляет запись по UUID контента и корзинки
     * @param contentUuid UUID контента
     * @param bucketUuid UUID корзинки
     * @return ответ на запрос удаления записи
     */
    suspend fun removeEntry(contentUuid: Uuid, bucketUuid: Uuid): Empty = stub.removeEntry(
        removeEntryRequest {
            this.pContentUuid = contentUuid.toString()
            this.pBucketUuid = bucketUuid.toString()
        }
    )
}