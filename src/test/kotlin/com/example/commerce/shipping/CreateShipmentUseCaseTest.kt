package com.example.commerce.shipping

import com.example.commerce.TestcontainersConfiguration
import com.example.commerce.order.domain.Order
import com.example.commerce.order.domain.OrderStatus
import com.example.commerce.order.persistence.OrderRepository
import com.example.commerce.shipping.application.CreateShipmentCommand
import com.example.commerce.shipping.application.usecase.CreateShipmentUseCase
import com.example.commerce.shipping.domain.ShippingStatus
import com.example.commerce.shipping.persistence.ShippingRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@Transactional
class CreateShipmentUseCaseTest {

    @Autowired
    private lateinit var createShipmentUseCase: CreateShipmentUseCase

    @Autowired
    private lateinit var shippingRepository: ShippingRepository

    @Autowired
    private lateinit var orderRepository: OrderRepository

    private fun createOrder(): Long =
        orderRepository.save(
            Order(
                userId = 1L,
                status = OrderStatus.CREATED,
                totalAmount = 0L,
                shippingFee = 0L,
                discountAmount = 0L,
                payableAmount = 0L,
            ),
        ).id

    @Test
    fun `처음 호출 시 배송이 1건 생성된다`() {
        val orderId = createOrder()

        createShipmentUseCase.execute(CreateShipmentCommand(orderId))

        assertThat(shippingRepository.countByOrderId(orderId)).isEqualTo(1L)
        val shipping = shippingRepository.findByOrderId(orderId)
        assertThat(shipping).isNotNull
        assertThat(shipping!!.status).isEqualTo(ShippingStatus.CREATED)
    }

    @Test
    fun `같은 orderId로 두 번 호출해도 1건만 존재한다`() {
        val orderId = createOrder()

        createShipmentUseCase.execute(CreateShipmentCommand(orderId))
        createShipmentUseCase.execute(CreateShipmentCommand(orderId))

        assertThat(shippingRepository.countByOrderId(orderId)).isEqualTo(1L)
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `동시 호출해도 1건만 생성된다`() {
        val orderId = createOrder()
        val latch = CountDownLatch(2)
        val executor = Executors.newFixedThreadPool(2)

        repeat(2) {
            executor.submit {
                latch.countDown()
                latch.await()
                createShipmentUseCase.execute(CreateShipmentCommand(orderId))
            }
        }
        executor.shutdown()
        assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue()

        assertThat(shippingRepository.countByOrderId(orderId)).isEqualTo(1L)
    }
}
