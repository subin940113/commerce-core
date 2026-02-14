package com.example.commerce.order.domain.exception

import com.example.commerce.common.error.DomainException
import com.example.commerce.common.error.ErrorCode

class OrderNotFoundException(orderId: Long) :
    DomainException(ErrorCode.ORDER_NOT_FOUND, "Order not found: orderId=$orderId")
