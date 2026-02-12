package com.example.commerce.order.domain

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@Entity
@Table(name = "orders")
@EntityListeners(AuditingEntityListener::class)
class Order(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val status: OrderStatus,

    @Column(name = "total_amount", nullable = false)
    val totalAmount: Long,

    @Column(name = "shipping_fee", nullable = false)
    val shippingFee: Long = 0L,

    @Column(name = "discount_amount", nullable = false)
    val discountAmount: Long = 0L,

    @Column(name = "payable_amount", nullable = false)
    val payableAmount: Long,

    @Column(nullable = false, length = 10)
    val currency: String = "KRW",

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    private val _items: MutableList<OrderItem> = mutableListOf(),
) {
    val items: List<OrderItem> get() = _items.toList()

    fun addItem(item: OrderItem) {
        _items.add(item)
        item.order = this
    }
}
