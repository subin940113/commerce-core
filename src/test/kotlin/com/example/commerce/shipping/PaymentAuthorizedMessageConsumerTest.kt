package com.example.commerce.shipping

import com.example.commerce.TestcontainersConfiguration
import com.example.commerce.common.messaging.RabbitMQConstants
import com.example.commerce.order.domain.Order
import com.example.commerce.order.domain.OrderStatus
import com.example.commerce.order.persistence.OrderRepository
import com.example.commerce.shipping.persistence.ShippingRepository
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.util.concurrent.TimeUnit.SECONDS

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class PaymentAuthorizedMessageConsumerTest {

    @Autowired
    private lateinit var rabbitTemplate: RabbitTemplate

    @Autowired
    private lateinit var shippingRepository: ShippingRepository

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Test
    fun `메시지 수신 시 배송이 생성된다`() {
        val orderId = orderRepository.save(
            Order(
                userId = 1L,
                status = OrderStatus.CREATED,
                totalAmount = 0L,
                shippingFee = 0L,
                discountAmount = 0L,
                payableAmount = 0L,
            ),
        ).id
        val payload = mapOf(
            "orderId" to orderId,
            "paymentId" to 999L,
            "eventVersion" to 1,
        )

        rabbitTemplate.convertAndSend(
            RabbitMQConstants.EXCHANGE_EVENTS,
            RabbitMQConstants.ROUTING_KEY_PAYMENT_AUTHORIZED_V1,
            payload,
        )

        await().atMost(10, SECONDS).until { shippingRepository.countByOrderId(orderId) == 1L }
        assertThat(shippingRepository.countByOrderId(orderId)).isEqualTo(1L)
    }
}
