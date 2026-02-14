package com.example.commerce.payment.application

data class AuthorizePaymentResult(
    val paymentId: Long,
    val status: String,
    val orderStatus: String,
)
