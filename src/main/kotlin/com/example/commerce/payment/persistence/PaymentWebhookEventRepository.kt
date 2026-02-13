package com.example.commerce.payment.persistence

import com.example.commerce.payment.domain.PaymentWebhookEvent
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentWebhookEventRepository : JpaRepository<PaymentWebhookEvent, Long> {

    fun findByProviderAndProviderEventId(provider: String, providerEventId: String): PaymentWebhookEvent?
}
