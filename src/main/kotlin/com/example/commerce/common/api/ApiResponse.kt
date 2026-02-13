package com.example.commerce.common.api

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiError? = null,
    val traceId: String? = null,
    val timestamp: Instant = Instant.now(),
) {
    companion object {
        fun <T> success(data: T, traceId: String? = null): ApiResponse<T> =
            ApiResponse(success = true, data = data, error = null, traceId = traceId)

        fun failure(error: ApiError, traceId: String? = null): ApiResponse<Nothing> =
            ApiResponse(success = false, data = null, error = error, traceId = traceId)
    }
}

data class ApiError(
    val code: String,
    val message: String,
)
