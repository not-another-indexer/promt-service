@file:OptIn(ExperimentalUuidApi::class)

package nsu.client

import CloudberryStorageOuterClass.*
import com.google.protobuf.ByteString
import io.grpc.ManagedChannel
import kotlinx.coroutines.flow.flow
import nsu.nai.core.Parameter
import java.io.InputStream
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import CloudberryStorageOuterClass.Parameter as CoefficientType

/**
 * Клиент для взаимодействия с CloudberryStorage через gRPC
 * @param channel gRPC канал для подключения
 */
class CloudberryStorageClient(channel: ManagedChannel) {

    // gRPC-stub, который используется для взаимодействия с сервером через переданный канал
    private val stub = CloudberryStorageGrpcKt.CloudberryStorageCoroutineStub(channel)

    /**
     * Инициализирует новую корзинку в хранилище
     * @param bucketUuid UUID корзинки
     * @return ответ на запрос инициализации корзинки
     */
    suspend fun initBucket(bucketUuid: Uuid): InitBucketResponse = stub.initBucket(
        InitBucketRequest.newBuilder()
            .setBucketUuid(bucketUuid.toString())
            .build()
    )

    /**
     * Удаляет корзинку по ее UUID
     * @param bucketUuid UUID корзинки
     * @return ответ на запрос удаления корзинки
     */
    suspend fun destroyBucket(bucketUuid: Uuid): DestroyBucketResponse = stub.destroyBucket(
        DestroyBucketRequest.newBuilder()
            .setBucketUuid(bucketUuid.toString())
            .build()
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
        FindRequest.newBuilder()
            .setQuery(query)
            .setBucketUuid(bucketUuid.toString())
            .addAllParameters(
                parameters.map { (key, value) ->
                    Coefficient.newBuilder()
                        .setParameter(CoefficientType.valueOf(key.name))
                        .setValue(value)
                        .build()
                }
            )
            .setCount(count)
            .build()
    )

    suspend fun putEntry(
        contentUuid: Uuid,
        bucketUuid: Uuid,
        extension: String,
        description: String,
        content: InputStream
    ): PutEntryResponse = stub.putEntry(
        flow {
            // Сначала отправляем метаданные записи
            emit(
                PutEntryRequest.newBuilder()
                    .setMetadata(
                        ContentMetadata.newBuilder()
                            .setContentUuid(contentUuid.toString())
                            .setBucketUuid(bucketUuid.toString())
                            .setExtension(extension)
                            .setDescription(description)
                            .build()
                    )
                    .build()
            )

            // Буфер для чтения данных по частям (64 КБ)
            val buffer = ByteArray(64 * 1024)

            generateSequence {
                content.read(buffer).takeIf { it != -1 }
            }
                .forEach { bytesRead ->
                    emit(
                        PutEntryRequest.newBuilder()
                            .setChunkData(
                                ByteString.copyFrom(
                                    buffer,
                                    0,
                                    bytesRead
                                )
                            )
                            .build()
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
    suspend fun removeEntry(contentUuid: Uuid, bucketUuid: Uuid): RemoveEntryResponse = stub.removeEntry(
        RemoveEntryRequest.newBuilder()
            .setContentUuid(contentUuid.toString())
            .setBucketUuid(bucketUuid.toString())
            .build()
    )
}