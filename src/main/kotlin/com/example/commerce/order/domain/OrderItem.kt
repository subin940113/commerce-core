package com.example.commerce.order.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "order_items")
class OrderItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    var order: Order? = null,

    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "product_name_snapshot", nullable = false, length = 200)
    val productNameSnapshot: String,

    @Column(name = "unit_price_snapshot", nullable = false)
    val unitPriceSnapshot: Long,

    @Column(nullable = false)
    val qty: Int,

    @Column(name = "line_amount", nullable = false)
    val lineAmount: Long,
)
