package com.example.commerce.payment.application

import com.example.commerce.payment.domain.WebhookResultType

data class MockWebhookCommand(
    val provider: String,
    val providerEventId: String,
    val providerPaymentId: String?,
    val paymentId: Long,
    val result: WebhookResultType,
)

data class WebhookResult(
    val paymentId: Long,
    val status: String,
    val orderStatus: String,
)
