package com.example.commerce.common.error

import com.example.commerce.common.api.ApiError
import com.example.commerce.common.api.ApiResponse
import com.example.commerce.common.api.ApiResponseSupport
import com.example.commerce.order.domain.exception.InsufficientStockException
import com.example.commerce.order.domain.exception.InventoryNotFoundException
import com.example.commerce.order.domain.exception.DataInconsistencyException
import com.example.commerce.order.domain.exception.InvalidOrderStateTransitionException
import com.example.commerce.order.domain.exception.OrderNotFoundException
import com.example.commerce.order.domain.exception.ProductNotFoundException
import com.example.commerce.payment.domain.exception.PaymentNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ProductNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleProductNotFound(ex: ProductNotFoundException): ApiResponse<Nothing> =
        ApiResponseSupport.failure(ApiError(ErrorCode.PRODUCT_NOT_FOUND.code, ex.message ?: "Product not found"))

    @ExceptionHandler(InventoryNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleInventoryNotFound(ex: InventoryNotFoundException): ApiResponse<Nothing> =
        ApiResponseSupport.failure(ApiError(ErrorCode.INVENTORY_NOT_FOUND.code, ex.message ?: "Inventory not found"))

    @ExceptionHandler(InsufficientStockException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleInsufficientStock(ex: InsufficientStockException): ApiResponse<Nothing> =
        ApiResponseSupport.failure(
            ApiError(ErrorCode.OUT_OF_STOCK.code, ex.message ?: "Insufficient stock for productId=${ex.productId}"),
        )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidation(ex: MethodArgumentNotValidException): ApiResponse<Nothing> {
        val firstError = ex.bindingResult.fieldErrors.firstOrNull()
        val message = firstError?.let { "${it.field}: ${it.defaultMessage}" } ?: "Validation failed"
        return ApiResponseSupport.failure(ApiError(ErrorCode.INVALID_REQUEST.code, message))
    }

    @ExceptionHandler(PaymentNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handlePaymentNotFound(ex: PaymentNotFoundException): ApiResponse<Nothing> =
        ApiResponseSupport.failure(ApiError(ErrorCode.PAYMENT_NOT_FOUND.code, ex.message ?: "Payment not found"))

    @ExceptionHandler(OrderNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleOrderNotFound(ex: OrderNotFoundException): ApiResponse<Nothing> =
        ApiResponseSupport.failure(ApiError(ErrorCode.ORDER_NOT_FOUND.code, ex.message ?: "Order not found"))

    @ExceptionHandler(InvalidOrderStateTransitionException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleInvalidOrderStateTransition(ex: InvalidOrderStateTransitionException): ApiResponse<Nothing> =
        ApiResponseSupport.failure(
            ApiError(ErrorCode.INVALID_ORDER_STATE_TRANSITION.code, ex.message ?: "Invalid order state transition"),
        )

    @ExceptionHandler(DataInconsistencyException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleDataInconsistency(ex: DataInconsistencyException): ApiResponse<Nothing> =
        ApiResponseSupport.failure(ApiError(ErrorCode.DATA_INCONSISTENCY.code, ex.message ?: "Data inconsistency"))
}
