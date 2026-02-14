package com.example.commerce.order.application.usecase

import com.example.commerce.catalog.InventoryRepository
import com.example.commerce.catalog.ProductRepository
import com.example.commerce.order.application.CreateOrderCommand
import com.example.commerce.order.application.CreateOrderResult
import com.example.commerce.order.application.OrderItemCommand
import com.example.commerce.order.domain.Order
import com.example.commerce.order.domain.OrderItem
import com.example.commerce.order.domain.OrderStatus
import com.example.commerce.order.domain.exception.InsufficientStockException
import com.example.commerce.order.domain.exception.InventoryNotFoundException
import com.example.commerce.order.domain.exception.ProductNotFoundException
import com.example.commerce.order.persistence.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 주문 생성 유스케이스.
 * 동시 주문에서 초과 예약이 나지 않도록 재고 행을 FOR UPDATE로 잠근 뒤 검증·예약만 수행한다.
 * 락 순서를 productId 오름차순으로 고정해 데드락 가능성을 낮춘다.
 * 결제 전에는 available을 줄이지 않고 reserved로 홀드한다.
 * 결제 실패/만료 시 예약 해제만 하면 된다.
 */
@Service
class CreateOrderUseCase(
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository,
    private val orderRepository: OrderRepository,
) {

    @Transactional
    fun execute(command: CreateOrderCommand): CreateOrderResult {
        val normalized = normalizeItems(command.items)

        val lineItems = mutableListOf<OrderItem>()
        var totalAmount = 0L

        // productId 오름차순 락으로 데드락 가능성을 낮춤. 가용량은 available - reserved 기준.
        for (item in normalized) {
            val product = productRepository.findById(item.productId)
                .orElseThrow { ProductNotFoundException(item.productId) }
            val inventory = inventoryRepository.findByProductIdForUpdate(item.productId)
                ?: throw InventoryNotFoundException(item.productId)

            val availableToReserve = inventory.availableQty - inventory.reservedQty
            if (availableToReserve < item.qty) {
                throw InsufficientStockException(item.productId, availableToReserve, item.qty)
            }

            inventory.reservedQty += item.qty

            val lineAmount = product.price * item.qty
            totalAmount += lineAmount
            lineItems.add(
                OrderItem(
                    productId = product.id,
                    productNameSnapshot = product.name,
                    unitPriceSnapshot = product.price,
                    qty = item.qty,
                    lineAmount = lineAmount,
                ),
            )
        }

        val order = Order(
            userId = command.userId,
            status = OrderStatus.CREATED,
            totalAmount = totalAmount,
            shippingFee = 0L,
            discountAmount = 0L,
            payableAmount = totalAmount,
            currency = "KRW",
        )
        lineItems.forEach { order.addItem(it) }

        val saved = orderRepository.save(order)
        return CreateOrderResult(
            orderId = saved.id,
            status = saved.status,
            payableAmount = saved.payableAmount,
        )
    }

    /**
     * 동일 상품이 여러 번 들어올 수 있어 수량을 합산한다.
     * 이후 productId 오름차순으로 정렬해 재고 락을 항상 같은 순서로 잡도록 맞춘다.
     * 동시 주문 시 교착 가능성을 낮추기 위한 전처리다.
     */
    private fun normalizeItems(items: List<OrderItemCommand>): List<OrderItemCommand> =
        items
            .groupBy { it.productId }
            .map { (productId, list) -> OrderItemCommand(productId, list.sumOf { it.qty }) }
            .sortedBy { it.productId }
}
