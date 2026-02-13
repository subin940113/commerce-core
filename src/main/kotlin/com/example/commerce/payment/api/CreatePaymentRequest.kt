package com.example.commerce.payment.api

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

data class CreatePaymentRequest(
    @field:NotNull(message = "orderId is required")
    @field:Positive(message = "orderId must be positive")
    val orderId: Long,
)
