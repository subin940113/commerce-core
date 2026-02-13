package com.example.commerce.payment.persistence

import com.example.commerce.payment.domain.Payment
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentRepository : JpaRepository<Payment, Long> {

    fun findByOrderId(orderId: Long): List<Payment>
}
