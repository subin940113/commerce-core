package com.example.commerce.order.application

import com.example.commerce.order.domain.OrderStatus

data class CreateOrderResult(
    val orderId: Long,
    val status: OrderStatus,
    val payableAmount: Long,
)
