package com.example.commerce.payment.application

import com.example.commerce.payment.domain.PaymentWebhookEvent
import com.example.commerce.payment.persistence.PaymentWebhookEventRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 웹훅 이벤트 insert 시도. 유니크 위반 시 예외 발생으로 멱등 판단.
 * REQUIRES_NEW로 호출해 실패 시 외부 트랜잭션은 유지.
 */
@Component
class WebhookEventInsert(
    private val paymentWebhookEventRepository: PaymentWebhookEventRepository,
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun insertIfAbsent(event: PaymentWebhookEvent) {
        paymentWebhookEventRepository.saveAndFlush(event)
    }
}
