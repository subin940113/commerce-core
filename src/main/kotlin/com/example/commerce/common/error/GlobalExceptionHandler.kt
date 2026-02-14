package com.example.commerce.common.error

import com.example.commerce.common.api.ApiError
import com.example.commerce.common.api.ApiResponse
import com.example.commerce.common.api.ApiResponseSupport
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 전역 예외 처리. "어떤 예외를 어떻게 응답으로 바꿀지"만 담당하고, 비즈니스 판단은 하지 않음.
 * DomainException은 보유한 errorCode로 status/code/message 결정. 검증 실패·알 수 없는 예외만 별도 처리.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(DomainException::class)
    fun handleDomainException(ex: DomainException): ResponseEntity<ApiResponse<Nothing>> {
        val body = ApiResponseSupport.failure(
            ApiError(ex.errorCode.code, ex.message ?: ex.errorCode.defaultMessage ?: "Error"),
        )
        return ResponseEntity.status(ex.errorCode.httpStatus).body(body)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidation(ex: MethodArgumentNotValidException): ApiResponse<Nothing> {
        val firstError = ex.bindingResult.fieldErrors.firstOrNull()
        val message = firstError?.let { "${it.field}: ${it.defaultMessage}" } ?: ErrorCode.INVALID_REQUEST.defaultMessage!!
        return ApiResponseSupport.failure(ApiError(ErrorCode.INVALID_REQUEST.code, message))
    }

    @ExceptionHandler(Throwable::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleUnknown(ex: Throwable): ApiResponse<Nothing> {
        log.error("Unhandled exception", ex)
        return ApiResponseSupport.failure(
            ApiError(ErrorCode.INTERNAL_ERROR.code, ErrorCode.INTERNAL_ERROR.defaultMessage!!),
        )
    }
}
