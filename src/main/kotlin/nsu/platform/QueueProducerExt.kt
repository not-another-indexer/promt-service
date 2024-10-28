package nsu.platform

import ru.yoomoney.tech.dbqueue.api.EnqueueParams
import ru.yoomoney.tech.dbqueue.api.QueueProducer

fun <PayloadT : Any> QueueProducer<PayloadT>.enqueue(payload: PayloadT) {
    enqueue(EnqueueParams.create(payload))
}