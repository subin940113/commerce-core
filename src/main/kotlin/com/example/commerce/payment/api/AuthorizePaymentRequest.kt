package com.example.commerce.payment.api

import jakarta.validation.constraints.NotBlank

data class AuthorizePaymentRequest(
    @field:NotBlank(message = "result is required")
    val result: String,

    val providerPaymentId: String? = null,
)
