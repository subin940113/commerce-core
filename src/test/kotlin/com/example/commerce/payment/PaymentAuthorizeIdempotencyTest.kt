package com.example.commerce.payment

import com.example.commerce.TestcontainersConfiguration
import com.example.commerce.common.outbox.OutboxPublisher
import com.example.commerce.catalog.InventoryRepository
import com.example.commerce.payment.persistence.PaymentRepository
import com.example.commerce.shipping.persistence.ShippingRepository
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.concurrent.TimeUnit.SECONDS

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class PaymentAuthorizeIdempotencyTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Autowired
    private lateinit var outboxPublisher: OutboxPublisher

    @Autowired
    private lateinit var shippingRepository: ShippingRepository

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

    @BeforeEach
    fun setUp() {
        inventoryRepository.findById(1L).ifPresent { inv ->
            inv.availableQty = 100
            inv.reservedQty = 0
            inventoryRepository.saveAndFlush(inv)
        }
        inventoryRepository.findById(2L).ifPresent { inv ->
            inv.availableQty = 50
            inv.reservedQty = 0
            inventoryRepository.saveAndFlush(inv)
        }
    }

    @Test
    fun `같은 Idempotency-Key로 승인 API 2번 호출해도 결과와 상태가 1번만 반영되고 재고·배송도 1회만 처리된다`() {
        val orderId = createOrderAndPayment()
        val paymentId = paymentRepository.findByOrderId(orderId).single().id
        val idempotencyKey = "idem-authorize-1"
        val body = """{"result":"AUTHORIZED","providerPaymentId":"pay_a1"}"""

        mockMvc.perform(
            post("/api/v1/payments/$paymentId/authorize")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("AUTHORIZED"))
            .andExpect(jsonPath("$.data.orderStatus").value("PAID"))

        outboxPublisher.publishPending()
        await().atMost(10, SECONDS).until { shippingRepository.findByOrderId(orderId) != null }
        assertThat(shippingRepository.countByOrderId(orderId)).isEqualTo(1L)

        mockMvc.perform(
            post("/api/v1/payments/$paymentId/authorize")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.paymentId").value(paymentId))
            .andExpect(jsonPath("$.data.status").value("AUTHORIZED"))
            .andExpect(jsonPath("$.data.orderStatus").value("PAID"))

        assertThat(shippingRepository.countByOrderId(orderId)).describedAs("재시도 시 배송 1건만").isEqualTo(1L)
    }

    @Test
    fun `Idempotency-Key 헤더 누락 시 400을 반환한다`() {
        val orderId = createOrderAndPayment()
        val paymentId = paymentRepository.findByOrderId(orderId).single().id

        mockMvc.perform(
            post("/api/v1/payments/$paymentId/authorize")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"result":"AUTHORIZED"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
    }

    @Test
    fun `같은 Idempotency-Key로 다른 payload를 보내면 409를 반환한다`() {
        val orderId = createOrderAndPayment()
        val paymentId = paymentRepository.findByOrderId(orderId).single().id
        val idempotencyKey = "idem-authorize-2"

        mockMvc.perform(
            post("/api/v1/payments/$paymentId/authorize")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"result":"AUTHORIZED","providerPaymentId":"pay_a2"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("AUTHORIZED"))

        mockMvc.perform(
            post("/api/v1/payments/$paymentId/authorize")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"result":"FAILED"}"""),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_CONFLICT"))
    }

    private fun createOrderAndPayment(): Long {
        val orderRes = mockMvc.perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":1,"items":[{"productId":1,"qty":2},{"productId":2,"qty":1}]}"""),
        )
            .andExpect(status().isCreated)
            .andReturn()
        val orderId = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            .readTree(orderRes.response.contentAsString).get("data").get("orderId").asLong()
        mockMvc.perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"orderId":$orderId}"""),
        )
            .andExpect(status().isCreated)
        return orderId
    }
}
