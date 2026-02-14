package com.example.commerce.order.domain.exception

import com.example.commerce.common.error.DomainException
import com.example.commerce.common.error.ErrorCode
import com.example.commerce.order.domain.OrderStatus

class InvalidOrderStateTransitionException(
    message: String,
) : DomainException(ErrorCode.ORDER_STATE_INVALID, message) {

    companion object {
        fun forPaymentCreation(current: OrderStatus): InvalidOrderStateTransitionException =
            InvalidOrderStateTransitionException(
                "Order status must be CREATED to create payment, current=$current",
            )
    }
}
