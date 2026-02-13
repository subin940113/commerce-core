package com.example.commerce.payment.api

data class WebhookResponse(
    val paymentId: Long,
    val status: String,
    val orderStatus: String,
)
