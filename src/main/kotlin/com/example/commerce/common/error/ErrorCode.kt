package com.example.commerce.common.error

import org.springframework.http.HttpStatus

/**
 * API 에러 응답의 코드·HTTP 상태·기본 메시지. 도메인 예외가 이 코드를 들고 있어
 * 전역 핸들러는 "예외 타입별 분기" 없이 errorCode만으로 응답을 만든다.
 */
enum class ErrorCode(
    val code: String,
    val httpStatus: HttpStatus,
    val defaultMessage: String? = null,
) {
    INVALID_REQUEST("INVALID_REQUEST", HttpStatus.BAD_REQUEST, "Invalid request"),
    PRODUCT_NOT_FOUND("PRODUCT_NOT_FOUND", HttpStatus.NOT_FOUND, "Product not found"),
    INVENTORY_NOT_FOUND("INVENTORY_NOT_FOUND", HttpStatus.NOT_FOUND, "Inventory not found"),
    OUT_OF_STOCK("OUT_OF_STOCK", HttpStatus.CONFLICT, "Insufficient stock"),
    ORDER_NOT_FOUND("ORDER_NOT_FOUND", HttpStatus.NOT_FOUND, "Order not found"),
    ORDER_STATE_INVALID("ORDER_STATE_INVALID", HttpStatus.CONFLICT, "Invalid order state transition"),
    PAYMENT_NOT_FOUND("PAYMENT_NOT_FOUND", HttpStatus.NOT_FOUND, "Payment not found"),
    PAYMENT_STATE_INVALID("PAYMENT_STATE_INVALID", HttpStatus.CONFLICT, "Invalid payment state"),
    IDEMPOTENCY_KEY_CONFLICT("IDEMPOTENCY_KEY_CONFLICT", HttpStatus.CONFLICT, "Idempotency key reused with different request"),
    DATA_INCONSISTENCY("DATA_INCONSISTENCY", HttpStatus.CONFLICT, "Data inconsistency"),
    INTERNAL_ERROR("INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"),
}
