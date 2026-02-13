package com.example.commerce.payment

import com.example.commerce.TestcontainersConfiguration
import com.example.commerce.catalog.InventoryRepository
import com.example.commerce.order.persistence.OrderRepository
import com.example.commerce.payment.application.MockWebhookCommand
import com.example.commerce.payment.application.usecase.ProcessPaymentWebhookUseCase
import com.example.commerce.payment.domain.WebhookResultType
import com.example.commerce.payment.persistence.PaymentRepository
import com.example.commerce.payment.persistence.PaymentWebhookEventRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.UUID
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class PaymentFlowTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Autowired
    private lateinit var paymentWebhookEventRepository: PaymentWebhookEventRepository

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var processPaymentWebhookUseCase: ProcessPaymentWebhookUseCase

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

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
    fun `주문 생성 후 결제 생성 시 order는 PAYMENT_PENDING, payment는 CREATED이다`() {
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
            .andExpect(jsonPath("$.data.paymentId").isNumber)
            .andExpect(jsonPath("$.data.orderId").value(orderId))
            .andExpect(jsonPath("$.data.status").value("CREATED"))
            .andExpect(jsonPath("$.data.amount").value(35000))

        val order = orderRepository.findById(orderId).orElseThrow()
        assertThat(order.status).isEqualTo(com.example.commerce.order.domain.OrderStatus.PAYMENT_PENDING)
        val payment = paymentRepository.findByOrderId(orderId).single()
        assertThat(payment.status).isEqualTo(com.example.commerce.payment.domain.PaymentStatus.CREATED)
    }

    @Test
    fun `웹훅 AUTHORIZED 시 order는 PAID, payment는 AUTHORIZED이고 재고가 확정 차감된다`() {
        val orderId = createOrderAndPayment()
        val paymentId = paymentRepository.findByOrderId(orderId).single().id

        mockMvc.perform(
            post("/api/v1/payments/webhooks/mock")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider":"MOCK","providerEventId":"evt_approved_1","providerPaymentId":"pay_1","paymentId":$paymentId,"result":"AUTHORIZED"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.paymentId").value(paymentId))
            .andExpect(jsonPath("$.data.status").value("AUTHORIZED"))
            .andExpect(jsonPath("$.data.orderStatus").value("PAID"))

        val order = orderRepository.findById(orderId).orElseThrow()
        assertThat(order.status).isEqualTo(com.example.commerce.order.domain.OrderStatus.PAID)
        val payment = paymentRepository.findById(paymentId).orElseThrow()
        assertThat(payment.status).isEqualTo(com.example.commerce.payment.domain.PaymentStatus.AUTHORIZED)

        val inv1 = inventoryRepository.findById(1L).orElseThrow()
        assertThat(inv1.availableQty).isEqualTo(98)
        assertThat(inv1.reservedQty).isEqualTo(0)
        val inv2 = inventoryRepository.findById(2L).orElseThrow()
        assertThat(inv2.availableQty).isEqualTo(49)
        assertThat(inv2.reservedQty).isEqualTo(0)
    }

    @Test
    fun `같은 providerEventId로 웹훅을 두 번 호출하면 두 번째는 200이고 상태와 재고가 중복 반영되지 않는다`() {
        val orderId = createOrderAndPayment()
        val paymentId = paymentRepository.findByOrderId(orderId).single().id
        val body = """{"provider":"MOCK","providerEventId":"evt_idem_1","providerPaymentId":"pay_1","paymentId":$paymentId,"result":"AUTHORIZED"}"""

        mockMvc.perform(
            post("/api/v1/payments/webhooks/mock")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("AUTHORIZED"))

        mockMvc.perform(
            post("/api/v1/payments/webhooks/mock")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("AUTHORIZED"))

        val eventsForId = paymentWebhookEventRepository.findAll().filter { it.providerEventId == "evt_idem_1" }
        assertThat(eventsForId).hasSize(1)
        val inv1 = inventoryRepository.findById(1L).orElseThrow()
        assertThat(inv1.availableQty).isEqualTo(98)
        assertThat(inv1.reservedQty).isEqualTo(0)
    }

    @Test
    fun `웹훅 FAILED 시 order는 PAYMENT_FAILED, payment는 FAILED이고 예약만 해제된다`() {
        val orderId = createOrderAndPayment()
        val paymentId = paymentRepository.findByOrderId(orderId).single().id
        val inv1Before = inventoryRepository.findById(1L).orElseThrow()

        mockMvc.perform(
            post("/api/v1/payments/webhooks/mock")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider":"MOCK","providerEventId":"evt_fail_1","providerPaymentId":"pay_1","paymentId":$paymentId,"result":"FAILED"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.orderStatus").value("PAYMENT_FAILED"))

        val order = orderRepository.findById(orderId).orElseThrow()
        assertThat(order.status).isEqualTo(com.example.commerce.order.domain.OrderStatus.PAYMENT_FAILED)
        val payment = paymentRepository.findById(paymentId).orElseThrow()
        assertThat(payment.status).isEqualTo(com.example.commerce.payment.domain.PaymentStatus.FAILED)

        val inv1After = inventoryRepository.findById(1L).orElseThrow()
        assertThat(inv1After.availableQty).isEqualTo(inv1Before.availableQty)
        assertThat(inv1After.reservedQty).isEqualTo(0)
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `동일 providerEventId로 동시에 웹훅을 호출해도 상태와 재고 반영은 1회만 된다`() {
        val orderId = createOrderAndPayment()
        val paymentId = paymentRepository.findByOrderId(orderId).single().id
        val eventId = "evt_concurrent_${UUID.randomUUID()}"
        val command = MockWebhookCommand(
            provider = "MOCK",
            providerEventId = eventId,
            providerPaymentId = "pay_c1",
            paymentId = paymentId,
            result = WebhookResultType.AUTHORIZED,
        )
        val threadCount = 10
        val results = AtomicInteger(0)
        val exceptions = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        repeat(threadCount) {
            executor.submit {
                latch.countDown()
                latch.await()
                try {
                    processPaymentWebhookUseCase.execute(command)
                    results.incrementAndGet()
                } catch (e: Exception) {
                    exceptions.add(e)
                }
            }
        }
        executor.shutdown()
        assertThat(executor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue()

        if (exceptions.isNotEmpty()) {
            val first = exceptions.first()
            throw AssertionError("${exceptions.size}개 스레드에서 예외 발생. 첫 예외: ${first.message}", first)
        }
        assertThat(results.get()).describedAs("모든 UseCase 호출이 예외 없이 완료").isEqualTo(threadCount)

        val (eventCount, orderStatus, invQty) = transactionTemplate.execute {
            entityManager.clear()
            val evs = paymentWebhookEventRepository.findAll().filter { it.providerEventId == eventId }
            val ord = orderRepository.findById(orderId).orElseThrow()
            val inv1 = inventoryRepository.findById(1L).orElseThrow()
            Triple(evs.size, ord.status, inv1.availableQty to inv1.reservedQty)
        } ?: Triple(0, com.example.commerce.order.domain.OrderStatus.PAYMENT_PENDING, 100 to 0)

        assertThat(eventCount).describedAs("동일 이벤트는 1건만 저장").isEqualTo(1)
        assertThat(orderStatus).describedAs("결제 처리 후 주문은 PAID").isEqualTo(com.example.commerce.order.domain.OrderStatus.PAID)
        assertThat(invQty.first).describedAs("availableQty").isEqualTo(98)
        assertThat(invQty.second).describedAs("reservedQty").isEqualTo(0)
    }

    @Test
    fun `같은 providerEventId에 다른 paymentId로 두 번째 요청이 와도 첫 번째 결과만 유효하고 추가 반영되지 않는다`() {
        val orderId1 = createOrderAndPayment()
        val paymentId1 = paymentRepository.findByOrderId(orderId1).single().id
        val orderId2 = createOrderAndPayment()
        val paymentId2 = paymentRepository.findByOrderId(orderId2).single().id

        mockMvc.perform(
            post("/api/v1/payments/webhooks/mock")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider":"MOCK","providerEventId":"evt_same_id","providerPaymentId":"pay_1","paymentId":$paymentId1,"result":"AUTHORIZED"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.paymentId").value(paymentId1))
            .andExpect(jsonPath("$.data.orderStatus").value("PAID"))

        mockMvc.perform(
            post("/api/v1/payments/webhooks/mock")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider":"MOCK","providerEventId":"evt_same_id","providerPaymentId":"pay_2","paymentId":$paymentId2,"result":"AUTHORIZED"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.paymentId").value(paymentId1))
            .andExpect(jsonPath("$.data.orderStatus").value("PAID"))

        val events = paymentWebhookEventRepository.findAll().filter { it.providerEventId == "evt_same_id" }
        assertThat(events).hasSize(1)
        assertThat(events.single().paymentId).isEqualTo(paymentId1)

        assertThat(orderRepository.findById(orderId1).orElseThrow().status).isEqualTo(com.example.commerce.order.domain.OrderStatus.PAID)
        assertThat(orderRepository.findById(orderId2).orElseThrow().status).isEqualTo(com.example.commerce.order.domain.OrderStatus.PAYMENT_PENDING)

        val inv1 = inventoryRepository.findById(1L).orElseThrow()
        assertThat(inv1.availableQty).isEqualTo(98)
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
