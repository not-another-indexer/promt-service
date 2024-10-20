@file:OptIn(ExperimentalUuidApi::class)

package nsu.client

import cloudberry.*
import cloudberry.CloudberryStorageOuterClass.Empty
import cloudberry.CloudberryStorageOuterClass.FindResponse
import com.google.protobuf.ByteString
import io.grpc.ManagedChannel
import kotlinx.coroutines.flow.flow
import nsu.nai.core.Parameter
import java.io.InputStream
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
            this.bucketUuid = bucketUuid.toString()
        }
    )

    /**
     * Удаляет корзинку по ее UUID
     * @param bucketUuid UUID корзинки
     * @return ответ на запрос удаления корзинки
     */
    suspend fun destroyBucket(bucketUuid: Uuid): Empty = stub.destroyBucket(
        destroyBucketRequest {
            this.bucketUuid = bucketUuid.toString()
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
            this.query = query
            this.bucketUuid = bucketUuid.toString()
            this.parameters.addAll(
                parameters.map { (key, value) ->
                    coefficient {
                        this.parameter = CoefficientType.valueOf(key.name)
                        this.value = value
                    }
                }
            )
            this.count = count
        }
    )

    suspend fun putEntry(
        contentUuid: Uuid,
        bucketUuid: Uuid,
        extension: String,
        description: String,
        content: InputStream
    ): Empty = stub.putEntry(
        flow {
            // Сначала отправляем метаданные записи
            emit(
                putEntryRequest {
                    metadata = contentMetadata {
                        this.contentUuid = contentUuid.toString()
                        this.bucketUuid = bucketUuid.toString()
                        this.extension = extension
                        this.description = description
                    }
                }
            )

            // Буфер для чтения данных по частям (64 КБ)
            val buffer = ByteArray(64 * 1024)

            generateSequence { content.read(buffer).takeIf { it != -1 } }
                .forEach { bytesRead ->
                    emit(
                        putEntryRequest {
                            chunkData = ByteString.copyFrom(
                                buffer,
                                0,
                                bytesRead
                            )
                        }
                    )
                }
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
            this.contentUuid = contentUuid.toString()
            this.bucketUuid = bucketUuid.toString()
        }
    )
}