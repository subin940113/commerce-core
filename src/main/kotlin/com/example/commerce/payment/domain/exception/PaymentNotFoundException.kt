package com.example.commerce.payment.domain.exception

class PaymentNotFoundException(paymentId: Long) :
    RuntimeException("Payment not found: paymentId=$paymentId")
