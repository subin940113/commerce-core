package com.example.commerce.order

import com.example.commerce.TestcontainersConfiguration
import com.example.commerce.order.application.CreateOrderCommand
import com.example.commerce.order.application.OrderItemCommand
import com.example.commerce.order.application.usecase.CreateOrderUseCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderConcurrencyTest {

    @Autowired
    private lateinit var createOrderUseCase: CreateOrderUseCase

    @Autowired
    private lateinit var inventoryRepository: com.example.commerce.catalog.InventoryRepository

    @BeforeEach
    fun setUp() {
        val inventory = inventoryRepository.findById(1L).orElseThrow()
        inventory.availableQty = 10
        inventory.reservedQty = 0
        inventoryRepository.saveAndFlush(inventory)
    }

    @Test
    fun `동시에 많이 주문해도 재고를 넘어서 예약되지 않는다`() {
        val threadCount = 20
        val successCount = AtomicInteger(0)
        val errors = ConcurrentLinkedQueue<Throwable>()
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)
        val command = CreateOrderCommand(1L, listOf(OrderItemCommand(1L, 1)))

        repeat(threadCount) {
            executor.submit {
                try {
                    latch.countDown()
                    latch.await()
                    createOrderUseCase.execute(command)
                    successCount.incrementAndGet()
                } catch (e: Throwable) {
                    errors.add(e)
                }
            }
        }

        executor.shutdown()
        assertThat(executor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue()

        assertThat(successCount.get())
            .describedAs("성공 주문 수는 정확히 10 (초과 예약/과소 예약 없음)")
            .isEqualTo(10)
        assertThat(errors.size + successCount.get()).isEqualTo(20)

        val inventory = inventoryRepository.findById(1L).orElseThrow()
        assertThat(inventory.reservedQty)
            .describedAs("reserved_qty는 성공한 주문 수와 정확히 일치")
            .isEqualTo(successCount.get())
        assertThat(inventory.availableQty).isEqualTo(10)
    }

    @Test
    fun `동시에 주문해도 수량 기준으로 재고 한도를 넘지 않는다 (qty=2)`() {
        val threadCount = 20
        val successCount = AtomicInteger(0)
        val errors = ConcurrentLinkedQueue<Throwable>()
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)
        val command = CreateOrderCommand(1L, listOf(OrderItemCommand(1L, 2)))

        repeat(threadCount) {
            executor.submit {
                try {
                    latch.countDown()
                    latch.await()
                    createOrderUseCase.execute(command)
                    successCount.incrementAndGet()
                } catch (e: Throwable) {
                    errors.add(e)
                }
            }
        }

        executor.shutdown()
        assertThat(executor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue()

        assertThat(successCount.get())
            .describedAs("qty=2일 때 성공 건수는 정확히 5 (10/2)")
            .isEqualTo(5)
        assertThat(errors.size + successCount.get()).isEqualTo(20)

        val inventory = inventoryRepository.findById(1L).orElseThrow()
        assertThat(inventory.reservedQty)
            .describedAs("reserved_qty는 10")
            .isEqualTo(10)
        assertThat(inventory.availableQty).isEqualTo(10)
    }
}
