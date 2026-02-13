package com.example.commerce.payment.application

import com.example.commerce.payment.domain.PaymentStatus

data class CreatePaymentResult(
    val paymentId: Long,
    val orderId: Long,
    val status: PaymentStatus,
    val amount: Long,
)
