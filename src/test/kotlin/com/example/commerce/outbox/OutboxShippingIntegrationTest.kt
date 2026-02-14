package com.example.commerce.outbox

import com.example.commerce.TestcontainersConfiguration
import com.example.commerce.common.messaging.RabbitMQConstants
import com.example.commerce.common.outbox.OutboxEventType
import com.example.commerce.common.outbox.OutboxPublisher
import com.example.commerce.common.outbox.OutboxStatus
import com.example.commerce.common.outbox.OutboxEventRepository
import com.example.commerce.payment.persistence.PaymentRepository
import com.example.commerce.shipping.domain.ShippingStatus
import com.example.commerce.shipping.persistence.ShippingRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class OutboxShippingIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Autowired
    private lateinit var outboxEventRepository: OutboxEventRepository

    @Autowired
    private lateinit var outboxPublisher: OutboxPublisher

    @Autowired
    private lateinit var shippingRepository: ShippingRepository

    @Autowired
    private lateinit var rabbitTemplate: RabbitTemplate

    @Autowired
    private lateinit var entityManager: EntityManager

    @BeforeEach
    fun setUp() {
        entityManager.clear()
    }

    @Test
    fun `결제 승인 시 Outbox에 PAYMENT_AUTHORIZED가 PENDING으로 1건 기록된다`() {
        val orderId = createOrderAndPayment()
        val paymentId = paymentRepository.findByOrderId(orderId).single().id

        mockMvc.perform(
            post("/api/v1/payments/webhooks/mock")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider":"MOCK","providerEventId":"evt_outbox_1","providerPaymentId":"pay_o1","paymentId":$paymentId,"result":"AUTHORIZED"}"""),
        ).andExpect(status().isOk)

        entityManager.clear()
        val events = outboxEventRepository.findAll().filter {
            it.eventType == OutboxEventType.PAYMENT_AUTHORIZED && it.status == OutboxStatus.PENDING
        }
        assertThat(events).hasSize(1)
        assertThat((events.single().payload["orderId"] as Number).toLong()).isEqualTo(orderId)
        assertThat((events.single().payload["paymentId"] as Number).toLong()).isEqualTo(paymentId)
    }

    @Test
    fun `퍼블리셔 처리 후 RabbitMQ로 발행되고 Consumer가 처리해 Shipping이 1건 생성된다`() {
        val orderId = createOrderAndPayment()
        val paymentId = paymentRepository.findByOrderId(orderId).single().id

        mockMvc.perform(
            post("/api/v1/payments/webhooks/mock")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider":"MOCK","providerEventId":"evt_outbox_2","providerPaymentId":"pay_o2","paymentId":$paymentId,"result":"AUTHORIZED"}"""),
        ).andExpect(status().isOk)

        assertThat(shippingRepository.findByOrderId(orderId)).isNull()
        outboxPublisher.publishPending()

        await().atMost(10, SECONDS).until { shippingRepository.findByOrderId(orderId) != null }
        entityManager.clear()
        val shipping = shippingRepository.findByOrderId(orderId)
        assertThat(shipping).isNotNull
        assertThat(shipping!!.orderId).isEqualTo(orderId)
        assertThat(shipping.status).isEqualTo(ShippingStatus.CREATED)
    }

    @Test
    fun `같은 메시지가 두 번 Consumer에 전달돼도 Shipping은 1건만 존재한다`() {
        val orderId = createOrderAndPayment()
        val paymentId = paymentRepository.findByOrderId(orderId).single().id

        mockMvc.perform(
            post("/api/v1/payments/webhooks/mock")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider":"MOCK","providerEventId":"evt_outbox_3","providerPaymentId":"pay_o3","paymentId":$paymentId,"result":"AUTHORIZED"}"""),
        ).andExpect(status().isOk)

        outboxPublisher.publishPending()
        await().atMost(10, SECONDS).until { shippingRepository.findByOrderId(orderId) != null }

        val payload = mapOf(
            "orderId" to orderId,
            "paymentId" to paymentId,
            "eventVersion" to 1,
        )
        rabbitTemplate.convertAndSend(
            RabbitMQConstants.EXCHANGE_EVENTS,
            RabbitMQConstants.ROUTING_KEY_PAYMENT_AUTHORIZED_V1,
            payload,
        )
        Thread.sleep(1500)

        entityManager.clear()
        val shipments = shippingRepository.findAll().filter { it.orderId == orderId }
        assertThat(shipments).describedAs("중복 메시지 처리 시에도 배송 1건만").hasSize(1)
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `publishPending을 두 인스턴스가 동시에 호출해도 Shipment는 1건만 생성된다`() {
        val orderId = createOrderAndPayment()
        val paymentId = paymentRepository.findByOrderId(orderId).single().id
        mockMvc.perform(
            post("/api/v1/payments/webhooks/mock")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider":"MOCK","providerEventId":"evt_concurrent_pub","providerPaymentId":"pay_cp","paymentId":$paymentId,"result":"AUTHORIZED"}"""),
        ).andExpect(status().isOk)

        val latch = CountDownLatch(2)
        val executor = Executors.newFixedThreadPool(2)
        repeat(2) {
            executor.submit {
                latch.countDown()
                latch.await()
                outboxPublisher.publishPending()
            }
        }
        executor.shutdown()
        assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue()

        await().atMost(10, SECONDS).until { shippingRepository.findByOrderId(orderId) != null }
        entityManager.clear()
        val shipments = shippingRepository.findAll().filter { it.orderId == orderId }
        assertThat(shipments).describedAs("다중 인스턴스 동시 publish 시에도 배송 1건만").hasSize(1)
    }

    private fun createOrderAndPayment(): Long {
        val orderRes = mockMvc.perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":1,"items":[{"productId":1,"qty":2},{"productId":2,"qty":1}]}"""),
        ).andExpect(status().isCreated).andReturn()
        val orderId = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            .readTree(orderRes.response.contentAsString).get("data").get("orderId").asLong()
        mockMvc.perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"orderId":$orderId}"""),
        ).andExpect(status().isCreated)
        return orderId
    }
}
