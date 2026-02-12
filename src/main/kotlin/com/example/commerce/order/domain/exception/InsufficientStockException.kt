package com.example.commerce.order.domain.exception

/**
 * 재고 부족 (가용 예약 수량 = available_qty - reserved_qty 기준).
 */
class InsufficientStockException(
    val productId: Long,
    val availableToReserve: Int,
    val requested: Int,
) : RuntimeException(
    "Insufficient stock for productId=$productId (availableToReserve=$availableToReserve, requested=$requested)",
)
