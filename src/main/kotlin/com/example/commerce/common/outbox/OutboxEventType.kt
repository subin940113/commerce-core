package com.example.commerce.common.outbox

object OutboxEventType {
    const val PAYMENT_AUTHORIZED = "PAYMENT_AUTHORIZED"
    const val PAYMENT_FAILED = "PAYMENT_FAILED"
}

object OutboxAggregateType {
    const val PAYMENT = "PAYMENT"
    const val ORDER = "ORDER"
}
