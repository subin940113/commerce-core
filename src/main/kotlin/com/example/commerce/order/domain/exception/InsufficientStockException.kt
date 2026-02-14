package com.example.commerce.order.domain.exception

import com.example.commerce.common.error.DomainException
import com.example.commerce.common.error.ErrorCode

class InsufficientStockException(
    val productId: Long,
    val availableToReserve: Int,
    val requested: Int,
) : DomainException(
    ErrorCode.OUT_OF_STOCK,
    "Insufficient stock for productId=$productId (availableToReserve=$availableToReserve, requested=$requested)",
)
