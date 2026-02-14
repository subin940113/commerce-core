package com.example.commerce.payment.domain.exception

import com.example.commerce.common.error.DomainException
import com.example.commerce.common.error.ErrorCode

class PaymentNotFoundException(paymentId: Long) :
    DomainException(ErrorCode.PAYMENT_NOT_FOUND, "Payment not found: paymentId=$paymentId")
