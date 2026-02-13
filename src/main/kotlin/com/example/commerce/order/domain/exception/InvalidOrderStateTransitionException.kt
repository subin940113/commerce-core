package com.example.commerce.order.domain.exception

import com.example.commerce.order.domain.OrderStatus

class InvalidOrderStateTransitionException(
    message: String,
) : RuntimeException(message) {

    companion object {
        fun forPaymentCreation(current: OrderStatus): InvalidOrderStateTransitionException =
            InvalidOrderStateTransitionException(
                "Order status must be CREATED to create payment, current=$current",
            )
    }
}
