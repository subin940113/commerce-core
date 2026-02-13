package com.example.commerce.common.api

import org.slf4j.MDC
import java.time.Instant

object ApiResponseSupport {

    fun currentTraceId(): String? = MDC.get("traceId")

    fun <T> success(data: T): ApiResponse<T> =
        ApiResponse.success(data, traceId = currentTraceId())

    fun failure(error: ApiError): ApiResponse<Nothing> =
        ApiResponse.failure(error, traceId = currentTraceId())
}
