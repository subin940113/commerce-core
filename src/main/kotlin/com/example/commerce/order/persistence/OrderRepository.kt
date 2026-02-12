package com.example.commerce.order.persistence

import com.example.commerce.order.domain.Order
import org.springframework.data.jpa.repository.JpaRepository

interface OrderRepository : JpaRepository<Order, Long>
