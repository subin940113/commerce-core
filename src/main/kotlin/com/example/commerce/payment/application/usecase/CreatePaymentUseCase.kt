package com.example.commerce.payment.application.usecase

import com.example.commerce.order.domain.OrderStatus
import com.example.commerce.order.domain.exception.InvalidOrderStateTransitionException
import com.example.commerce.order.domain.exception.OrderNotFoundException
import com.example.commerce.order.persistence.OrderRepository
import com.example.commerce.payment.application.CreatePaymentCommand
import com.example.commerce.payment.application.CreatePaymentResult
import com.example.commerce.payment.domain.Payment
import com.example.commerce.payment.domain.PaymentStatus
import com.example.commerce.payment.persistence.PaymentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreatePaymentUseCase(
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
) {

    @Transactional
    fun execute(command: CreatePaymentCommand): CreatePaymentResult {
        val order = orderRepository.findById(command.orderId)
            .orElseThrow { OrderNotFoundException(command.orderId) }

        if (order.status != OrderStatus.CREATED) {
            throw InvalidOrderStateTransitionException.forPaymentCreation(order.status)
        }

        val payment = Payment(
            orderId = order.id,
            order = order,
            amount = order.payableAmount,
            status = PaymentStatus.CREATED,
            provider = "MOCK",
        )
        val saved = paymentRepository.save(payment)
        order.status = OrderStatus.PAYMENT_PENDING

        return CreatePaymentResult(
            paymentId = saved.id,
            orderId = saved.orderId,
            status = saved.status,
            amount = saved.amount,
        )
    }
}
