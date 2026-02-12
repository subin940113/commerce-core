package com.example.commerce.order.api

data class CreateOrderResponse(
    val orderId: Long,
    val status: String,
    val payableAmount: Long,
)
