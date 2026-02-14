package com.example.commerce.payment.application

import com.example.commerce.payment.domain.WebhookResultType

data class AuthorizePaymentCommand(
    val paymentId: Long,
    val idempotencyKey: String,
    val result: WebhookResultType,
    val providerPaymentId: String? = null,
)
