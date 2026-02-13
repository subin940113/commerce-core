package com.example.commerce.payment.api

import com.example.commerce.common.api.ApiResponse
import com.example.commerce.common.api.ApiResponseSupport
import com.example.commerce.payment.application.CreatePaymentCommand
import com.example.commerce.payment.application.MockWebhookCommand
import com.example.commerce.payment.application.usecase.CreatePaymentUseCase
import com.example.commerce.payment.application.usecase.ProcessPaymentWebhookUseCase
import com.example.commerce.payment.domain.WebhookResultType
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(
    private val createPaymentUseCase: CreatePaymentUseCase,
    private val processPaymentWebhookUseCase: ProcessPaymentWebhookUseCase,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createPayment(@Valid @RequestBody request: CreatePaymentRequest): ApiResponse<CreatePaymentResponse> {
        val result = createPaymentUseCase.execute(CreatePaymentCommand(orderId = request.orderId))
        return ApiResponseSupport.success(
            CreatePaymentResponse(
                paymentId = result.paymentId,
                orderId = result.orderId,
                status = result.status.name,
                amount = result.amount,
            ),
        )
    }

    @PostMapping("/webhooks/mock")
    fun processMockWebhook(@Valid @RequestBody request: MockWebhookRequest): ApiResponse<WebhookResponse> {
        val resultType = try {
            WebhookResultType.valueOf(request.result)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid webhook result: ${request.result}. Allowed: AUTHORIZED, FAILED")
        }
        val result = processPaymentWebhookUseCase.execute(
            MockWebhookCommand(
                provider = request.provider,
                providerEventId = request.providerEventId,
                providerPaymentId = request.providerPaymentId,
                paymentId = request.paymentId,
                result = resultType,
            ),
        )
        return ApiResponseSupport.success(
            WebhookResponse(
                paymentId = result.paymentId,
                status = result.status,
                orderStatus = result.orderStatus,
            ),
        )
    }
}
