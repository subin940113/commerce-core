package com.example.commerce.common.error

/**
 * 표준 에러 응답 형식: { "code": "ERROR_CODE", "message": "설명" }
 */
data class ErrorResponse(
    val code: String,
    val message: String,
)
