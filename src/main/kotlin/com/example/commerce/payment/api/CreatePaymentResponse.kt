package com.example.commerce.payment.api

data class CreatePaymentResponse(
    val paymentId: Long,
    val orderId: Long,
    val status: String,
    val amount: Long,
)
