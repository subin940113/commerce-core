package com.example.commerce.order.api

import com.example.commerce.order.application.CreateOrderCommand
import com.example.commerce.order.application.CreateOrderResult
import com.example.commerce.order.application.OrderApplicationService
import com.example.commerce.order.application.OrderItemCommand
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * API 계층: Request → Command 변환, Application 호출, Result → Response 변환만 수행.
 * 비즈니스 로직·트랜잭션 없음.
 */
@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val orderApplicationService: OrderApplicationService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createOrder(@Valid @RequestBody request: CreateOrderRequest): CreateOrderResponse {
        val command = CreateOrderCommand(
            userId = request.userId,
            items = request.items.map { OrderItemCommand(it.productId, it.qty) },
        )
        val result = orderApplicationService.createOrder(command)
        return toResponse(result)
    }

    private fun toResponse(result: CreateOrderResult): CreateOrderResponse =
        CreateOrderResponse(
            orderId = result.orderId,
            status = result.status.name,
            payableAmount = result.payableAmount,
        )
}
