package com.example.commerce.payment.application.usecase

import com.example.commerce.common.error.DomainException
import com.example.commerce.common.error.ErrorCode
import com.example.commerce.order.domain.OrderStatus
import com.example.commerce.payment.application.AuthorizePaymentCommand
import com.example.commerce.payment.application.AuthorizePaymentResult
import com.example.commerce.payment.application.PaymentOutcomeApplier
import com.example.commerce.payment.domain.exception.PaymentNotFoundException
import com.example.commerce.payment.persistence.PaymentIdempotencyRecord
import com.example.commerce.payment.persistence.PaymentIdempotencyRepository
import com.example.commerce.payment.persistence.PaymentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest

@Service
class AuthorizePaymentUseCase(
    private val paymentRepository: PaymentRepository,
    private val paymentIdempotencyRepository: PaymentIdempotencyRepository,
    private val paymentOutcomeApplier: PaymentOutcomeApplier,
) {

    @Transactional
    fun execute(command: AuthorizePaymentCommand): AuthorizePaymentResult {
        val requestHash = computeRequestHash(command)
        val existing = paymentIdempotencyRepository.findByPaymentIdAndIdempotencyKey(command.paymentId, command.idempotencyKey)
        if (existing != null) {
            if (existing.requestHash == requestHash) {
                return fromPayload(existing.responsePayload)
            }
            throw DomainException(
                ErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                "Idempotency key already used with different request body",
            )
        }

        val payment = paymentRepository.findById(command.paymentId)
            .orElseThrow { PaymentNotFoundException(command.paymentId) }
        val order = payment.order ?: throw PaymentNotFoundException(command.paymentId)
        if (order.status != OrderStatus.PAYMENT_PENDING) {
            throw DomainException(
                ErrorCode.ORDER_STATE_INVALID,
                "Order must be PAYMENT_PENDING for authorize, current=${order.status}",
            )
        }

        when (command.result) {
            com.example.commerce.payment.domain.WebhookResultType.AUTHORIZED ->
                paymentOutcomeApplier.applyAuthorized(payment, order, command.providerPaymentId)
            com.example.commerce.payment.domain.WebhookResultType.FAILED ->
                paymentOutcomeApplier.applyFailed(payment, order, command.providerPaymentId)
        }

        val result = AuthorizePaymentResult(
            paymentId = payment.id,
            status = payment.status.name,
            orderStatus = order.status.name,
        )
        val responsePayload = mapOf(
            "paymentId" to result.paymentId,
            "status" to result.status,
            "orderStatus" to result.orderStatus,
        )
        paymentIdempotencyRepository.save(
            PaymentIdempotencyRecord(
                paymentId = command.paymentId,
                idempotencyKey = command.idempotencyKey,
                requestHash = requestHash,
                responsePayload = responsePayload,
            ),
        )
        return result
    }

    private fun computeRequestHash(command: AuthorizePaymentCommand): String {
        val canonical = "${command.result}|${command.providerPaymentId ?: ""}"
        val bytes = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun fromPayload(payload: Map<String, Any>): AuthorizePaymentResult =
        AuthorizePaymentResult(
            paymentId = (payload["paymentId"] as? Number)?.toLong() ?: throw IllegalStateException("Missing paymentId"),
            status = payload["status"] as? String ?: throw IllegalStateException("Missing status"),
            orderStatus = payload["orderStatus"] as? String ?: throw IllegalStateException("Missing orderStatus"),
        )
}
