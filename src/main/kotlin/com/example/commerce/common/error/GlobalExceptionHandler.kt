package com.example.commerce.common.error

import com.example.commerce.order.domain.exception.InsufficientStockException
import com.example.commerce.order.domain.exception.InventoryNotFoundException
import com.example.commerce.order.domain.exception.ProductNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ProductNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleProductNotFound(ex: ProductNotFoundException): ErrorResponse =
        ErrorResponse(code = ErrorCode.PRODUCT_NOT_FOUND.code, message = ex.message ?: "Product not found")

    @ExceptionHandler(InventoryNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleInventoryNotFound(ex: InventoryNotFoundException): ErrorResponse =
        ErrorResponse(code = ErrorCode.INVENTORY_NOT_FOUND.code, message = ex.message ?: "Inventory not found")

    @ExceptionHandler(InsufficientStockException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleInsufficientStock(ex: InsufficientStockException): ErrorResponse =
        ErrorResponse(
            code = ErrorCode.OUT_OF_STOCK.code,
            message = ex.message ?: "Insufficient stock for productId=${ex.productId}",
        )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidation(ex: MethodArgumentNotValidException): ErrorResponse {
        val firstError = ex.bindingResult.fieldErrors.firstOrNull()
        val message = firstError?.let { "${it.field}: ${it.defaultMessage}" } ?: "Validation failed"
        return ErrorResponse(code = ErrorCode.INVALID_REQUEST.code, message = message)
    }
}
