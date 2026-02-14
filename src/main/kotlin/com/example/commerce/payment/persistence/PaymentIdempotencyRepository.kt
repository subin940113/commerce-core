package com.example.commerce.payment.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface PaymentIdempotencyRepository : JpaRepository<PaymentIdempotencyRecord, Long> {

    fun findByPaymentIdAndIdempotencyKey(paymentId: Long, idempotencyKey: String): PaymentIdempotencyRecord?
}
