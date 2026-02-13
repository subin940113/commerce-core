package com.example.commerce.payment.api

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

data class MockWebhookRequest(
    @field:NotBlank
    val provider: String,

    @field:NotBlank
    val providerEventId: String,

    val providerPaymentId: String? = null,

    @field:NotNull
    @field:Positive
    val paymentId: Long,

    @field:NotBlank
    val result: String,
)
