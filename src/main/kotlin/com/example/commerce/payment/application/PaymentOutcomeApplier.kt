package com.example.commerce.payment.application

import com.example.commerce.catalog.InventoryRepository
import com.example.commerce.common.outbox.OutboxAggregateType
import com.example.commerce.common.outbox.OutboxEvent
import com.example.commerce.common.outbox.OutboxEventType
import com.example.commerce.common.outbox.OutboxEventRepository
import com.example.commerce.common.outbox.OutboxStatus
import com.example.commerce.order.domain.Order
import com.example.commerce.order.domain.OrderStatus
import com.example.commerce.order.domain.exception.DataInconsistencyException
import com.example.commerce.order.domain.exception.InventoryNotFoundException
import com.example.commerce.payment.domain.Payment
import com.example.commerce.payment.domain.PaymentStatus
import org.springframework.stereotype.Component

/**
 * 결제 승인/실패 시 상태·재고·Outbox 적용. 웹훅과 승인 API 양쪽에서 공통 사용.
 */
@Component
class PaymentOutcomeApplier(
    private val inventoryRepository: InventoryRepository,
    private val outboxEventRepository: OutboxEventRepository,
) {

    fun applyAuthorized(payment: Payment, order: Order, providerPaymentId: String?) {
        payment.status = PaymentStatus.AUTHORIZED
        payment.providerPaymentId = providerPaymentId
        order.status = OrderStatus.PAID
        confirmInventory(order)
        val occurredAt = java.time.Instant.now()
        outboxEventRepository.save(
            OutboxEvent(
                eventType = OutboxEventType.PAYMENT_AUTHORIZED,
                aggregateType = OutboxAggregateType.PAYMENT,
                aggregateId = payment.id,
                payload = mapOf(
                    "eventVersion" to 1,
                    "occurredAt" to occurredAt.toString(),
                    "paymentId" to payment.id,
                    "orderId" to order.id,
                    "amount" to payment.amount,
                    "authorizedAt" to occurredAt.toString(),
                    "providerPaymentId" to (providerPaymentId ?: ""),
                ),
                status = OutboxStatus.PENDING,
            ),
        )
    }

    fun applyFailed(payment: Payment, order: Order, providerPaymentId: String?) {
        payment.status = PaymentStatus.FAILED
        payment.providerPaymentId = providerPaymentId
        order.status = OrderStatus.PAYMENT_FAILED
        releaseReservation(order)
    }

    private fun confirmInventory(order: Order) {
        val items = order.items.sortedBy { it.productId }
        for (item in items) {
            val inv = inventoryRepository.findByProductIdForUpdate(item.productId)
                ?: throw InventoryNotFoundException(item.productId)
            if (inv.reservedQty < item.qty) {
                throw DataInconsistencyException(
                    "Insufficient reserved qty for productId=${item.productId}: reserved=${inv.reservedQty}, required=${item.qty}",
                )
            }
            inv.availableQty -= item.qty
            inv.reservedQty -= item.qty
        }
    }

    private fun releaseReservation(order: Order) {
        val items = order.items.sortedBy { it.productId }
        for (item in items) {
            val inv = inventoryRepository.findByProductIdForUpdate(item.productId)
                ?: throw InventoryNotFoundException(item.productId)
            if (inv.reservedQty < item.qty) {
                throw DataInconsistencyException(
                    "Insufficient reserved qty for release productId=${item.productId}: reserved=${inv.reservedQty}, requested=${item.qty}",
                )
            }
            inv.reservedQty -= item.qty
        }
    }
}
