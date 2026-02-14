package com.example.commerce.shipping.application.consumer

import com.example.commerce.common.messaging.RabbitMQConstants
import com.example.commerce.shipping.application.CreateShipmentCommand
import com.example.commerce.shipping.application.usecase.CreateShipmentUseCase
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/**
 * PAYMENT_AUTHORIZED 메시지를 소비해 배송을 생성한다.
 * at-least-once이므로 중복 수신 가능. CreateShipmentUseCase가 order_id 기준 멱등을 보장한다.
 * 예외 시 nack → 재전송, 반복 실패 시 DLQ로 이동한다.
 */
@Component
class PaymentAuthorizedMessageConsumer(
    private val createShipmentUseCase: CreateShipmentUseCase,
) {

    @RabbitListener(queues = [RabbitMQConstants.QUEUE_SHIPPING_PAYMENT_AUTHORIZED])
    fun handle(payload: Map<String, Any?>) {
        val orderId = (payload["orderId"] as? Number)?.toLong()
            ?: throw IllegalArgumentException("PAYMENT_AUTHORIZED payload must contain orderId")
        createShipmentUseCase.execute(CreateShipmentCommand(orderId))
    }
}
