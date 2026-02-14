package com.example.commerce.payment.application.usecase

import com.example.commerce.order.domain.OrderStatus
import com.example.commerce.order.domain.exception.InvalidOrderStateTransitionException
import com.example.commerce.payment.application.MockWebhookCommand
import com.example.commerce.payment.application.PaymentOutcomeApplier
import com.example.commerce.payment.application.WebhookEventInsert
import com.example.commerce.payment.application.WebhookResult
import com.example.commerce.payment.domain.PaymentWebhookEvent
import com.example.commerce.payment.domain.WebhookResultType
import com.example.commerce.payment.domain.exception.PaymentNotFoundException
import com.example.commerce.payment.persistence.PaymentRepository
import com.example.commerce.payment.persistence.PaymentWebhookEventRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 결제 웹훅을 처리한다.
 * PG(결제사) 서버가 우리 서버로 보내는 콜백을 가정한다.
 * 웹훅은 같은 이벤트가 여러 번 올 수 있으므로,
 * (provider, providerEventId) 유니크 제약으로 한 번만 처리되도록 한다.
 *
 * - AUTHORIZED: 주문을 PAID로 바꾸고, 예약된 재고를 실제 차감한다.
 * - FAILED: 예약된 재고만 해제한다.
 *
 * 주문 상태 변경과 Outbox 이벤트 저장은 같은 트랜잭션에서 처리한다.
 */
@Service
class ProcessPaymentWebhookUseCase(
    private val paymentRepository: PaymentRepository,
    private val paymentWebhookEventRepository: PaymentWebhookEventRepository,
    private val webhookEventInsert: WebhookEventInsert,
    private val paymentOutcomeApplier: PaymentOutcomeApplier,
) {

    @Transactional
    fun execute(command: MockWebhookCommand): WebhookResult {
        // 웹훅을 먼저 저장 시도해, 이 이벤트를 우리가 처리할 차례인지 확인한다.
        val event = PaymentWebhookEvent(
            provider = command.provider,
            providerEventId = command.providerEventId,
            providerPaymentId = command.providerPaymentId,
            paymentId = command.paymentId,
            payload = null,
        )

        // 이미 처리된 웹훅이면(유니크 위반), 다시 처리하지 않고 현재 상태만 반환한다.
        try {
            webhookEventInsert.insertIfAbsent(event)
        } catch (e: Exception) {
            if (!isDuplicateKeyOrUniqueViolation(e)) throw e
            val existing = paymentWebhookEventRepository.findByProviderAndProviderEventId(command.provider, command.providerEventId)
                ?: throw e
            // 중복 호출이면, 처음 처리된 결과 기준으로 응답한다.
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

        // 결제 대기 상태에서만 웹훅을 반영한다.
        if (order.status != OrderStatus.PAYMENT_PENDING) {
            throw InvalidOrderStateTransitionException("Order must be PAYMENT_PENDING for webhook, current=${order.status}")
        }

        when (command.result) {
            WebhookResultType.AUTHORIZED ->
                paymentOutcomeApplier.applyAuthorized(payment, order, command.providerPaymentId)
            WebhookResultType.FAILED ->
                paymentOutcomeApplier.applyFailed(payment, order, command.providerPaymentId)
        }

        return WebhookResult(
            paymentId = payment.id,
            status = payment.status.name,
            orderStatus = order.status.name,
        )
    }

    // DB 종류나 예외 타입이 달라도, 유니크 위반인지 확인하기 위한 함수다.
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
