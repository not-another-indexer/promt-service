package nsu.nai.dbqueue

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.yoomoney.tech.dbqueue.api.TaskPayloadTransformer

object PutEntryPayloadTransformer : TaskPayloadTransformer<PutEntryPayload> {
    override fun fromObject(payload: PutEntryPayload?): String {
        requireNotNull(payload)
        return Json.encodeToString(payload)
    }

    override fun toObject(payload: String?): PutEntryPayload {
        requireNotNull(payload)
        return Json.decodeFromString(payload)
    }

}

object RemoveEntryPayloadTransformer : TaskPayloadTransformer<RemoveEntryPayload> {
    override fun fromObject(payload: RemoveEntryPayload?): String {
        requireNotNull(payload)
        return Json.encodeToString(payload)
    }

    override fun toObject(payload: String?): RemoveEntryPayload {
        requireNotNull(payload)
        return Json.decodeFromString(payload)
    }
}

object InitIndexPayloadTransformer : TaskPayloadTransformer<InitIndexPayload> {
    override fun fromObject(payload: InitIndexPayload?): String {
        requireNotNull(payload)
        return Json.encodeToString(payload)
    }

    override fun toObject(payload: String?): InitIndexPayload {
        requireNotNull(payload)
        return Json.decodeFromString(payload)
    }
}

object DestroyIndexPayloadTransformer : TaskPayloadTransformer<DestroyIndexPayload> {
    override fun fromObject(payload: DestroyIndexPayload?): String {
        requireNotNull(payload)
        return Json.encodeToString(payload)
    }

    override fun toObject(payload: String?): DestroyIndexPayload {
        requireNotNull(payload)
        return Json.decodeFromString(payload)
    }
}
