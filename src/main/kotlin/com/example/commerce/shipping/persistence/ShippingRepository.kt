package com.example.commerce.shipping.persistence

import com.example.commerce.shipping.domain.Shipping
import org.springframework.data.jpa.repository.JpaRepository

interface ShippingRepository : JpaRepository<Shipping, Long> {

    fun findByOrderId(orderId: Long): Shipping?

    fun countByOrderId(orderId: Long): Long
}
