package com.example.commerce.order.domain.exception

class OrderNotFoundException(orderId: Long) :
    RuntimeException("Order not found: orderId=$orderId")
