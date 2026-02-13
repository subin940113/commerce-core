package com.example.commerce.payment.application.usecase

import com.example.commerce.catalog.InventoryRepository
import com.example.commerce.order.domain.OrderStatus
import com.example.commerce.order.domain.exception.InvalidOrderStateTransitionException
import com.example.commerce.payment.application.MockWebhookCommand
import com.example.commerce.payment.application.WebhookEventInsert
import com.example.commerce.payment.application.WebhookResult
import com.example.commerce.payment.domain.PaymentStatus
import com.example.commerce.payment.domain.PaymentWebhookEvent
import com.example.commerce.payment.domain.WebhookResultType
import com.example.commerce.payment.domain.exception.PaymentNotFoundException
import com.example.commerce.payment.persistence.PaymentRepository
import com.example.commerce.payment.persistence.PaymentWebhookEventRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProcessPaymentWebhookUseCase(
    private val paymentRepository: PaymentRepository,
    private val paymentWebhookEventRepository: PaymentWebhookEventRepository,
    private val webhookEventInsert: WebhookEventInsert,
    private val inventoryRepository: InventoryRepository,
) {

    @Transactional
    fun execute(command: MockWebhookCommand): WebhookResult {
        val event = PaymentWebhookEvent(
            provider = command.provider,
            providerEventId = command.providerEventId,
            providerPaymentId = command.providerPaymentId,
            paymentId = command.paymentId,
            payload = null,
        )

        try {
            webhookEventInsert.insertIfAbsent(event)
        } catch (e: Exception) {
            if (!isDuplicateKeyOrUniqueViolation(e)) throw e
            val existing = paymentWebhookEventRepository.findByProviderAndProviderEventId(command.provider, command.providerEventId)
                ?: throw e
            val paymentId = existing.paymentId ?: throw e
            val payment = paymentRepository.findById(paymentId).orElseThrow { PaymentNotFoundException(paymentId) }
            return WebhookResult(
                paymentId = payment.id,
                status = payment.status.name,
                orderStatus = payment.order!!.status.name,
            )
        }

        val payment = paymentRepository.findById(command.paymentId)
            .orElseThrow { PaymentNotFoundException(command.paymentId) }
        val order = payment.order ?: throw PaymentNotFoundException(command.paymentId)
        order.items.size

        if (order.status != OrderStatus.PAYMENT_PENDING) {
            throw InvalidOrderStateTransitionException("Order must be PAYMENT_PENDING for webhook, current=${order.status}")
        }

        when (command.result) {
            WebhookResultType.AUTHORIZED -> {
                payment.status = PaymentStatus.AUTHORIZED
                payment.providerPaymentId = command.providerPaymentId
                order.status = OrderStatus.PAID
                confirmInventory(order)
            }
            WebhookResultType.FAILED -> {
                payment.status = PaymentStatus.FAILED
                payment.providerPaymentId = command.providerPaymentId
                order.status = OrderStatus.PAYMENT_FAILED
                releaseReservation(order)
            }
        }

        return WebhookResult(
            paymentId = payment.id,
            status = payment.status.name,
            orderStatus = order.status.name,
        )
    }

    private fun confirmInventory(order: com.example.commerce.order.domain.Order) {
        val items = order.items.sortedBy { it.productId }
        for (item in items) {
            val inv = inventoryRepository.findByProductIdForUpdate(item.productId)
                ?: throw com.example.commerce.order.domain.exception.InventoryNotFoundException(item.productId)
            if (inv.reservedQty < item.qty) {
                throw com.example.commerce.order.domain.exception.DataInconsistencyException(
                    "Insufficient reserved qty for productId=${item.productId}: reserved=${inv.reservedQty}, required=${item.qty}",
                )
            }
            inv.availableQty -= item.qty
            inv.reservedQty -= item.qty
        }
    }

    private fun releaseReservation(order: com.example.commerce.order.domain.Order) {
        val items = order.items.sortedBy { it.productId }
        for (item in items) {
            val inv = inventoryRepository.findByProductIdForUpdate(item.productId)
                ?: throw com.example.commerce.order.domain.exception.InventoryNotFoundException(item.productId)
            if (inv.reservedQty < item.qty) {
                throw com.example.commerce.order.domain.exception.DataInconsistencyException(
                    "Insufficient reserved qty for release productId=${item.productId}: reserved=${inv.reservedQty}, requested=${item.qty}",
                )
            }
            inv.reservedQty -= item.qty
        }
    }

    private fun isDuplicateKeyOrUniqueViolation(e: Exception): Boolean {
        if (e is DuplicateKeyException || e is DataIntegrityViolationException) return true
        var cause: Throwable? = e.cause
        while (cause != null) {
            if (cause is DuplicateKeyException || cause is DataIntegrityViolationException) return true
            val msg = cause.message?.uppercase() ?: ""
            if ("UNIQUE" in msg || "DUPLICATE" in msg || "23505" in msg) return true
            cause = cause.cause
        }
        return false
    }
}
