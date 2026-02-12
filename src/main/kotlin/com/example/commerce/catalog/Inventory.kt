package com.example.commerce.catalog

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "inventory")
class Inventory(
    @Id
    @Column(name = "product_id")
    val productId: Long,

    @Column(name = "available_qty", nullable = false)
    var availableQty: Int,

    @Column(name = "reserved_qty", nullable = false)
    var reservedQty: Int,

    @Column(nullable = false)
    var version: Long = 0L,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
