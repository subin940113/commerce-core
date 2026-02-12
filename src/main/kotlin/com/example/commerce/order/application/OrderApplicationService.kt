package com.example.commerce.order.application

import com.example.commerce.catalog.InventoryRepository
import com.example.commerce.catalog.ProductRepository
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
 * - DDD Lite: 도메인 엔티티는 JPA Entity 그대로 사용.
 * - 가용 예약 수량 = available_qty - reserved_qty.
 * - 재고 락은 productId 오름차순으로 고정하여 데드락 방지.
 */
@Service
class OrderApplicationService(
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository,
    private val orderRepository: OrderRepository,
) {

    @Transactional
    fun createOrder(command: CreateOrderCommand): CreateOrderResult {
        // 1. 입력 정규화 (동일 productId 합산)
        // 2. productId 오름차순 정렬
        val normalized = normalizeItems(command.items)

        // 3. 재고 락 획득 (FOR UPDATE) + 4. 재고 예약 처리 (dirty checking)
        val lineItems = mutableListOf<OrderItem>()
        var totalAmount = 0L

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

        // 5. Order 엔티티 생성
        // 6. OrderItem 추가
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

        // 7. 저장
        val saved = orderRepository.save(order)

        // 8. Result 반환
        return CreateOrderResult(
            orderId = saved.id,
            status = saved.status,
            payableAmount = saved.payableAmount,
        )
    }

    /** 동일 productId 합산 후 productId 오름차순 정렬(락 순서 고정). */
    private fun normalizeItems(items: List<OrderItemCommand>): List<OrderItemCommand> =
        items
            .groupBy { it.productId }
            .map { (productId, list) -> OrderItemCommand(productId, list.sumOf { it.qty }) }
            .sortedBy { it.productId }
}
