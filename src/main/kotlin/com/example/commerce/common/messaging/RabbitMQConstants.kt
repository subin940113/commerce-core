package com.example.commerce.common.messaging

/**
 * RabbitMQ Exchange / Queue / RoutingKey 계약.
 * 이벤트 버전은 routing key 또는 payload.eventVersion으로 표현한다.
 */
object RabbitMQConstants {

    const val EXCHANGE_EVENTS = "commerce.events"
    const val EXCHANGE_DLX = "commerce.events.dlx"

    /** PAYMENT_AUTHORIZED → shipping 큐 */
    const val ROUTING_KEY_PAYMENT_AUTHORIZED_V1 = "payment.authorized.v1"
    const val QUEUE_SHIPPING_PAYMENT_AUTHORIZED = "shipping.payment-authorized"
    const val QUEUE_SHIPPING_PAYMENT_AUTHORIZED_DLQ = "shipping.payment-authorized.dlq"
    const val ROUTING_KEY_SHIPPING_PAYMENT_AUTHORIZED_DLQ = "shipping.payment-authorized.dlq"
}

/** event_type 문자열을 RabbitMQ routing key로 매핑한다. */
fun String.toRoutingKey(): String? = when (this) {
    "PAYMENT_AUTHORIZED" -> RabbitMQConstants.ROUTING_KEY_PAYMENT_AUTHORIZED_V1
    else -> null
}
