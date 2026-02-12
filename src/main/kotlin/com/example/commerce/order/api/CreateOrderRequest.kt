package com.example.commerce.order.api

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

data class CreateOrderRequest(
    @field:NotNull(message = "userId is required")
    @field:Positive(message = "userId must be positive")
    val userId: Long,

    @field:NotEmpty(message = "items must not be empty")
    @field:Valid
    val items: List<OrderItemRequestDto>,
)

data class OrderItemRequestDto(
    @field:NotNull(message = "productId is required")
    @field:Positive(message = "productId must be positive")
    val productId: Long,

    @field:NotNull(message = "qty is required")
    @field:Positive(message = "qty must be positive")
    val qty: Int,
)
