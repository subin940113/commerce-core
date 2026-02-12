package com.example.commerce.order.application

data class CreateOrderCommand(
    val userId: Long,
    val items: List<OrderItemCommand>,
)

data class OrderItemCommand(
    val productId: Long,
    val qty: Int,
)
